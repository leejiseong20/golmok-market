package com.golmok.market.domain.admin.dto;

import com.golmok.market.domain.report.Report;
import com.golmok.market.domain.report.ReportReason;
import com.golmok.market.domain.report.ReportStatus;
import com.golmok.market.domain.report.ReportTarget;

import java.time.LocalDateTime;

/**
 * 신고 목록 한 줄.
 *
 * @param reportCount 같은 대상에 쌓인 신고 수. 한 건짜리와 여러 건 몰린 것을 목록에서 바로 가르기 위해 함께 준다.
 */
public record AdminReportSummary(Long id, ReportTarget targetType, Long targetId, String targetName,
                                 ReportReason reason, String detail, ReportStatus status,
                                 long reportCount, String reporterNickname, LocalDateTime createdAt) {

    public static AdminReportSummary of(Report report, String targetName, long reportCount) {
        return new AdminReportSummary(report.getId(), report.getTargetType(), report.getTargetId(), targetName,
                report.getReason(), report.getDetail(), report.getStatus(), reportCount,
                report.getReporter().getNickname(), report.getCreatedAt());
    }

    public AdminReportSummary withReportCount(long count) {
        return new AdminReportSummary(id, targetType, targetId, targetName, reason, detail, status,
                count, reporterNickname, createdAt);
    }
}
