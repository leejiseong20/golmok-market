package com.golmok.market.domain.push;

import com.golmok.market.domain.chat.MessageType;
import com.golmok.market.domain.chat.event.ChatMessageSentEvent;
import com.golmok.market.domain.notification.event.NotificationCreatedEvent;
import com.golmok.market.global.config.AsyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림·채팅이 커밋된 뒤 푸시를 보낸다. 전용 실행기에서 돌아 요청 스레드를 붙잡지 않는다.
 *
 * <b>앱을 보고 있는 사람에게는 보내지 않는다.</b> WebSocket 이 연결돼 있으면 앱이 열려 있는 것이고,
 * 화면이 이미 실시간으로 바뀐다. 푸시까지 오면 같은 내용을 두 번 받는다.
 * 서비스 워커에서 거르지 않는 이유: iOS 와 Chrome 은 푸시를 받고 알림을 띄우지 않으면
 * 구독을 끊거나 "백그라운드에서 업데이트됨" 같은 기본 알림을 대신 띄운다. 받은 푸시는 반드시 보여야 한다.
 *
 * 한계: PC 에서 탭을 뒤로 숨겨 둔 사람도 연결은 살아 있어 푸시를 받지 못한다(탭의 안 읽은 뱃지로 본다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushRelay {

    private final PushService pushService;
    private final SimpUserRegistry userRegistry;

    @Async(AsyncConfig.PUSH_EXECUTOR)
    @TransactionalEventListener
    public void onNotification(NotificationCreatedEvent event) {
        if (!pushService.enabled() || online(event.recipientId())) {
            return;
        }
        send(event.recipientId(), PushMessage.notification(event.notification()));
    }

    /**
     * 시스템 메시지(예약·거래완료)는 보내지 않는다. 같은 일로 거래 알림이 따로 만들어지고,
     * 그 알림이 위 onNotification 으로 푸시된다. 둘 다 보내면 한 사건에 알림이 두 개 뜬다.
     */
    @Async(AsyncConfig.PUSH_EXECUTOR)
    @TransactionalEventListener
    public void onChatMessage(ChatMessageSentEvent event) {
        if (!pushService.enabled() || event.message().type() == MessageType.SYSTEM) {
            return;
        }
        for (Long participantId : event.participantIds()) {
            if (participantId.equals(event.message().senderId()) || online(participantId)) {
                continue;
            }
            send(participantId, PushMessage.chat(event.senderNickname(), event.message()));
        }
    }

    /** STOMP 세션의 사용자 이름은 회원 id 문자열이다(StompAuthChannelInterceptor). */
    private boolean online(long userId) {
        return userRegistry.getUser(String.valueOf(userId)) != null;
    }

    /** 실행기 스레드의 예외는 아무도 받지 않는다. 기록만 하고 넘어간다(푸시는 부가 기능이다). */
    private void send(long userId, PushMessage message) {
        try {
            pushService.send(userId, message);
        } catch (RuntimeException e) {
            log.warn("푸시 발송 중 오류: 사용자 {}", userId, e);
        }
    }
}
