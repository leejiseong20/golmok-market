package com.golmok.market.domain.admin;

/** 관리자가 한 조치. 감사 로그에 남는다. */
public enum AdminActionType {

    SUSPEND_USER,
    UNSUSPEND_USER,
    DELETE_PRODUCT,
    /** 관리자가 내린 상품을 되살렸다. 판매자가 직접 지운 상품에는 쓸 수 없다. */
    RESTORE_PRODUCT,
    /** 신고를 인정하고 닫았다(조치는 따로 남는다). */
    RESOLVE_REPORT,
    /** 신고 내용이 문제가 아니라고 보고 닫았다. */
    REJECT_REPORT
}
