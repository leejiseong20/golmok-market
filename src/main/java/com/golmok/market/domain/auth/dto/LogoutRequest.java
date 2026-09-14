package com.golmok.market.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 여러 기기 로그인을 허용하므로, 어느 기기의 세션을 끝낼지 refreshToken 으로 지정한다.
 */
public record LogoutRequest(

        @NotBlank(message = "refreshToken 은 필수입니다.")
        String refreshToken
) {
}
