package com.golmok.market.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 개인 큐(/user/queue/chat)로 내려가는 실시간 이벤트.
 *
 * 연결 하나로 채팅방 화면과 채팅 목록 화면을 함께 갱신하도록 모든 이벤트에 roomId 를 담는다.
 * 종류마다 쓰지 않는 필드는 응답에서 뺀다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRealtimeEvent(Type type, Long roomId, ChatMessageResponse message, Long readerId) {

    public enum Type {
        /** 새 메시지 */
        MESSAGE,
        /** readerId 가 상대 메시지를 모두 읽었다 */
        READ
    }

    public static ChatRealtimeEvent message(ChatMessageResponse message) {
        return new ChatRealtimeEvent(Type.MESSAGE, message.roomId(), message, null);
    }

    public static ChatRealtimeEvent read(Long roomId, Long readerId) {
        return new ChatRealtimeEvent(Type.READ, roomId, null, readerId);
    }
}
