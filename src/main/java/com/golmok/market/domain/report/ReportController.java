package com.golmok.market.domain.report;

import com.golmok.market.domain.report.dto.ReportCreateRequest;
import com.golmok.market.domain.report.dto.ReportResponse;
import com.golmok.market.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신고. 로그인한 사용자만 접수할 수 있다(SecurityConfig 의 기본 차단 규칙).
 * 조회 API 는 두지 않는다. 내가 낸 신고 목록을 보여 줄 화면이 없고, 남의 신고는 볼 수 없어야 한다.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse report(@Valid @RequestBody ReportCreateRequest request,
                                 @AuthenticationPrincipal AuthUser viewer) {
        return reportService.report(request, viewer);
    }
}
