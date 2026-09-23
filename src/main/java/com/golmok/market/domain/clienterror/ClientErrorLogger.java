package com.golmok.market.domain.clienterror;

import com.golmok.market.domain.clienterror.dto.ClientErrorRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 화면 오류를 서버 로그에 한 줄로 남긴다. DB 에는 넣지 않는다 — 운영 중 사용자에게 난 오류를 "알 수 있게" 하는 것이 목적이고,
 * 모아 보는 화면이 필요해지면 그때 표를 만든다. 보는 법: `docker compose logs app | grep client-error`.
 *
 * **남기기 전에 제어문자(줄바꿈 포함)를 지운다.** 메시지는 브라우저가 마음대로 보낼 수 있는 값이라,
 * 줄바꿈을 넣어 가짜 로그 줄(예: 다른 사용자의 로그인 기록)을 끼워 넣을 수 있다. 한 보고는 반드시 한 줄이다.
 */
@Slf4j
@Component
public class ClientErrorLogger {

    static final int USER_AGENT_MAX = 200;

    public void log(ClientErrorRequest request, String userAgent) {
        log.warn("[client-error] kind={} boundary={} path={} message={} userAgent={} stack={}",
                request.kind(), clean(request.boundary(), 30), clean(request.path(), 200),
                clean(request.message(), 300), clean(userAgent, USER_AGENT_MAX), clean(request.componentStack(), 2000));
    }

    /**
     * 제어문자를 공백으로 바꾸고, 이어진 공백을 하나로 줄이고, 길이를 자른다. 비었으면 "-".
     * 컴포넌트 스택은 원래 여러 줄이라 이렇게 하면 "at A at B" 처럼 한 줄로 이어진다.
     */
    static String clean(String value, int max) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        // Cc: 모든 제어문자(ASCII 줄바꿈과 U+0085 NEL 포함), Zl·Zp: 유니코드 줄·문단 구분자(U+2028·U+2029).
        String oneLine = value.replaceAll("[\\p{Cc}\\p{Zl}\\p{Zp}]", " ").replaceAll(" {2,}", " ").strip();
        if (oneLine.isEmpty()) {
            return "-";
        }
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }
}
