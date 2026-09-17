package com.golmok.market.global.security;

import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * STOMP 프레임 단위 인증·인가.
 *
 * 인증은 WebSocket 연결 요청(HTTP)이 아니라 첫 STOMP 프레임(CONNECT)에서 한다.
 * 브라우저 WebSocket API 는 연결 요청에 Authorization 헤더를 붙일 수 없고,
 * 토큰을 URL 쿼리에 넣으면 서버·프록시 접속 로그에 토큰이 남는다.
 *
 * 허용하는 프레임
 * - CONNECT     : Authorization: Bearer {accessToken} 필수
 * - SUBSCRIBE   : 내 개인 큐({@link #CHAT_SUBSCRIPTION}) 하나만
 * - UNSUBSCRIBE, DISCONNECT, 하트비트
 * SEND 는 거부한다. 메시지 전송은 REST 다. 막지 않으면 브로커 목적지로 직접 보내
 * 다른 사용자의 큐에 가짜 메시지를 넣는 것이 가능해진다.
 *
 * 거부하면 ERROR 프레임의 message 헤더에 에러 코드(EXPIRED_TOKEN 등)를 담고 연결을 끊는다.
 * 프론트는 EXPIRED_TOKEN 이면 REST 로 토큰을 재발급한 뒤 다시 연결한다.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /** 클라이언트가 구독하는 주소. 서버는 사용자별 실제 큐(/queue/chat-user{세션})로 바꿔 전달한다. */
    public static final String CHAT_SUBSCRIPTION = "/user/queue/chat";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public StompAuthChannelInterceptor(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    /**
     * WebSocket 세션의 사용자. 사용자별 큐는 Principal 이름으로 찾으므로 이름을 회원 id 로 둔다.
     * 서버가 보낼 때도 같은 규칙(회원 id 문자열)으로 대상을 지정한다.
     */
    public record StompUser(AuthUser authUser) implements Principal {

        @Override
        public String getName() {
            return String.valueOf(authUser.id());
        }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // 하트비트
        }
        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT -> accessor.setUser(new StompUser(authenticate(accessor)));
            case SUBSCRIBE -> requireOwnQueue(accessor);
            case UNSUBSCRIBE, DISCONNECT -> {
            }
            default -> throw reject(ErrorCode.FORBIDDEN);
        }
        return message;
    }

    private AuthUser authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw reject(ErrorCode.UNAUTHORIZED);
        }
        try {
            return tokenProvider.parseAccessToken(header.substring(BEARER_PREFIX.length()).trim());
        } catch (BusinessException e) {
            throw reject(e.getErrorCode());
        }
    }

    private void requireOwnQueue(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw reject(ErrorCode.UNAUTHORIZED);
        }
        // 서버 내부 이름(/queue/chat-user...)으로 직접 구독하는 것도 여기서 막힌다.
        if (!CHAT_SUBSCRIPTION.equals(accessor.getDestination())) {
            throw reject(ErrorCode.FORBIDDEN);
        }
    }

    /** ERROR 프레임의 message 헤더가 이 예외 메시지가 된다. 프론트가 분기할 수 있게 코드만 담는다. */
    private static MessageDeliveryException reject(ErrorCode errorCode) {
        return new MessageDeliveryException(errorCode.name());
    }
}
