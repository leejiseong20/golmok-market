package com.golmok.market.domain.push;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

/**
 * 웹 푸시 본문 암호화 — RFC 8291(Message Encryption for Web Push) + RFC 8188(aes128gcm).
 *
 * 푸시는 Google·Apple 의 푸시 서비스를 거쳐 가므로 내용을 그대로 보내면 그들이 읽을 수 있다.
 * 브라우저가 구독할 때 준 공개키(p256dh)와 인증 비밀(auth)로 암호화해, 받는 브라우저만 풀 수 있게 한다.
 *
 *   1. 보낼 때마다 새 키 쌍(as)과 salt 를 만든다(같은 내용도 매번 다른 암호문이 된다)
 *   2. ECDH(as 개인키, 브라우저 공개키) → 공유 비밀
 *   3. HKDF 로 CEK(16바이트)·NONCE(12바이트)를 만든다. auth 비밀과 두 공개키를 섞는다
 *   4. 본문 + 0x02(마지막 레코드 표시)를 AES-128-GCM 으로 암호화
 *   5. 머리(salt · 레코드 크기 · 키 길이 · as 공개키) + 암호문
 *
 * JDK 표준 API 만 쓴다. RFC 8291 부록 A 의 값과 바이트 단위로 같은지 테스트로 확인한다.
 */
public final class WebPushEncryptor {

    /** 레코드 크기. 본문은 한 레코드에 들어가야 한다(여러 레코드로 나누지 않는다). */
    static final int RECORD_SIZE = 4096;
    private static final int SALT_LENGTH = 16;
    private static final int TAG_LENGTH = 16;
    /** 한 레코드에 넣을 수 있는 본문 최대 크기. 구분 바이트(0x02) 1개와 GCM 태그 16바이트를 뺀다. */
    static final int MAX_PLAINTEXT = RECORD_SIZE - 1 - TAG_LENGTH;

    private static final byte[] KEY_INFO = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushEncryptor() {
    }

    /** 브라우저의 공개키(p256dh 65바이트)와 인증 비밀(auth 16바이트)로 암호화한다. */
    public static byte[] encrypt(byte[] plaintext, byte[] browserPublicKey, byte[] authSecret) {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, browserPublicKey, authSecret, P256.generateKeyPair(), salt);
    }

    /** 키 쌍과 salt 를 밖에서 받는다. RFC 의 고정 값으로 테스트하기 위해서다. */
    static byte[] encrypt(byte[] plaintext, byte[] browserPublicKey, byte[] authSecret, KeyPair serverKeys, byte[] salt) {
        if (plaintext.length > MAX_PLAINTEXT) {
            throw new IllegalArgumentException("푸시 본문이 너무 큽니다(최대 " + MAX_PLAINTEXT + "바이트)");
        }
        ECPublicKey browserKey = P256.decodePublic(browserPublicKey);
        byte[] serverPublic = P256.encode(serverKeys.getPublic());
        byte[] ecdhSecret = P256.agree(serverKeys.getPrivate(), browserKey);
        Keys keys = deriveKeys(ecdhSecret, authSecret, browserPublicKey, serverPublic, salt);

        byte[] record = concat(plaintext, new byte[]{0x02});
        byte[] ciphertext;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys.cek(), "AES"), new GCMParameterSpec(TAG_LENGTH * 8, keys.nonce()));
            ciphertext = cipher.doFinal(record);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("푸시 본문을 암호화하지 못했습니다", e);
        }

        ByteBuffer header = ByteBuffer.allocate(SALT_LENGTH + 4 + 1 + serverPublic.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) serverPublic.length).put(serverPublic);
        return concat(header.array(), ciphertext);
    }

    /** 키 유도의 중간값. 테스트가 RFC 부록 A 의 값과 하나씩 대조할 수 있게 모두 남긴다. */
    record Keys(byte[] prkKey, byte[] ikm, byte[] prk, byte[] cek, byte[] nonce) {
    }

    /**
     * HKDF(RFC 5869). 출력이 32바이트 이하라 Expand 는 HMAC 한 번(T(1))이면 된다.
     *   PRK_key = HMAC(auth_secret, ecdh_secret)                 -- Extract
     *   IKM     = HMAC(PRK_key, "WebPush: info\0" || ua || as || 0x01)
     *   PRK     = HMAC(salt, IKM)                                 -- Extract
     *   CEK     = HMAC(PRK, cek_info || 0x01) 앞 16바이트
     *   NONCE   = HMAC(PRK, nonce_info || 0x01) 앞 12바이트
     */
    static Keys deriveKeys(byte[] ecdhSecret, byte[] authSecret, byte[] browserPublic, byte[] serverPublic, byte[] salt) {
        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] ikm = hmac(prkKey, concat(KEY_INFO, browserPublic, serverPublic, new byte[]{0x01}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat(CEK_INFO, new byte[]{0x01})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat(NONCE_INFO, new byte[]{0x01})), 12);
        return new Keys(prkKey, ikm, prk, cek, nonce);
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC 을 계산하지 못했습니다", e);
        }
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
