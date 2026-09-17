package com.golmok.market.domain.chat;

import com.golmok.market.domain.chat.dto.ChatRealtimeEvent;
import com.golmok.market.domain.chat.event.ChatMessageSentEvent;
import com.golmok.market.domain.chat.event.ChatMessagesReadEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 채팅 변경을 참여자의 개인 큐로 밀어준다.
 *
 * 커밋이 끝난 뒤에만 보낸다(@TransactionalEventListener 기본값 AFTER_COMMIT).
 * 트랜잭션 안에서 보내면 저장이 롤백된 메시지가 상대 화면에 먼저 뜰 수 있다.
 *
 * 전달은 최선 노력이다. 상대가 접속해 있지 않으면 그냥 버려지고, 다시 접속한 화면이 REST 로 불러온다.
 * 그래서 전달 실패가 이미 성공한 REST 응답을 실패로 바꾸지 않도록 예외를 삼키고 기록만 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRealtimeRelay {

    /** 서버 기준 목적지. 구독 주소 /user/queue/chat 과 짝이다. */
    private static final String DESTINATION = "/queue/chat";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener
    public void onMessageSent(ChatMessageSentEvent event) {
        push(event.participantIds(), ChatRealtimeEvent.message(event.message()));
    }

    @TransactionalEventListener
    public void onMessagesRead(ChatMessagesReadEvent event) {
        push(event.participantIds(), ChatRealtimeEvent.read(event.roomId(), event.readerId()));
    }

    private void push(List<Long> userIds, ChatRealtimeEvent payload) {
        for (Long userId : userIds) {
            try {
                // 사용자 이름 규칙은 StompAuthChannelInterceptor.StompUser 와 같다(회원 id 문자열).
                messagingTemplate.convertAndSendToUser(String.valueOf(userId), DESTINATION, payload);
            } catch (MessagingException e) {
                log.warn("채팅 실시간 전달 실패 userId={}, roomId={}, type={}", userId, payload.roomId(), payload.type(), e);
            }
        }
    }
}
