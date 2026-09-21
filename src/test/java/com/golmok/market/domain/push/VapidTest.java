package com.golmok.market.domain.push;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ECDSA 서명은 매번 난수를 섞어 결과가 달라서 고정 값과 비교할 수 없다.
 * 대신 푸시 서비스가 하는 검사를 그대로 해 본다: 모양(JWT 세 부분, R||S 64바이트), 내용(aud·exp·sub), 공개키로 검증.
 */
class VapidTest {

    private static final Instant NOW = Instant.parse("2026-09-21T09:00:00Z");

    /** 운영과 같은 형식(base64url 비압축 공개키 65바이트 · 스칼라 32바이트)으로 키를 만든다. */
    private static String[] newKeys() {
        KeyPair pair = P256.generateKeyPair();
        byte[] scalar = ((ECPrivateKey) pair.getPrivate()).getS().toByteArray();
        byte[] fixed = new byte[32];
        int start = Math.max(0, scalar.length - 32);
        System.arraycopy(scalar, start, fixed, 32 - (scalar.length - start), scalar.length - start);
        return new String[]{Vapid.b64(P256.encode(pair.getPublic())), Vapid.b64(fixed)};
    }

    private static String decode(String part) {
        return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
    }

    @Test
    void 푸시_서비스가_받는_모양의_서명을_만든다() {
        String[] keys = newKeys();
        Vapid vapid = Vapid.of(keys[0], keys[1], "https://golmok-market-frontend.vercel.app");

        String header = vapid.authorization("https://fcm.googleapis.com/fcm/send/abc123:xyz", NOW);

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + keys[0]);
        String token = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        assertThat(decode(parts[0])).isEqualTo("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
        // aud 는 출처까지만. 경로(/fcm/send/...)가 들어가면 푸시 서비스가 거부한다.
        assertThat(decode(parts[1])).isEqualTo("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":"
                + NOW.plus(Vapid.EXPIRES_IN).getEpochSecond() + ",\"sub\":\"https://golmok-market-frontend.vercel.app\"}");

        byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
        assertThat(signature).as("JWT 의 ES256 서명은 DER 이 아니라 R||S 64바이트").hasSize(64);
        byte[] signed = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        assertThat(Vapid.verify(P256.decodePublic(Base64.getUrlDecoder().decode(keys[0])), signed, signature)).isTrue();
    }

    @Test
    void 만료는_24시간_이내다() {
        assertThat(Vapid.EXPIRES_IN).isLessThanOrEqualTo(java.time.Duration.ofHours(24));
    }

    @Test
    void 포트가_있는_구독_주소는_출처에_포트를_남긴다() {
        String[] keys = newKeys();
        String header = Vapid.of(keys[0], keys[1], "mailto:ops@example.com")
                .authorization("https://push.example.net:8443/push/abc", NOW);
        String claims = decode(header.substring("vapid t=".length()).split("\\.")[1]);
        assertThat(claims).contains("\"aud\":\"https://push.example.net:8443\"");
    }

    /** 짝이 틀린 키로 기동하면 모든 푸시가 조용히 거부된다. 기동할 때 알 수 있어야 한다. */
    @Test
    void 짝이_맞지_않는_키는_거부한다() {
        String[] one = newKeys();
        String[] other = newKeys();
        assertThatThrownBy(() -> Vapid.of(one[0], other[1], "mailto:ops@example.com"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 공개키_형식이_틀리면_거부한다() {
        String[] keys = newKeys();
        String shortKey = Vapid.b64(Arrays.copyOf(Base64.getUrlDecoder().decode(keys[0]), 64));
        assertThatThrownBy(() -> Vapid.of(shortKey, keys[1], "mailto:ops@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 앞자리가_0인_개인키도_32바이트로_다룬다() {
        // BigInteger.toByteArray() 는 앞자리 0 을 버리거나 부호 바이트를 붙인다. 좌표·스칼라는 늘 32바이트로 맞춰야 한다.
        assertThat(new BigInteger(1, new byte[]{0, 0, 1}).toByteArray()).hasSize(1);
        for (int i = 0; i < 20; i++) {
            String[] keys = newKeys();
            assertThat(Base64.getUrlDecoder().decode(keys[1])).hasSize(32);
            assertThat(Vapid.of(keys[0], keys[1], "mailto:ops@example.com").publicKey()).isEqualTo(keys[0]);
        }
    }
}
