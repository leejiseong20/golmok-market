package com.golmok.market.global.config;

import com.golmok.market.global.security.StompAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * 실시간 채팅용 STOMP over WebSocket.
 *
 * WebSocket 은 서버 → 클라이언트 알림(푸시)만 맡는다. 메시지 전송은 REST(POST /api/chat-rooms/{id}/messages)다.
 * 검증·에러 형식·잠금을 REST 에 이미 두었고, 전송 경로가 둘이면 같은 규칙을 두 번 구현해야 한다.
 *
 * 접속 경로를 /api 아래에 둔 이유: Vite 개발 프록시와 배포용 Caddy 가 /api 만 백엔드로 넘긴다.
 *
 * 브로커는 메모리 내장(simple broker)이라 서버 한 대에서만 동작한다.
 * 서버를 여러 대로 늘리면 다른 서버에 붙은 사용자에게 전달되지 않으므로 Redis Pub/Sub 이나
 * 외부 STOMP 브로커(RabbitMQ) 릴레이로 바꿔야 한다. 지금 배포 구성은 서버 한 대다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String ENDPOINT = "/api/ws";

    /** 하트비트 간격(ms). 조용한 연결을 프록시나 공유기가 끊지 않게 하고, 끊긴 연결을 서버가 알아채게 한다. */
    private static final long HEARTBEAT_MILLIS = 10_000;

    private final StompAuthChannelInterceptor authInterceptor;
    private final List<String> allowedOrigins;
    private TaskScheduler heartbeatScheduler;

    public WebSocketConfig(StompAuthChannelInterceptor authInterceptor,
                           @Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * 메시지 브로커 설정이 만드는 스케줄러를 하트비트에 쓴다.
     * 같은 설정 클래스가 만드는 빈이라 생성자로 받으면 순환 참조가 되므로 @Lazy 로 늦춰 받는다(Spring 문서의 방식).
     */
    @Autowired
    public void setHeartbeatScheduler(@Lazy TaskScheduler messageBrokerTaskScheduler) {
        this.heartbeatScheduler = messageBrokerTaskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 연결 요청은 CORS 필터가 아니라 여기서 출처를 검사한다. REST 와 같은 허용 목록을 쓴다.
        // SockJS(구형 브라우저 대체 전송)는 쓰지 않는다. 지원 대상 브라우저는 모두 WebSocket 을 지원한다.
        registry.addEndpoint(ENDPOINT).setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 방별 공개 채널(/topic/...)을 두지 않고 사용자별 개인 큐만 쓴다.
        // 방별 채널은 "남의 방 번호로 구독"을 막는 권한 검사가 필요하고, 빠뜨리면 남의 대화가 샌다.
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[]{HEARTBEAT_MILLIS, HEARTBEAT_MILLIS})
                .setTaskScheduler(heartbeatScheduler);
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
