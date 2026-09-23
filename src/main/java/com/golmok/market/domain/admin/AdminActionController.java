package com.golmok.market.domain.admin;

import com.golmok.market.domain.admin.dto.AdminActionLogResponse;
import com.golmok.market.global.pagination.CursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조치 기록(관리자 전용, 읽기만). 권한 검사와 404 규칙은 AdminReportController 와 같다.
 */
@RestController
@RequestMapping("/api/admin/actions")
@RequiredArgsConstructor
public class AdminActionController {

    private final AdminActionService adminActionService;

    /** 최신순. action·targetType 을 비우면 전체. */
    @GetMapping
    public CursorResponse<AdminActionLogResponse> list(@RequestParam(required = false) AdminActionType action,
                                                       @RequestParam(required = false) AdminActionTarget targetType,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(required = false) Integer size) {
        return adminActionService.list(action, targetType, cursor, size);
    }
}
