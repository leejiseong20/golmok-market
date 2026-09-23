package com.golmok.market.domain.admin;

import com.golmok.market.domain.admin.dto.AdminReportDetailResponse;
import com.golmok.market.domain.admin.dto.AdminReportSummary;
import com.golmok.market.domain.admin.dto.ReportHandleRequest;
import com.golmok.market.domain.report.ReportStatus;
import com.golmok.market.domain.report.ReportTarget;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신고 처리(관리자 전용).
 *
 * 권한 검사는 SecurityConfig 가 한다(`/api/admin/**` 는 ADMIN 만). 관리자가 아니면 **404** 로 답한다 —
 * 403 을 주면 이 주소에 관리자 화면이 있다는 사실이 드러난다.
 */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@Validated
public class AdminReportController {

    private final AdminReportService adminReportService;

    /** 신고 목록. 기본은 처리 전(PENDING)만 본다. */
    @GetMapping
    public CursorResponse<AdminReportSummary> list(@RequestParam(required = false, defaultValue = "PENDING") String status,
                                                   @RequestParam(required = false) ReportTarget targetType,
                                                   @RequestParam(required = false) String cursor,
                                                   @RequestParam(required = false) Integer size) {
        ReportStatus filter = "ALL".equalsIgnoreCase(status) ? null : ReportStatus.valueOf(status.toUpperCase());
        return adminReportService.list(filter, targetType, cursor, size);
    }

    @GetMapping("/{id}")
    public AdminReportDetailResponse detail(@PathVariable @Positive(message = "신고 번호가 올바르지 않습니다.") Long id) {
        return adminReportService.detail(id);
    }

    /** 신고를 인정하고(필요하면 조치까지) 닫는다. 같은 대상의 대기 신고도 함께 닫힌다. */
    @PostMapping("/{id}/resolve")
    public AdminReportDetailResponse resolve(@AuthenticationPrincipal AuthUser viewer,
                                             @PathVariable @Positive(message = "신고 번호가 올바르지 않습니다.") Long id,
                                             @Valid @RequestBody ReportHandleRequest request) {
        return adminReportService.resolve(viewer, id, request);
    }

    /** 문제가 아니라고 보고 이 신고만 닫는다. */
    @PostMapping("/{id}/reject")
    public AdminReportDetailResponse reject(@AuthenticationPrincipal AuthUser viewer,
                                            @PathVariable @Positive(message = "신고 번호가 올바르지 않습니다.") Long id,
                                            @Valid @RequestBody ReportHandleRequest request) {
        return adminReportService.reject(viewer, id, request);
    }
}
