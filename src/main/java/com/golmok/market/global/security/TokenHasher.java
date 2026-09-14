package com.golmok.market.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * refresh token 생성과 해시.
 *
 * DB 에는 원문이 아니라 SHA-256 해시만 저장한다.
 * 원문을 저장하면 DB 가 유출됐을 때 그 값으로 바로 다른 사람으로 로그인할 수 있다.
 *
 * 비밀번호처럼 BCrypt 를 쓰지 않는 이유:
 * - 토큰은 256비트 무작위 값이라 사전 대입·무차별 대입이 불가능하다. 느린 해시가 막아줄 공격이 없다.
 * - BCrypt 는 솔트 때문에 같은 입력도 매번 결과가 달라 "해시로 조회"(UNIQUE 인덱스)를 할 수 없다.
 */
public final class TokenHasher {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenHasher() {
    }

    /** 클라이언트에게 내려줄 원문. URL-safe Base64, 43자. */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** DB 에 저장·조회할 값. 16진수 64자. */
    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Java SE 명세상 모든 JVM 은 SHA-256 을 지원해야 하므로 발생하지 않는다.
            throw new IllegalStateException(e);
        }
    }
}
