package com.golmok.market.domain.notification.event;

import com.golmok.market.domain.notification.NotificationType;

/**
 * 알림을 만들어 달라는 요청. 찜·거래·후기 서비스가 자기 트랜잭션 안에서 발행한다.
 *
 * 문구를 발행하는 쪽에서 완성해 담는다. 알림은 커밋 뒤에 만들어지는데,
 * 그때는 원래 트랜잭션의 영속성 컨텍스트가 닫혀 상품 제목·닉네임을 지연 로딩할 수 없다.
 */
public record NotificationRequestedEvent(long recipientId, NotificationType type,
                                         String title, String content, String targetUrl) {

    public static String productUrl(long productId) {
        return "/products/" + productId;
    }

    public static String chatRoomUrl(long roomId) {
        return "/chat-rooms/" + roomId;
    }

    /** 받은 후기는 본인 마이페이지의 받은 후기 탭에서 본다. */
    public static final String MY_REVIEWS_URL = "/my/reviews";
}
