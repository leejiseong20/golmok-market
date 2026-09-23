package com.golmok.market.domain.admin.dto;

import com.golmok.market.domain.report.Report;
import com.golmok.market.domain.report.ReportReason;
import com.golmok.market.domain.report.ReportStatus;
import com.golmok.market.domain.report.ReportTarget;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 신고 상세.
 *
 * 같은 대상의 다른 신고(sameTarget)와 지금까지의 조치(actions)를 함께 준다.
 * 한 건만 보고 판단하면 "여러 사람이 같은 문제를 신고했는지"를 놓친다.
 */
public record AdminReportDetailResponse(Long id, ReportTarget targetType, Long targetId, String targetName,
                                        ReportReason reason, String detail, ReportStatus status,
                                        String reporterNickname, LocalDateTime createdAt,
                                        String handledByNickname, LocalDateTime handledAt, String adminMemo,
                                        List<AdminReportSummary> sameTarget, List<AdminActionResponse> actions) {

    public static AdminReportDetailResponse of(Report report, String targetName,
                                               List<Report> sameTarget, List<AdminActionResponse> actions) {
        List<AdminReportSummary> others = sameTarget.stream()
                .filter(other -> !other.getId().equals(report.getId()))
                .map(other -> AdminReportSummary.of(other, targetName, sameTarget.size()))
                .toList();
        return new AdminReportDetailResponse(report.getId(), report.getTargetType(), report.getTargetId(), targetName,
                report.getReason(), report.getDetail(), report.getStatus(),
                report.getReporter().getNickname(), report.getCreatedAt(),
                report.getHandledBy() == null ? null : report.getHandledBy().getNickname(),
                report.getHandledAt(), report.getAdminMemo(), others, actions);
    }
}
