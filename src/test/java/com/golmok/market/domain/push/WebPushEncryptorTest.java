package com.golmok.market.domain.push;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RFC 8291 부록 A 의 값과 바이트 단위로 대조한다.
 * 값은 https://www.rfc-editor.org/rfc/rfc8291.txt 원문에서 줄바꿈·공백만 지우고 옮겼다.
 *
 * 암호화는 "비슷하게 맞는" 것이 없다. 한 바이트라도 다르면 브라우저가 풀지 못하고 알림은 조용히 사라진다.
 * 그래서 최종 결과만이 아니라 중간값을 하나씩 확인해, 틀렸을 때 어느 단계인지 바로 알 수 있게 한다.
 */
class WebPushEncryptorTest {

    private static byte[] b64(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    // ---------- RFC 8291 부록 A 입력 ----------
    private static final byte[] PLAINTEXT = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] AS_PUBLIC = b64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
    private static final byte[] AS_PRIVATE = b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
    private static final byte[] UA_PUBLIC = b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
    private static final byte[] UA_PRIVATE = b64("q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94");
    private static final byte[] SALT = b64("DGv6ra1nlYgDCS1FRnbzlw");
    private static final byte[] AUTH_SECRET = b64("BTBZMqHH6r4Tts7J_aSIgg");

    private static KeyPair serverKeys() {
        return new KeyPair(P256.decodePublic(AS_PUBLIC), P256.decodePrivate(AS_PRIVATE));
    }

    @Test
    void 입력의_평문이_RFC_와_같다() {
        assertThat(PLAINTEXT).isEqualTo(b64("V2hlbiBJIGdyb3cgdXAsIEkgd2FudCB0byBiZSBhIHdhdGVybWVsb24"));
    }

    @Test
    void 공개키를_65바이트_비압축_형식으로_주고받는다() {
        assertThat(P256.encode(P256.decodePublic(AS_PUBLIC))).isEqualTo(AS_PUBLIC);
        assertThat(P256.encode(P256.decodePublic(UA_PUBLIC))).isEqualTo(UA_PUBLIC);
    }

    @Test
    void ECDH_공유_비밀이_RFC_와_같다() {
        byte[] secret = P256.agree(P256.decodePrivate(AS_PRIVATE), P256.decodePublic(UA_PUBLIC));
        assertThat(secret).isEqualTo(b64("kyrL1jIIOHEzg3sM2ZWRHDRB62YACZhhSlknJ672kSs"));
    }

    @Test
    void 키_유도의_중간값이_모두_RFC_와_같다() {
        byte[] ecdh = b64("kyrL1jIIOHEzg3sM2ZWRHDRB62YACZhhSlknJ672kSs");
        WebPushEncryptor.Keys keys = WebPushEncryptor.deriveKeys(ecdh, AUTH_SECRET, UA_PUBLIC, AS_PUBLIC, SALT);

        assertThat(keys.prkKey()).isEqualTo(b64("Snr3JMxaHVDXHWJn5wdC52WjpCtd2EIEGBykDcZW32k"));
        assertThat(keys.ikm()).isEqualTo(b64("S4lYMb_L0FxCeq0WhDx813KgSYqU26kOyzWUdsXYyrg"));
        assertThat(keys.prk()).isEqualTo(b64("09_eUZGrsvxChDCGRCdkLiDXrReGOEVeSCdCcPBSJSc"));
        assertThat(keys.cek()).isEqualTo(b64("oIhVW04MRdy2XN9CiKLxTg"));
        assertThat(keys.nonce()).isEqualTo(b64("4h_95klXJ5E_qnoN"));
    }

    /**
     * RFC 5절 예시의 머리에는 "Content-Length: 145" 라고 적혀 있지만 실제 본문은 144바이트다.
     * 머리 86 + 암호문 58(평문 41 + 구분 1 + 태그 16) = 144 이고, 5절에 실린 base64 본문(192자)도 풀면 144바이트다.
     * 처음에 145 를 그대로 믿고 테스트를 썼다가 틀렸다. 숫자가 아니라 본문 바이트를 비교한다.
     */
    @Test
    void 최종_본문이_RFC_5절의_본문과_같다() {
        byte[] body = WebPushEncryptor.encrypt(PLAINTEXT, UA_PUBLIC, AUTH_SECRET, serverKeys(), SALT);

        assertThat(body).hasSize(86 + 58);
        assertThat(Arrays.copyOf(body, 86)).as("머리 86바이트: salt · 레코드 크기 4096 · 키 길이 65 · as 공개키")
                .isEqualTo(b64("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"));
        assertThat(Arrays.copyOfRange(body, 86, body.length)).as("암호문")
                .isEqualTo(b64("8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ"));
        assertThat(body).isEqualTo(b64("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN"));
    }

    /**
     * 실제로 쓰는 쪽(매번 새 키·salt)도 받는 쪽이 풀 수 있어야 한다.
     * 받는 쪽(브라우저)의 개인키로 거꾸로 풀어 원문이 나오는지 본다.
     */
    @Test
    void 매번_새_키로_암호화해도_받는_쪽이_풀_수_있다() throws Exception {
        byte[] message = "{\"title\":\"골목이웃\",\"body\":\"안녕하세요\"}".getBytes(StandardCharsets.UTF_8);
        byte[] first = WebPushEncryptor.encrypt(message, UA_PUBLIC, AUTH_SECRET);
        byte[] second = WebPushEncryptor.encrypt(message, UA_PUBLIC, AUTH_SECRET);

        assertThat(first).as("같은 내용도 매번 다른 암호문").isNotEqualTo(second);
        assertThat(decryptAsBrowser(first)).isEqualTo(message);
        assertThat(decryptAsBrowser(second)).isEqualTo(message);
    }

    @Test
    void 한_레코드에_들어가지_않는_본문은_거부한다() {
        assertThatThrownBy(() -> WebPushEncryptor.encrypt(new byte[WebPushEncryptor.MAX_PLAINTEXT + 1], UA_PUBLIC, AUTH_SECRET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 형식이_틀린_브라우저_공개키는_거부한다() {
        assertThatThrownBy(() -> WebPushEncryptor.encrypt(PLAINTEXT, new byte[65], AUTH_SECRET))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebPushEncryptor.encrypt(PLAINTEXT, Arrays.copyOf(UA_PUBLIC, 64), AUTH_SECRET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 브라우저가 하는 일을 테스트에서 흉내 낸다: 머리에서 salt·서버 공개키를 읽어 같은 키를 유도해 푼다. */
    private static byte[] decryptAsBrowser(byte[] body) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        assertThat(buffer.getInt()).isEqualTo(WebPushEncryptor.RECORD_SIZE);
        byte[] serverPublic = new byte[buffer.get()];
        buffer.get(serverPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        byte[] ecdh = P256.agree(P256.decodePrivate(UA_PRIVATE), P256.decodePublic(serverPublic));
        WebPushEncryptor.Keys keys = WebPushEncryptor.deriveKeys(ecdh, AUTH_SECRET, UA_PUBLIC, serverPublic, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keys.cek(), "AES"), new GCMParameterSpec(128, keys.nonce()));
        byte[] record = cipher.doFinal(ciphertext);
        assertThat(record[record.length - 1]).as("마지막 레코드 표시").isEqualTo((byte) 0x02);
        return Arrays.copyOf(record, record.length - 1);
    }
}
