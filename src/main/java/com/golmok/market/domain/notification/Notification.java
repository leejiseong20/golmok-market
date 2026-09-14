package com.golmok.market.domain.notification;

import com.golmok.market.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 255)
    private String content;

    /** 알림 클릭 시 프론트에서 이동할 경로. 예: /products/12 */
    @Column(name = "target_url", length = 255)
    private String targetUrl;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    private Notification(User user, NotificationType type, String title, String content, String targetUrl) {
        this.user = user;
        this.type = type;
        this.title = title;
        this.content = content;
        this.targetUrl = targetUrl;
    }

    public static Notification of(User user, NotificationType type,
                                  String title, String content, String targetUrl) {
        return new Notification(user, type, title, content, targetUrl);
    }

    public void markAsRead() {
        this.read = true;
    }
}
