package com.golmok.market.domain.admin;

import com.golmok.market.domain.admin.dto.AdminSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 현황판(관리자 전용). 권한 검사와 404 규칙은 AdminReportController 와 같다.
 */
@RestController
@RequestMapping("/api/admin/summary")
@RequiredArgsConstructor
public class AdminSummaryController {

    private final AdminSummaryService adminSummaryService;

    @GetMapping
    public AdminSummaryResponse summary() {
        return adminSummaryService.summary();
    }
}
