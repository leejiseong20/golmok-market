package com.golmok.market.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * 비밀번호 규칙: 8~64자, 영문·숫자·특수문자 각 1자 이상, 공백·한글 불가(ASCII 출력 가능 문자만).
 *
 * 왜 ASCII 로 제한하는가: BCrypt 는 72바이트까지만 처리한다.
 * 글자 수만 64자로 막으면 한글(UTF-8 3바이트)이 섞일 때 72바이트를 넘어 가입이 500 으로 실패한다.
 * ASCII 로 제한하면 64자 = 64바이트라 항상 안전하다.
 */
public record SignupRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 아닙니다.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
        @Pattern(regexp = PASSWORD_PATTERN,
                message = "비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 하며 공백과 한글은 사용할 수 없습니다.")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 30, message = "닉네임은 2~30자여야 합니다.")
        String nickname,

        @Pattern(regexp = "^01\\d{8,9}$", message = "휴대폰 번호는 숫자만 10~11자리로 입력해 주세요.")
        String phone
) {

    /** 영문 1+, 숫자 1+, 특수문자 1+, 전체가 ASCII 출력 가능 문자(! ~ ~) */
    static final String PASSWORD_PATTERN =
            "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!-/:-@\\[-`{-~])[!-~]+$";

    /**
     * 이메일은 소문자로 저장한다. 운영 DB(utf8mb4_unicode_ci)는 대소문자를 구분하지 않아
     * A@x.com 과 a@x.com 이 같은 값으로 취급되지만, H2 는 구분한다. 저장 전에 맞춰 두면 둘의 동작이 같아진다.
     */
    public String normalizedEmail() {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
