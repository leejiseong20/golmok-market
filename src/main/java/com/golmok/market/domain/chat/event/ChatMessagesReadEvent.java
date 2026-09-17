package com.golmok.market.domain.chat.event;

import java.util.List;

/**
 * 한 방에서 상대 메시지를 읽음 처리했다.
 * 상대 화면은 내가 보낸 메시지에 "읽음"을 켜고, 읽은 사람의 다른 탭은 안 읽은 수를 0 으로 맞춘다.
 */
public record ChatMessagesReadEvent(List<Long> participantIds, Long roomId, Long readerId) {
}
