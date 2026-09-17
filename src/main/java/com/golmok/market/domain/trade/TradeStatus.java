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
            // REQUESTED → CONFIRMED: 결제 없이 만나서 거래한 직거래를 판매자가 완료한다.
            case REQUESTED -> EnumSet.of(PAID, CONFIRMED, CANCELED);
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

    /**
     * 구매자가 "구매확정"을 누를 수 있는 상태. 결제가 끝난 거래만이다.
     * canTransitionTo(CONFIRMED) 로 판단하면 안 된다. 직거래 완료를 위해 REQUESTED → CONFIRMED 가 열려 있어
     * 결제도 안 한 예약 단계에서 구매확정 버튼이 보이게 된다.
     */
    public boolean isBuyerConfirmable() {
        return this == PAID || this == SHIPPING;
    }

    /** 진행 중 거래 상태 목록. 조회 조건에 쓴다. isActive() 와 같은 기준이다. */
    public static Set<TradeStatus> activeStatuses() {
        return EnumSet.of(REQUESTED, PAID, SHIPPING);
    }

    public boolean isFinished() {
        return nextStates().isEmpty();
    }
}
