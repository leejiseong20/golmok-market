package com.golmok.market.domain.admin.dto;

import com.golmok.market.domain.admin.AdminText;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRole;
import com.golmok.market.domain.user.UserStatus;

import java.time.LocalDateTime;

/**
 * 회원 목록 한 줄. 이메일은 가려서 준다({@link AdminText#maskEmail}).
 *
 * @param admin 관리자 계정인지. 관리자는 정지할 수 없어 화면이 버튼을 미리 숨긴다.
 */
public record AdminUserSummary(Long id, String email, String nickname, boolean admin, UserStatus status,
                               LocalDateTime createdAt, LocalDateTime lastLoginAt) {

    public static AdminUserSummary from(User user) {
        return new AdminUserSummary(user.getId(), AdminText.maskEmail(user.getEmail()), user.getNickname(),
                user.getRole() == UserRole.ADMIN, user.getStatus(), user.getCreatedAt(), user.getLastLoginAt());
    }
}
