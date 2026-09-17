package com.golmok.market.domain.chat;

/** 방별 안 읽은 메시지 수 집계 결과. JPQL 생성자 표현식이 COUNT 를 Long 으로 넘기므로 래퍼 타입이다. */
public record RoomUnreadCount(Long roomId, Long count) {
}
