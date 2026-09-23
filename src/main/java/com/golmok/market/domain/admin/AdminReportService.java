package com.golmok.market.domain.admin;

import com.golmok.market.domain.admin.dto.AdminActionResponse;
import com.golmok.market.domain.admin.dto.AdminReportDetailResponse;
import com.golmok.market.domain.admin.dto.AdminReportSummary;
import com.golmok.market.domain.admin.dto.ReportHandleRequest;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.report.AdminReportRepository;
import com.golmok.market.domain.report.Report;
import com.golmok.market.domain.report.ReportStatus;
import com.golmok.market.domain.report.ReportTarget;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.domain.user.UserRole;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.pagination.Cursor;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.pagination.PageSize;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 신고 처리(관리자).
 *
 * 조치는 **사람이 판단한 뒤에만** 한다. 신고 수로 자동으로 상품을 내리면 몰아서 신고하는 것으로 멀쩡한 상품이 내려간다.
 *
 * 한 대상에 신고가 여러 건이면 **같은 대상의 대기 신고를 한 번에 닫는다.** 그러지 않으면 같은 상품을 신고 수만큼
 * 반복해서 내리게 되고, 목록에서도 처리한 대상이 계속 남는다.
 *
 * 조치와 감사 로그는 같은 트랜잭션이다. 기록 없이 조치만 남는 상황을 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportService {

    private final AdminReportRepository reportRepository;
    private final AdminActionRepository adminActionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public CursorResponse<AdminReportSummary> list(ReportStatus status, ReportTarget targetType,
                                                   String rawCursor, Integer rawSize) {
        PageSize size = PageSize.of(rawSize);
        Long cursorId = rawCursor == null || rawCursor.isBlank() ? null : Cursor.parse(rawCursor).id();
        List<Report> reports = reportRepository.findPage(status, targetType, cursorId,
                PageRequest.of(0, size.value() + 1));

        List<AdminReportSummary> summaries = reports.stream()
                .map(report -> AdminReportSummary.of(report, targetName(report), 0))
                .toList();
        // 같은 대상에 몇 건이 쌓였는지 함께 보여 준다. 신고마다 세면 건수만큼 쿼리가 나가므로 종류별로 한 번씩만 센다.
        Map<String, Long> counts = countsFor(reports);
        summaries = summaries.stream()
                .map(summary -> summary.withReportCount(counts.getOrDefault(key(summary.targetType(), summary.targetId()), 1L)))
                .toList();
        return CursorResponse.of(summaries, size, summary -> new Cursor(String.valueOf(summary.id()), summary.id()));
    }

    public AdminReportDetailResponse detail(long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        List<Report> sameTarget = reportRepository.findByTargetTypeAndTargetIdOrderByIdDesc(
                report.getTargetType(), report.getTargetId());
        List<AdminActionResponse> actions = adminActionRepository
                .findByTargetTypeAndTargetIdOrderByIdDesc(targetOf(report.getTargetType()), report.getTargetId())
                .stream().map(AdminActionResponse::from).toList();
        return AdminReportDetailResponse.of(report, targetName(report), sameTarget, actions);
    }

    /**
     * 신고를 인정하고 필요하면 조치한다.
     *
     * 잠금 순서는 기존 규칙과 같다(상품 → 회원). 잠근 뒤 상태를 다시 확인해, 그 사이 이미 삭제·정지됐으면 조치를 건너뛰고
     * 신고만 닫는다. 대상이 이미 사라졌다고 신고 처리 자체를 실패로 만들면 목록에서 영영 지울 수 없다.
     */
    @Transactional
    public AdminReportDetailResponse resolve(AuthUser viewer, long reportId, ReportHandleRequest request) {
        Report report = pendingReport(reportId);
        User admin = userRepository.getReferenceById(viewer.id());
        AdminActionType action = null;

        if (request.action() == ReportHandleRequest.Action.DELETE_PRODUCT) {
            if (report.getTargetType() != ReportTarget.PRODUCT) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "상품 신고에만 쓸 수 있는 조치입니다.");
            }
            Product product = productRepository.findByIdForUpdate(report.getTargetId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
            if (!product.isDeleted()) {
                product.softDelete();
                action = AdminActionType.DELETE_PRODUCT;
            }
        } else if (request.action() == ReportHandleRequest.Action.SUSPEND_USER) {
            long targetUserId = report.getTargetType() == ReportTarget.USER
                    ? report.getTargetId()
                    : sellerOf(report.getTargetId());
            User target = userRepository.findByIdForUpdate(targetUserId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            if (target.getRole() == UserRole.ADMIN) {
                throw new BusinessException(ErrorCode.CANNOT_SUSPEND_ADMIN);
            }
            if (!target.isSuspended()) {
                target.suspend();
                action = AdminActionType.SUSPEND_USER;
            }
        }

        if (action != null) {
            record(admin, action, targetOf(report.getTargetType()), report.getTargetId(), reportId, request.reason());
        }
        closeSameTarget(report, ReportStatus.RESOLVED, admin, request.reason());
        record(admin, AdminActionType.RESOLVE_REPORT, AdminActionTarget.REPORT, reportId, reportId, request.reason());
        return detail(reportId);
    }

    /** 신고 내용이 문제가 아니라고 보고 닫는다. 대상에는 아무 일도 하지 않는다. */
    @Transactional
    public AdminReportDetailResponse reject(AuthUser viewer, long reportId, ReportHandleRequest request) {
        Report report = pendingReport(reportId);
        User admin = userRepository.getReferenceById(viewer.id());
        // 반려는 이 신고 한 건만 닫는다. 같은 대상의 다른 신고는 사유가 다를 수 있어 따로 판단해야 한다.
        report.handle(ReportStatus.REJECTED, admin, request.reason(), LocalDateTime.now(clock));
        record(admin, AdminActionType.REJECT_REPORT, AdminActionTarget.REPORT, reportId, reportId, request.reason());
        return detail(reportId);
    }

    private Report pendingReport(long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        if (!report.isPending()) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_HANDLED);
        }
        return report;
    }

    /** 같은 대상의 대기 신고를 모두 닫는다. 같은 상품을 신고 수만큼 반복해서 내리지 않기 위해서다. */
    private void closeSameTarget(Report report, ReportStatus status, User admin, String memo) {
        LocalDateTime now = LocalDateTime.now(clock);
        reportRepository.findByTargetTypeAndTargetIdOrderByIdDesc(report.getTargetType(), report.getTargetId())
                .forEach(same -> same.handle(status, admin, memo, now));
    }

    private void record(User admin, AdminActionType action, AdminActionTarget targetType, long targetId,
                        Long reportId, String reason) {
        adminActionRepository.save(AdminAction.of(admin, action, targetType, targetId, reportId, reason));
    }

    private long sellerOf(long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND))
                .getSeller().getId();
    }

    private static AdminActionTarget targetOf(ReportTarget target) {
        return target == ReportTarget.PRODUCT ? AdminActionTarget.PRODUCT : AdminActionTarget.USER;
    }

    /** 목록·상세에 보여 줄 대상 이름. 이미 지워졌으면 그렇게 알린다(관리자는 사라진 대상도 봐야 한다). */
    private String targetName(Report report) {
        if (report.getTargetType() == ReportTarget.PRODUCT) {
            return productRepository.findById(report.getTargetId())
                    .map(product -> product.isDeleted() ? product.getTitle() + " (삭제됨)" : product.getTitle())
                    .orElse("(없는 상품)");
        }
        return userRepository.findById(report.getTargetId())
                .map(user -> user.isSuspended() ? user.getNickname() + " (정지됨)" : user.getNickname())
                .orElse("(없는 사용자)");
    }

    private Map<String, Long> countsFor(List<Report> reports) {
        Map<String, Long> counts = new HashMap<>();
        for (ReportTarget type : ReportTarget.values()) {
            List<Long> ids = reports.stream()
                    .filter(report -> report.getTargetType() == type)
                    .map(Report::getTargetId).distinct().toList();
            if (ids.isEmpty()) {
                continue;
            }
            reportRepository.countByTargets(type, ids)
                    .forEach(row -> counts.put(key(row.getTargetType(), row.getTargetId()), row.getTotal()));
        }
        return counts;
    }

    private static String key(ReportTarget type, long targetId) {
        return type + ":" + targetId;
    }
}
