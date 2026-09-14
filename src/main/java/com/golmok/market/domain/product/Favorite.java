package com.golmok.market.domain.product;

import com.golmok.market.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 찜하기. (user_id, product_id) 에 UNIQUE 가 걸려 있어
 * 동시 요청으로 중복 찜이 생기는 것을 DB가 막아준다.
 */
@Entity
@Getter
@Table(name = "favorites")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    private Favorite(User user, Product product) {
        this.user = user;
        this.product = product;
    }

    public static Favorite of(User user, Product product) {
        return new Favorite(user, product);
    }
}
