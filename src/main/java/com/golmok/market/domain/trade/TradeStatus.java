package com.golmok.market.domain.trade;

import java.util.EnumSet;
import java.util.Set;

/**
 * 거래 상태. 전이 규칙을 enum 안에 박아두면
 * 서비스 레이어마다 if 문이 흩어지는 것을 막을 수 있다.
 */
public enum TradeStatus {

    REQUESTED,  // 거래 요청됨 (결제 전)
    PAID,       // 결제 완료
    SHIPPING,   // 발송됨
    CONFIRMED,  // 구매 확정
    CANCELED,   // 취소
    REFUNDED;   // 환불

    public Set<TradeStatus> nextStates() {
        return switch (this) {
            case REQUESTED -> EnumSet.of(PAID, CANCELED);
            case PAID      -> EnumSet.of(SHIPPING, CONFIRMED, REFUNDED);
            case SHIPPING  -> EnumSet.of(CONFIRMED, REFUNDED);
            case CONFIRMED, CANCELED, REFUNDED -> EnumSet.noneOf(TradeStatus.class);
        };
    }

    public boolean canTransitionTo(TradeStatus next) {
        return nextStates().contains(next);
    }

    /** 진행 중인 거래인지. active_product_id 생성컬럼과 같은 기준이어야 한다. */
    public boolean isActive() {
        return this != CANCELED && this != REFUNDED;
    }

    public boolean isFinished() {
        return nextStates().isEmpty();
    }
}
