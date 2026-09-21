package com.golmok.market.domain.block;

import com.golmok.market.domain.user.User;
import com.golmok.market.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 차단.
 *
 * 한 방향으로만 기록한다. 내가 차단해도 상대 쪽 행은 생기지 않는다.
 * 대신 "차단 관계인가"를 볼 때 양쪽을 모두 본다({@link BlockRepository#existsBetween}).
 * 양쪽에 행을 만들면 한쪽이 해제할 때 다른 쪽 행을 어떻게 할지가 애매해지고,
 * "누가 먼저 차단했는가"라는 사실도 잃는다.
 *
 * 차단해도 상대에게 알리지 않는다. 알리면 보복으로 이어진다.
 */
@Entity
@Getter
@Table(name = "blocks", uniqueConstraints =
        @UniqueConstraint(name = "uk_block", columnNames = {"blocker_id", "blocked_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 차단한 사람 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id")
    private User blocker;

    /** 차단당한 사람 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id")
    private User blocked;

    private Block(User blocker, User blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
    }

    public static Block of(User blocker, User blocked) {
        return new Block(blocker, blocked);
    }
}
