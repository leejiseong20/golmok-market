package com.golmok.market.domain.notification;

import com.golmok.market.domain.notification.dto.NotificationResponse;
import com.golmok.market.domain.notification.event.NotificationCreatedEvent;
import com.golmok.market.global.config.WebSocketConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 저장된 알림을 받는 사람의 개인 큐로 밀어준다. 알림 트랜잭션이 커밋된 뒤에만 보낸다.
 *
 * 채팅과 같은 연결·같은 구독 주소를 쓴다. 구독 허용 목록을 늘리지 않고, 프론트는 type 으로 구분한다.
 * 전달은 최선 노력이다. 접속해 있지 않으면 버려지고, 다시 접속한 화면이 REST 로 개수를 불러온다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRealtimeRelay {

    private final SimpMessagingTemplate messagingTemplate;

    /** 개인 큐로 내려가는 알림 이벤트. 채팅 이벤트(MESSAGE·READ)와 type 으로 구분한다. */
    public record Payload(String type, NotificationResponse notification) {

        static Payload of(NotificationResponse notification) {
            return new Payload("NOTIFICATION", notification);
        }
    }

    @TransactionalEventListener
    public void onCreated(NotificationCreatedEvent event) {
        try {
            messagingTemplate.convertAndSendToUser(String.valueOf(event.recipientId()),
                    WebSocketConfig.USER_QUEUE, Payload.of(event.notification()));
        } catch (MessagingException e) {
            log.warn("알림 실시간 전달 실패 recipientId={}, notificationId={}",
                    event.recipientId(), event.notification().id(), e);
        }
    }
}
