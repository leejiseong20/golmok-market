package com.golmok.market.domain.report;

/**
 * 신고 대상 종류.
 *
 * reports 테이블은 이 값과 target_id 로 대상을 가리킨다. 한 컬럼이 users 와 products 를 동시에
 * 가리킬 수 없어 FK 는 포기했고, 대상이 실제로 있는지는 {@link ReportService} 가 확인한다.
 * 대상을 나눠 테이블을 만들면 FK 는 지키지만 대상이 늘 때마다 테이블·엔티티·리포지터리가 복제된다.
 */
public enum ReportTarget {
    USER,
    PRODUCT
}
