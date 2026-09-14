package com.golmok.market.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * application.yml 의 jwt.* 설정.
 *
 * @param secret               HMAC-SHA 서명 키. Base64 인코딩, 디코딩 후 32바이트(256비트) 이상.
 *                             반드시 환경변수(JWT_SECRET)로 주입하고 커밋하지 않는다.
 * @param accessTokenValidity  access token 유효 기간 (명세: 30분)
 * @param refreshTokenValidity refresh token 유효 기간 (명세: 14일)
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenValidity,
        Duration refreshTokenValidity
) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret 이 설정되지 않았습니다. JWT_SECRET 환경변수를 확인하세요.");
        }
        if (accessTokenValidity == null || refreshTokenValidity == null) {
            throw new IllegalStateException("jwt 토큰 유효 기간 설정이 없습니다.");
        }
    }
}
