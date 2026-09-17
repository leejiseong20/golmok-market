package com.golmok.market.domain.product.event;

/**
 * 판매자가 가격을 내렸다. 받는 사람(찜한 사람 전원)은 알림 쪽이 커밋 뒤에 조회한다.
 * 상품 수정 트랜잭션이 찜 목록 크기만큼 길어지지 않게 하기 위해서다.
 */
public record ProductPriceDroppedEvent(long productId, String title, int oldPrice, int newPrice) {
}
