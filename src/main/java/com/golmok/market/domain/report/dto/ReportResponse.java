package com.golmok.market.domain.report.dto;

import com.golmok.market.domain.report.Report;
import com.golmok.market.domain.report.ReportReason;
import com.golmok.market.domain.report.ReportTarget;

import java.time.LocalDateTime;

/** 접수 확인용. 신고자 본인만 받는 응답이라 대상의 정보는 담지 않는다. */
public record ReportResponse(
        Long id,
        ReportTarget targetType,
        Long targetId,
        ReportReason reason,
        LocalDateTime createdAt
) {
    public static ReportResponse from(Report report) {
        return new ReportResponse(report.getId(), report.getTargetType(), report.getTargetId(),
                report.getReason(), report.getCreatedAt());
    }
}
