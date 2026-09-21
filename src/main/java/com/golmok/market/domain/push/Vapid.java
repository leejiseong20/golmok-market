package com.golmok.market.domain.push;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * VAPID(RFC 8292) — "이 푸시를 보낸 서버가 누구인지" 푸시 서비스에 증명하는 서명.
 *
 * 브라우저는 구독할 때 우리 공개키를 받아 두고, 푸시 서비스는 요청의 서명이 그 공개키로 검증될 때만 받아 준다.
 * 개인키를 가진 우리 서버만 이 구독자에게 푸시를 보낼 수 있다.
 *
 * 서명은 ES256 JWT 다: 머리 {"typ":"JWT","alg":"ES256"}, 내용 {aud: 푸시 서비스 출처, exp: 만료, sub: 연락처}.
 * JWT 의 ES256 서명은 DER 이 아니라 R||S 64바이트여야 한다. JDK 의 "SHA256withECDSAinP1363Format" 이
 * 바로 그 형식을 준다(SHA256withECDSA 는 DER 이라 그대로 쓰면 푸시 서비스가 거부한다).
 */
public final class Vapid {

    /** RFC 8292 는 만료를 24시간 이내로 요구한다. 시계가 조금 어긋나도 넉넉하게 12시간으로 둔다. */
    static final Duration EXPIRES_IN = Duration.ofHours(12);
    private static final String HEADER = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));

    private final PrivateKey privateKey;
    private final byte[] publicKey;
    private final String subject;

    private Vapid(PrivateKey privateKey, byte[] publicKey, String subject) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.subject = subject;
    }

    /**
     * 설정값(base64url)으로 만든다. 공개키는 65바이트 비압축, 개인키는 32바이트 스칼라다.
     * 두 키가 짝이 맞는지 여기서 서명·검증을 한 번 해 본다. 짝이 틀리면 기동할 때 바로 알 수 있고,
     * 모르고 넘어가면 모든 푸시가 푸시 서비스에서 조용히 거부된다.
     */
    public static Vapid of(String publicKeyBase64, String privateKeyBase64, String subject) {
        byte[] publicBytes = Base64.getUrlDecoder().decode(publicKeyBase64.trim());
        PublicKey publicKey = P256.decodePublic(publicBytes);
        PrivateKey privateKey = P256.decodePrivate(Base64.getUrlDecoder().decode(privateKeyBase64.trim()));
        Vapid vapid = new Vapid(privateKey, publicBytes, subject);
        byte[] probe = "vapid-key-check".getBytes(StandardCharsets.US_ASCII);
        if (!verify(publicKey, probe, vapid.sign(probe))) {
            throw new IllegalStateException("VAPID 공개키와 개인키가 짝이 맞지 않습니다");
        }
        return vapid;
    }

    /** 브라우저가 구독할 때 쓰는 공개키(base64url). 프론트에 내려 준다. */
    public String publicKey() {
        return b64(publicKey);
    }

    /**
     * 푸시 요청의 Authorization 머리값: {@code vapid t=<JWT>, k=<공개키>}.
     * aud 는 구독 주소의 출처(scheme://host[:port])다. 경로까지 넣으면 푸시 서비스가 거부한다.
     */
    public String authorization(String endpoint, Instant now) {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        String claims = "{\"aud\":\"" + json(audience) + "\",\"exp\":" + now.plus(EXPIRES_IN).getEpochSecond()
                + ",\"sub\":\"" + json(subject) + "\"}";
        String unsigned = HEADER + "." + b64(claims.getBytes(StandardCharsets.UTF_8));
        String token = unsigned + "." + b64(sign(unsigned.getBytes(StandardCharsets.US_ASCII)));
        return "vapid t=" + token + ", k=" + publicKey();
    }

    private byte[] sign(byte[] data) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("VAPID 서명에 실패했습니다", e);
        }
    }

    static boolean verify(PublicKey key, byte[] data, byte[] rawSignature) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initVerify(key);
            signature.update(data);
            return signature.verify(rawSignature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    /** 설정값(주소·연락처)만 들어가지만, 따옴표·역슬래시가 섞여도 JSON 이 깨지지 않게 한다. */
    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
