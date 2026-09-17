package com.golmok.market.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.golmok.market.domain.chat.ChatRoom;

import java.time.LocalDateTime;

/** 채팅 목록 한 줄. 상대·상품·마지막 메시지·안 읽은 수를 한 번에 그릴 수 있게 한다. */
public record ChatRoomSummaryResponse(
        Long roomId,
        ChatRoomResponse.ProductInfo product,
        ChatRoomResponse.Opponent opponent,
        String lastMessage,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime lastMessageAt,
        long unreadCount
) {

    public static ChatRoomSummaryResponse of(ChatRoom room, Long viewerId, String thumbnailUrl, long unreadCount) {
        return new ChatRoomSummaryResponse(
                room.getId(),
                ChatRoomResponse.ProductInfo.of(room.getProduct(), thumbnailUrl),
                ChatRoomResponse.Opponent.of(room.getOpponent(viewerId)),
                room.getLastMessage(),
                room.getLastMessageAt(),
                unreadCount);
    }
}
