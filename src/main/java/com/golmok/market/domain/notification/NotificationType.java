package com.golmok.market.domain.notification;

public enum NotificationType {
    CHAT,        // 새 채팅 메시지 (만들지 않는다. 채팅 목록의 안 읽은 수가 대신한다)
    FAVORITE,    // 내 상품을 누가 찜함
    PRICE_DROP,  // 찜한 상품 가격 인하
    TRADE,       // 거래 상태 변경 · 후기 수신
    SYSTEM;

    /**
     * 같은 대상에 안 읽은 알림이 이미 있으면 새로 만들지 않는 종류.
     * 찜은 눌렀다 풀었다를 반복할 수 있어, 막지 않으면 판매자 알림함이 같은 알림으로 쌓인다.
     */
    public boolean collapsesUnread() {
        return this == FAVORITE;
    }
}
