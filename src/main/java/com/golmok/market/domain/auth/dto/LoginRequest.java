package com.golmok.market.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * 로그인은 형식 검증을 최소화한다. 형식이 틀린 이메일은 어차피 가입돼 있지 않아 LOGIN_FAILED 가 된다.
 * 비밀번호 최대 길이만 막는다. BCrypt 에 72바이트를 넘는 값을 넘기지 않기 위함.
 */
public record LoginRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(max = 64, message = "비밀번호는 64자 이하여야 합니다.")
        String password
) {

    public String normalizedEmail() {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
