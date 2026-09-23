package com.golmok.market.domain.report;

/** 신고 처리 상태. 처리한 신고가 목록에 계속 남지 않도록 관리자 화면이 이 값으로 거른다. */
public enum ReportStatus {

    /** 아직 아무도 보지 않았다. */
    PENDING,
    /** 신고를 인정했다(조치했거나, 조치는 하지 않되 근거를 남겼다). */
    RESOLVED,
    /** 신고 내용이 문제가 아니라고 판단했다. */
    REJECTED
}
