package com.golmok.market.domain.clienterror;

import com.golmok.market.domain.clienterror.dto.ClientErrorRequest;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 화면 오류 보고. 화면의 오류 경계가 잡은 렌더 오류를 받아 서버 로그에 남긴다.
 *
 * 로그인 없이 받는다 — 로그인하지 않은 사람의 화면도 망가질 수 있고, 토큰이 만료된 채 망가진 화면이 재발급까지 할 수는 없다.
 * 대신 요청 수를 제한하고(ClientErrorRateLimiter), 사람을 가릴 수 있는 값은 받지 않는다(ClientErrorRequest).
 */
@RestController
@RequestMapping("/api/client-errors")
@RequiredArgsConstructor
public class ClientErrorController {

    private final ClientErrorRateLimiter rateLimiter;
    private final ClientErrorLogger clientErrorLogger;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@Valid @RequestBody ClientErrorRequest request,
                       @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                       HttpServletRequest servletRequest) {
        if (!rateLimiter.tryAcquire(ClientIp.of(servletRequest))) {
            throw new BusinessException(ErrorCode.TOO_MANY_CLIENT_ERRORS);
        }
        clientErrorLogger.log(request, userAgent);
    }
}
