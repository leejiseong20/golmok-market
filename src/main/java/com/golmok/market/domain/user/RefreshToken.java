package com.golmok.market.domain.user;

import com.golmok.market.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 500, unique = true)
    private String token;

    @Column(name = "device_info", length = 200)
    private String deviceInfo;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    private RefreshToken(User user, String token, String deviceInfo, LocalDateTime expiresAt) {
        this.user = user;
        this.token = token;
        this.deviceInfo = deviceInfo;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(User user, String token, String deviceInfo, LocalDateTime expiresAt) {
        return new RefreshToken(user, token, deviceInfo, expiresAt);
    }

    public boolean isExpired() {
        return this.expiresAt.isBefore(LocalDateTime.now());
    }

    public void rotate(String newToken, LocalDateTime newExpiresAt) {
        this.token = newToken;
        this.expiresAt = newExpiresAt;
    }
}
