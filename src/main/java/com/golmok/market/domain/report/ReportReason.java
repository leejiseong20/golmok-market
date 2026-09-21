package com.golmok.market.domain.report;

/** 신고 사유. 화면에 보일 문구는 프론트가 정한다(상품 상태 라벨과 같은 방식). */
public enum ReportReason {
    /** 광고·도배 */
    SPAM,
    /** 사기 의심 */
    FRAUD,
    /** 거래 금지 물품 */
    PROHIBITED,
    /** 욕설·비방 */
    ABUSE,
    /** 기타. 이 사유를 고르면 내용을 반드시 적어야 한다. */
    OTHER
}
