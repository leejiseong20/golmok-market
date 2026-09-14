package com.golmok.market.global.security;

import com.golmok.market.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증이 필요한 경로에 토큰 없이 접근했을 때 401.
 * 기본 동작(로그인 페이지 리다이렉트 / WWW-Authenticate 헤더) 대신 명세의 에러 형식으로 응답한다.
 *
 * 토큰이 "있는데 틀린" 경우는 여기까지 오지 않고 JwtAuthenticationFilter 가
 * EXPIRED_TOKEN / INVALID_TOKEN 으로 먼저 응답한다.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorWriter errorWriter;

    public RestAuthenticationEntryPoint(SecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        errorWriter.write(response, ErrorCode.UNAUTHORIZED);
    }
}
