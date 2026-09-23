package com.golmok.market.global.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청한 클라이언트의 주소. 로그인 실패 제한과 오류 보고 제한이 같은 규칙을 쓴다.
 *
 * 운영에서는 Caddy 를 거치므로 소켓 주소는 항상 Caddy 다. Caddy 는 X-Forwarded-For 뒤에 실제 접속 IP 를 덧붙이므로
 * **맨 뒤 값**이 우리 앞단이 직접 본 주소다. 앞쪽 값은 클라이언트가 마음대로 적어 보낼 수 있어 믿지 않는다.
 * 헤더가 없으면(로컬 개발·직접 접속) 소켓 주소를 쓴다.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] hops = forwarded.split(",");
        return hops[hops.length - 1].trim();
    }
}
