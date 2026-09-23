package com.golmok.market.domain.admin;

import com.golmok.market.domain.admin.dto.AdminReasonRequest;
import com.golmok.market.domain.admin.dto.AdminUserDetail;
import com.golmok.market.domain.admin.dto.AdminUserSummary;
import com.golmok.market.domain.user.UserStatus;
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
 * 회원 관리(관리자 전용). 권한 검사와 404 규칙은 AdminReportController 와 같다(SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Validated
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 회원 목록. q 에 @ 가 있으면 이메일 전체 일치, 없으면 닉네임 부분 일치. status 를 비우면 전체. */
    @GetMapping
    public CursorResponse<AdminUserSummary> list(@RequestParam(required = false) String q,
                                                 @RequestParam(required = false) UserStatus status,
                                                 @RequestParam(required = false) String cursor,
                                                 @RequestParam(required = false) Integer size) {
        return adminUserService.list(q, status, cursor, size);
    }

    @GetMapping("/{id}")
    public AdminUserDetail detail(@PathVariable @Positive(message = "회원 번호가 올바르지 않습니다.") Long id) {
        return adminUserService.detail(id);
    }

    @PostMapping("/{id}/suspend")
    public AdminUserDetail suspend(@AuthenticationPrincipal AuthUser viewer,
                                   @PathVariable @Positive(message = "회원 번호가 올바르지 않습니다.") Long id,
                                   @Valid @RequestBody AdminReasonRequest request) {
        return adminUserService.suspend(viewer, id, request.reason());
    }

    @PostMapping("/{id}/unsuspend")
    public AdminUserDetail unsuspend(@AuthenticationPrincipal AuthUser viewer,
                                     @PathVariable @Positive(message = "회원 번호가 올바르지 않습니다.") Long id,
                                     @Valid @RequestBody AdminReasonRequest request) {
        return adminUserService.unsuspend(viewer, id, request.reason());
    }
}
