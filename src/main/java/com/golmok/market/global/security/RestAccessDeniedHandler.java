package com.golmok.market.global.security;

import com.golmok.market.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 로그인은 했지만 URL 단위 권한이 없을 때 403.
 *
 * **관리자 경로만 404 로 답한다.** 403 을 주면 "이 주소에 관리자 기능이 있다"는 사실이 드러나
 * 어디를 두드려야 하는지 알려 주는 꼴이 된다(남의 채팅방을 404 로 감추는 것과 같은 이유다).
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final String ADMIN_PREFIX = "/api/admin/";

    private final SecurityErrorWriter errorWriter;

    public RestAccessDeniedHandler(SecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        boolean admin = request.getRequestURI() != null && request.getRequestURI().startsWith(ADMIN_PREFIX);
        errorWriter.write(response, admin ? ErrorCode.RESOURCE_NOT_FOUND : ErrorCode.FORBIDDEN);
    }
}
