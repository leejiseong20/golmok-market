package com.golmok.market.domain.notification;

public enum NotificationType {
    CHAT,        // 새 채팅 메시지
    FAVORITE,    // 내 상품을 누가 찜함
    PRICE_DROP,  // 찜한 상품 가격 인하
    TRADE,       // 거래 상태 변경
    SYSTEM
}
