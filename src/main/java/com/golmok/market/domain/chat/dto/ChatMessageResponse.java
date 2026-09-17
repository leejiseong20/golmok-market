package com.golmok.market.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.golmok.market.domain.chat.ChatMessage;
import com.golmok.market.domain.chat.MessageType;

import java.time.LocalDateTime;

/**
 * 메시지 한 건. 보낸 사람은 id 만 준다.
 * 1:1 방이라 상대 닉네임은 채팅방 정보에 이미 있고, 메시지마다 회원을 조회하면 N+1 이 된다.
 */
public record ChatMessageResponse(
        Long id,
        Long roomId,
        Long senderId,
        MessageType type,
        String content,
        boolean read,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt
) {

    public static ChatMessageResponse from(ChatMessage message) {
        // 연관 엔티티의 id 는 프록시를 초기화하지 않고 꺼낼 수 있다.
        return new ChatMessageResponse(
                message.getId(),
                message.getRoom().getId(),
                message.getSender().getId(),
                message.getType(),
                message.getContent(),
                message.isRead(),
                message.getCreatedAt());
    }
}
