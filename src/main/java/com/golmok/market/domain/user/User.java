package com.golmok.market.domain.user;

import com.golmok.market.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
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

    @Column(name = "manner_temp", nullable = false, precision = 3, scale = 1)
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

    /** 매너온도 조정. 0.0 ~ 99.9 범위를 벗어나지 않는다. */
    public void adjustMannerTemp(BigDecimal delta) {
        BigDecimal next = this.mannerTemp.add(delta);
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            next = BigDecimal.ZERO;
        } else if (next.compareTo(new BigDecimal("99.9")) > 0) {
            next = new BigDecimal("99.9");
        }
        this.mannerTemp = next;
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }
}
