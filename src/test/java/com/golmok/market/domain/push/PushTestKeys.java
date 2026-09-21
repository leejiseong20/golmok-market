package com.golmok.market.domain.push;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;

/**
 * 푸시 테스트에 쓰는 키. RFC 8291 부록 A 의 키를 그대로 쓴다(서로 짝이 맞는 것이 이미 검증된 키다).
 *   - 서버 키 쌍 → VAPID 키로 쓴다
 *   - 브라우저 키 쌍 + auth → 구독 정보로 쓰고, 받은 본문을 브라우저처럼 풀어 본다
 */
final class PushTestKeys {

    static final String VAPID_PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    static final String VAPID_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    static final String BROWSER_PUBLIC = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    static final String BROWSER_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    static final String BROWSER_AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

    private PushTestKeys() {
    }

    /** 브라우저가 하는 일: 머리에서 salt·서버 공개키를 읽어 같은 키를 유도하고 푼다. */
    static byte[] decryptAsBrowser(byte[] body) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        buffer.getInt();
        byte[] serverPublic = new byte[buffer.get()];
        buffer.get(serverPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        byte[] browserPublic = Base64.getUrlDecoder().decode(BROWSER_PUBLIC);
        byte[] auth = Base64.getUrlDecoder().decode(BROWSER_AUTH);
        byte[] ecdh = P256.agree(P256.decodePrivate(Base64.getUrlDecoder().decode(BROWSER_PRIVATE)), P256.decodePublic(serverPublic));
        WebPushEncryptor.Keys keys = WebPushEncryptor.deriveKeys(ecdh, auth, browserPublic, serverPublic, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keys.cek(), "AES"), new GCMParameterSpec(128, keys.nonce()));
        byte[] record = cipher.doFinal(ciphertext);
        return Arrays.copyOf(record, record.length - 1);
    }
}
