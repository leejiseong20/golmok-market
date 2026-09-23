package com.golmok.market.domain.admin;

import com.golmok.market.domain.admin.dto.AdminActionResponse;
import com.golmok.market.domain.admin.dto.AdminUserDetail;
import com.golmok.market.domain.admin.dto.AdminUserSummary;
import com.golmok.market.domain.product.AdminProductRepository;
import com.golmok.market.domain.report.AdminReportRepository;
import com.golmok.market.domain.report.ReportTarget;
import com.golmok.market.domain.user.AdminUserRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserAccessChangedEvent;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.domain.user.UserRole;
import com.golmok.market.domain.user.UserStatus;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.pagination.Cursor;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.pagination.PageSize;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 회원 관리(관리자).
 *
 * 신고 없이도 정지·정지 해제를 할 수 있다. 잘못 정지했을 때 되돌릴 길이 있어야 하고,
 * 신고가 들어오기 전에 드러난 문제(운영자가 직접 본 사기 등)도 막을 수 있어야 해서다.
 * 직접 조치해도 그 회원에 대한 대기 신고는 닫지 않는다 — 신고 판단은 신고함에서 따로 한다.
 *
 * 조치와 감사 로그는 같은 트랜잭션이다(AdminReportService 와 같은 규칙).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final UserRepository userRepository;
    private final AdminProductRepository adminProductRepository;
    private final AdminReportRepository adminReportRepository;
    private final AdminActionRepository adminActionRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 검색어에 @ 가 있으면 이메일 전체 일치, 없으면 닉네임 부분 일치로 찾는다. */
    public CursorResponse<AdminUserSummary> list(String query, UserStatus status, String rawCursor, Integer rawSize) {
        PageSize size = PageSize.of(rawSize);
        Long cursorId = rawCursor == null || rawCursor.isBlank() ? null : Cursor.parse(rawCursor).id();
        String keyword = query == null ? "" : query.strip();
        String email = keyword.contains("@") ? keyword : null;
        String nickname = keyword.contains("@") ? null : AdminText.likeKeyword(keyword);

        List<AdminUserSummary> users = adminUserRepository
                .findPage(status, email, nickname, cursorId, PageRequest.of(0, size.fetchSize()))
                .stream().map(AdminUserSummary::from).toList();
        return CursorResponse.of(users, size, user -> new Cursor(String.valueOf(user.id()), user.id()));
    }

    public AdminUserDetail detail(long userId) {
        User user = adminUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<AdminActionResponse> actions = adminActionRepository.findHistory(AdminActionTarget.USER, userId)
                .stream().map(AdminActionResponse::from).toList();
        return new AdminUserDetail(AdminUserSummary.from(user), user.getProfileImageUrl(), user.getMannerTemp(),
                adminProductRepository.countVisibleBySeller(userId),
                adminReportRepository.countByTargetTypeAndTargetId(ReportTarget.USER, userId),
                adminReportRepository.countOnProductsOf(ReportTarget.PRODUCT, userId),
                actions);
    }

    /**
     * 정지. 로그인·토큰 재발급과, 이미 받은 access token 으로 하는 요청까지 곧바로 막힌다(UserAccessCache).
     * 잠근 뒤 상태를 확인해, 두 관리자가 동시에 눌러도 기록이 한 번만 남는다.
     */
    @Transactional
    public AdminUserDetail suspend(AuthUser viewer, long userId, String reason) {
        User target = lockedUser(userId);
        if (target.getRole() == UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_SUSPEND_ADMIN);
        }
        if (target.isWithdrawn()) {
            throw new BusinessException(ErrorCode.ADMIN_ACTION_NOT_ALLOWED, "탈퇴한 회원은 정지할 수 없습니다.");
        }
        if (target.isSuspended()) {
            throw new BusinessException(ErrorCode.ADMIN_ACTION_NOT_ALLOWED, "이미 정지된 회원입니다.");
        }
        target.suspend();
        // 이미 받은 access token 으로도 곧바로 막히게 인증 캐시를 비운다(UserAccessCache).
        eventPublisher.publishEvent(new UserAccessChangedEvent(userId));
        record(viewer, AdminActionType.SUSPEND_USER, userId, reason);
        return detail(userId);
    }

    @Transactional
    public AdminUserDetail unsuspend(AuthUser viewer, long userId, String reason) {
        User target = lockedUser(userId);
        if (!target.isSuspended()) {
            throw new BusinessException(ErrorCode.ADMIN_ACTION_NOT_ALLOWED, "정지된 회원이 아닙니다.");
        }
        target.unsuspend();
        eventPublisher.publishEvent(new UserAccessChangedEvent(userId));
        record(viewer, AdminActionType.UNSUSPEND_USER, userId, reason);
        return detail(userId);
    }

    private User lockedUser(long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void record(AuthUser viewer, AdminActionType action, long userId, String reason) {
        User admin = userRepository.getReferenceById(viewer.id());
        adminActionRepository.save(AdminAction.of(admin, action, AdminActionTarget.USER, userId, null, reason));
    }
}
