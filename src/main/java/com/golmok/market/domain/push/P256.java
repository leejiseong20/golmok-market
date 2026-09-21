package com.golmok.market.domain.push;

import javax.crypto.KeyAgreement;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

/**
 * 웹 푸시가 쓰는 P-256 타원곡선 키 다루기. JDK 표준 API 만 쓴다(외부 암호 라이브러리 없음).
 *
 * 웹 푸시는 키를 X9.62 "비압축" 형식으로 주고받는다: 0x04 + X(32바이트) + Y(32바이트) = 65바이트.
 * JDK 는 이 형식을 직접 읽지 못해 좌표를 잘라 ECPoint 로 만든다.
 */
final class P256 {

    static final int PUBLIC_KEY_LENGTH = 65;
    private static final int COORDINATE_LENGTH = 32;
    private static final ECParameterSpec PARAMS = params();

    private P256() {
    }

    private static ECParameterSpec params() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 곡선을 쓸 수 없습니다", e);
        }
    }

    static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(PARAMS);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("키를 만들 수 없습니다", e);
        }
    }

    /** 비압축 형식(65바이트) → 공개키. 형식이 틀리거나 곡선 위의 점이 아니면 IllegalArgumentException. */
    static ECPublicKey decodePublic(byte[] encoded) {
        if (encoded == null || encoded.length != PUBLIC_KEY_LENGTH || encoded[0] != 0x04) {
            throw new IllegalArgumentException("P-256 비압축 공개키(65바이트)가 아닙니다");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(encoded, 1, 1 + COORDINATE_LENGTH));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(encoded, 1 + COORDINATE_LENGTH, PUBLIC_KEY_LENGTH));
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), PARAMS));
        } catch (GeneralSecurityException e) {
            // 곡선 위에 없는 점이면 여기서 걸린다. 잘못된 점으로 키 합의를 하면 비밀이 새는 공격이 있다.
            throw new IllegalArgumentException("P-256 공개키가 올바르지 않습니다", e);
        }
    }

    static PrivateKey decodePrivate(byte[] scalar) {
        try {
            return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), PARAMS));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("P-256 개인키가 올바르지 않습니다", e);
        }
    }

    /** 공개키 → 비압축 형식(65바이트). */
    static byte[] encode(PublicKey key) {
        ECPoint point = ((ECPublicKey) key).getW();
        byte[] encoded = new byte[PUBLIC_KEY_LENGTH];
        encoded[0] = 0x04;
        copyFixed(point.getAffineX(), encoded, 1);
        copyFixed(point.getAffineY(), encoded, 1 + COORDINATE_LENGTH);
        return encoded;
    }

    /** ECDH 공유 비밀(32바이트). */
    static byte[] agree(PrivateKey own, PublicKey peer) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(own);
            agreement.doPhase(peer, true);
            return agreement.generateSecret();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("키 합의에 실패했습니다", e);
        }
    }

    /**
     * 좌표를 정확히 32바이트로 쓴다. BigInteger.toByteArray() 는 부호 바이트(0x00)가 붙어 33바이트가 되거나,
     * 앞자리가 0 이면 31바이트 이하로 짧아진다. 그대로 쓰면 65바이트 형식이 깨진다.
     */
    private static void copyFixed(BigInteger value, byte[] target, int offset) {
        byte[] bytes = value.toByteArray();
        int start = Math.max(0, bytes.length - COORDINATE_LENGTH);
        int length = bytes.length - start;
        System.arraycopy(bytes, start, target, offset + COORDINATE_LENGTH - length, length);
    }
}
