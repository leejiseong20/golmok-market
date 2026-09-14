package com.golmok.market.domain.auth.dto;

/** refreshToken 은 DB 에 저장된 해시가 아니라 클라이언트가 보관할 원문이다. */
public record TokenResponse(String accessToken, String refreshToken) {
}
