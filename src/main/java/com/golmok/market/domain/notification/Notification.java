package com.golmok.market.domain.notification;

import com.golmok.market.domain.user.User;
import com.golmok.market.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림함의 알림 한 건. 원래 동작(찜·거래·후기)이 커밋된 뒤 별도 트랜잭션에서 만든다.
 *
 * 무엇에 대한 알림인지는 target_url 경로(/products/12, /chat-rooms/7)로만 남긴다.
 * 스키마에 대상 id 컬럼이 없고, 프론트는 이 경로를 해석해 화면을 연다.
 */
@Entity
@Getter
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseCreatedTimeEntity {

    private static final int TITLE_LENGTH = 100;
    private static final int CONTENT_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private NotificationType type;

    @Column(nullable = false, length = TITLE_LENGTH)
    private String title;

    @Column(length = CONTENT_LENGTH)
    private String content;

    /** 알림 클릭 시 프론트에서 이동할 경로. 예: /products/12 */
    @Column(name = "target_url", length = 255)
    private String targetUrl;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    private Notification(User user, NotificationType type, String title, String content, String targetUrl) {
        this.user = user;
        this.type = type;
        this.title = truncate(title, TITLE_LENGTH);
        this.content = content == null ? null : truncate(content, CONTENT_LENGTH);
        this.targetUrl = targetUrl;
    }

    public static Notification of(User user, NotificationType type,
                                  String title, String content, String targetUrl) {
        return new Notification(user, type, title, content, targetUrl);
    }

    public void markAsRead() {
        this.read = true;
    }

    /** 상품 제목·닉네임이 섞여 길이를 넘을 수 있다. 저장 실패 대신 자르고, 이모지 한가운데서는 자르지 않는다. */
    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
        return value.substring(0, end);
    }
}
