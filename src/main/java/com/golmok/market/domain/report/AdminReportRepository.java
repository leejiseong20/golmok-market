package com.golmok.market.domain.report;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

/**
 * 관리자 화면이 쓰는 신고 조회. 일반 신고 접수(ReportRepository)와 목적이 달라 따로 둔다.
 */
public interface AdminReportRepository extends JpaRepository<Report, Long> {

    /** 최신순 한 페이지. 상태·대상 종류로 거를 수 있다(null 이면 전체). id 하나로 정렬해 커서가 단순하다. */
    @Query("""
            select r from Report r join fetch r.reporter
            where (:status is null or r.status = :status)
              and (:targetType is null or r.targetType = :targetType)
              and (:cursorId is null or r.id < :cursorId)
            order by r.id desc
            """)
    List<Report> findPage(ReportStatus status, ReportTarget targetType, Long cursorId, Pageable pageable);

    /** 같은 대상에 쌓인 신고 수. 목록에서 "이 상품은 5건" 처럼 보여 준다. */
    @Query("""
            select r.targetType as targetType, r.targetId as targetId, count(r) as total
            from Report r
            where r.targetType = :targetType and r.targetId in :targetIds
            group by r.targetType, r.targetId
            """)
    List<TargetCount> countByTargets(ReportTarget targetType, Collection<Long> targetIds);

    /** 같은 대상의 신고. 상세에서 함께 보여 주고, 처리할 때 한꺼번에 닫는다. */
    List<Report> findByTargetTypeAndTargetIdOrderByIdDesc(ReportTarget targetType, long targetId);

    interface TargetCount {
        ReportTarget getTargetType();
        Long getTargetId();
        long getTotal();
    }
}
