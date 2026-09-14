package com.golmok.market.domain.trade;

public enum PaymentStatus {
    READY,     // 결제창 띄우기 전, 주문번호만 발급된 상태
    APPROVED,  // 승인 완료
    FAILED,    // 실패
    CANCELED   // 승인 후 취소
}
