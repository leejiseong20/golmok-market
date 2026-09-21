package com.golmok.market.domain.push;

import com.golmok.market.domain.user.User;
import com.golmok.market.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 웹 푸시 구독. 브라우저(기기) 하나에 하나다 — 한 사람이 폰과 PC 에서 각각 받으면 두 행이다.
 *
 * endpoint 는 푸시 서비스가 기기마다 발급한 주소라 기기를 가리킨다. 그래서 UNIQUE 이고,
 * 같은 브라우저에서 다른 계정으로 로그인해 다시 구독하면 새 행을 만들지 않고 주인을 바꾼다
 * (그러지 않으면 이전 사람의 알림이 계속 이 기기로 온다).
 */
@Entity
@Getter
@Table(name = "push_subscriptions", uniqueConstraints =
        @UniqueConstraint(name = "uk_push_endpoint", columnNames = "endpoint"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 500)
    private String endpoint;

    /** 브라우저의 P-256 공개키(base64url). 본문을 이 기기만 풀 수 있게 암호화하는 데 쓴다. */
    @Column(nullable = false, length = 100)
    private String p256dh;

    /** 브라우저가 만든 인증 비밀(base64url). 암호화 키 유도에 섞인다. */
    @Column(nullable = false, length = 50)
    private String auth;

    private PushSubscription(User user, String endpoint, String p256dh, String auth) {
        this.user = user;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public static PushSubscription of(User user, String endpoint, String p256dh, String auth) {
        return new PushSubscription(user, endpoint, p256dh, auth);
    }

    /** 같은 기기에서 다시 구독했다. 계정이 바뀌었을 수 있고, 브라우저가 키를 새로 만들었을 수도 있다. */
    public void renew(User user, String p256dh, String auth) {
        this.user = user;
        this.p256dh = p256dh;
        this.auth = auth;
    }
}
