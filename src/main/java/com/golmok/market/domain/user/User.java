package com.golmok.market.domain.user;

import com.golmok.market.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    public static final String WITHDRAWN_NICKNAME_PREFIX = "탈퇴한사용자";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 30, unique = true)
    private String nickname;

    @Column(length = 20)
    private String phone;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "manner_temp", nullable = false, precision = 3, scale = 1, updatable = false)
    private BigDecimal mannerTemp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserStatus status;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private User(String email, String password, String nickname, String phone) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.phone = phone;
        this.mannerTemp = new BigDecimal("36.5");
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
    }

    public void updateProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    /**
     * 탈퇴. 행은 남기고(거래·후기·채팅 기록이 참조한다) 개인정보를 지운다.
     *
     * 이메일·닉네임은 UNIQUE 라 그대로 두면 같은 이메일로 영영 다시 가입할 수 없다. 사람이 쓸 수 없는 값으로 바꿔
     * 원래 값을 풀어 주고, 남은 기록(후기 작성자 등)에 개인정보가 보이지 않게 한다. .invalid 는 실제로 존재할 수 없는 도메인이다.
     */
    public void withdraw(LocalDateTime now) {
        this.status = UserStatus.WITHDRAWN;
        this.deletedAt = now;
        this.email = "withdrawn-" + this.id + "@deleted.invalid";
        this.nickname = WITHDRAWN_NICKNAME_PREFIX + this.id;
        this.profileImageUrl = null;
        this.phone = null;
    }

    public boolean isWithdrawn() {
        return this.status == UserStatus.WITHDRAWN;
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    // ---------- 관리자 조치 ----------
    // 정지는 탈퇴와 다르다. 개인정보를 지우지 않고 상태만 바꾼다(해제하면 그대로 돌아온다).
    // 로그인·재발급은 requireActive 가 막는다. 이미 발급된 access token 은 만료(최대 30분)까지 살아 있다.

    public void suspend() {
        if (isWithdrawn()) {
            return;   // 이미 탈퇴한 회원은 상태를 되돌리지 않는다
        }
        this.status = UserStatus.SUSPENDED;
    }

    public void unsuspend() {
        if (this.status == UserStatus.SUSPENDED) {
            this.status = UserStatus.ACTIVE;
        }
    }

    public boolean isSuspended() {
        return this.status == UserStatus.SUSPENDED;
    }
}
