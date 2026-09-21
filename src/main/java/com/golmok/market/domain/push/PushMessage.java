package com.golmok.market.domain.push;

import com.golmok.market.domain.chat.dto.ChatMessageResponse;
import com.golmok.market.domain.notification.dto.NotificationResponse;

/**
 * 기기에 보낼 알림 한 건. 서비스 워커가 이 내용으로 알림을 띄우고, 누르면 url 로 간다.
 *
 * @param tag 같은 tag 의 알림은 기기에서 하나로 겹쳐진다. 같은 채팅방 메시지가 여러 개 와도 알림이 쌓이지 않는다.
 */
public record PushMessage(String title, String body, String url, String tag, PushGateway.Urgency urgency) {

    /** 알림 본문은 짧게 자른다. 잠금 화면에 몇 줄만 보이고, 푸시 본문 크기에도 한도가 있다. */
    static final int BODY_LIMIT = 120;

    public static PushMessage chat(String senderNickname, ChatMessageResponse message) {
        return new PushMessage(senderNickname, shorten(message.content()),
                "/chat-rooms/" + message.roomId(), "chat-room-" + message.roomId(), PushGateway.Urgency.HIGH);
    }

    public static PushMessage notification(NotificationResponse notification) {
        return new PushMessage(notification.title(), shorten(notification.content()),
                notification.targetUrl(), "notification-" + notification.id(), PushGateway.Urgency.NORMAL);
    }

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        return text.codePointCount(0, text.length()) <= BODY_LIMIT
                ? text
                : text.substring(0, text.offsetByCodePoints(0, BODY_LIMIT)) + "…";
    }
}
