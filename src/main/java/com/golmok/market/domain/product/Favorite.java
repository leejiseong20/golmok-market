package com.golmok.market.domain.product;

import com.golmok.market.global.entity.BaseCreatedTimeEntity;
import com.golmok.market.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 찜하기. (user_id, product_id) 에 UNIQUE 가 걸려 있어
 * 동시 요청으로 중복 찜이 생기는 것을 DB가 막아준다.
 */
@Entity
@Getter
// UNIQUE 를 엔티티에도 선언한다. 선언하지 않으면 스키마를 엔티티로 만드는 H2 테스트에만 제약이 빠져
// 중복 찜이 테스트에서는 통과하고 운영 MySQL 에서만 터진다. (컬럼 정의는 그대로라 SQL 스키마는 변경 없음)
@Table(name = "favorites", uniqueConstraints =
        @UniqueConstraint(name = "uk_favorite", columnNames = {"user_id", "product_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    private Favorite(User user, Product product) {
        this.user = user;
        this.product = product;
    }

    public static Favorite of(User user, Product product) {
        return new Favorite(user, product);
    }
}
