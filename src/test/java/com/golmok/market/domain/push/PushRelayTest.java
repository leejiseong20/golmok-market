package com.golmok.market.domain.push;

import com.golmok.market.domain.chat.MessageType;
import com.golmok.market.domain.chat.dto.ChatMessageResponse;
import com.golmok.market.domain.chat.event.ChatMessageSentEvent;
import com.golmok.market.domain.notification.NotificationType;
import com.golmok.market.domain.notification.dto.NotificationResponse;
import com.golmok.market.domain.notification.event.NotificationCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 누구에게 보내고 누구에게 보내지 않는지. 이벤트 → 발송 판단만 떼어 본다(발송 자체는 PushApiTest).
 */
class PushRelayTest {

    private static final long SENDER = 1L;
    private static final long RECEIVER = 2L;

    private PushService pushService;
    private SimpUserRegistry registry;
    private PushRelay relay;

    @BeforeEach
    void 준비() {
        pushService = mock(PushService.class);
        registry = mock(SimpUserRegistry.class);
        when(pushService.enabled()).thenReturn(true);
        relay = new PushRelay(pushService, registry);
    }

    private static ChatMessageSentEvent chat(MessageType type, String content) {
        ChatMessageResponse message = new ChatMessageResponse(10L, 7L, SENDER, type, content, false, LocalDateTime.now());
        return new ChatMessageSentEvent(List.of(RECEIVER, SENDER), message, "보낸사람");
    }

    @Test
    void 채팅은_보낸_사람을_빼고_상대에게_보낸다() {
        relay.onChatMessage(chat(MessageType.TEXT, "아직 판매하시나요?"));

        ArgumentCaptor<PushMessage> sent = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).send(eq(RECEIVER), sent.capture());
        verify(pushService, never()).send(eq(SENDER), any());
        assertThat(sent.getValue()).isEqualTo(new PushMessage("보낸사람", "아직 판매하시나요?",
                "/chat-rooms/7", "chat-room-7", PushGateway.Urgency.HIGH));
    }

    /** 앱을 보고 있으면(WebSocket 연결 중) 화면이 이미 바뀐다. 푸시까지 오면 같은 내용을 두 번 받는다. */
    @Test
    void 앱을_보고_있는_사람에게는_보내지_않는다() {
        when(registry.getUser(String.valueOf(RECEIVER))).thenReturn(mock(SimpUser.class));

        relay.onChatMessage(chat(MessageType.TEXT, "안녕하세요"));

        verify(pushService, never()).send(anyLong(), any());
    }

    /** 예약·거래완료 시스템 메시지는 거래 알림이 따로 푸시된다. 둘 다 보내면 한 사건에 알림이 두 개다. */
    @Test
    void 시스템_메시지는_보내지_않는다() {
        relay.onChatMessage(chat(MessageType.SYSTEM, "예약이 확정됐어요"));
        verify(pushService, never()).send(anyLong(), any());
    }

    @Test
    void 알림은_받는_사람에게_보낸다() {
        NotificationResponse notification = new NotificationResponse(31L, NotificationType.TRADE,
                "판매자가 예약했어요", "원목 식탁", "/chat-rooms/7", false, LocalDateTime.now());

        relay.onNotification(new NotificationCreatedEvent(RECEIVER, notification));

        verify(pushService).send(RECEIVER, new PushMessage("판매자가 예약했어요", "원목 식탁",
                "/chat-rooms/7", "notification-31", PushGateway.Urgency.NORMAL));
    }

    @Test
    void 푸시가_꺼져_있으면_아무것도_하지_않는다() {
        when(pushService.enabled()).thenReturn(false);
        relay.onChatMessage(chat(MessageType.TEXT, "안녕하세요"));
        verify(pushService, never()).send(anyLong(), any());
    }

    /** 잠금 화면에는 몇 줄만 보이고 푸시 본문 크기에도 한도가 있다. 이모지가 반으로 잘리지 않게 글자 단위로 자른다. */
    @Test
    void 긴_메시지는_글자_단위로_자른다() {
        String longText = "🍎".repeat(PushMessage.BODY_LIMIT + 10);
        relay.onChatMessage(chat(MessageType.TEXT, longText));

        ArgumentCaptor<PushMessage> sent = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).send(eq(RECEIVER), sent.capture());
        String body = sent.getValue().body();
        assertThat(body).endsWith("…");
        assertThat(body.codePointCount(0, body.length())).isEqualTo(PushMessage.BODY_LIMIT + 1);
    }

    /** 푸시 서비스 오류가 실행기 밖으로 새지 않는다(다음 사람에게는 계속 보낸다). */
    @Test
    void 한_사람에게_실패해도_예외를_던지지_않는다() {
        org.mockito.Mockito.doThrow(new IllegalStateException("푸시 서비스 장애")).when(pushService).send(anyLong(), any());
        relay.onChatMessage(chat(MessageType.TEXT, "안녕하세요"));
        verify(pushService).send(eq(RECEIVER), any());
    }
}
