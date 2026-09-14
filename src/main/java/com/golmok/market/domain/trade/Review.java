package com.golmok.market.domain.trade;

import com.golmok.market.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 거래 후 매너평가. (trade_id, reviewer_id) UNIQUE 로 1인 1회만 허용.
 */
@Entity
@Getter
@Table(name = "reviews")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trade_id")
    private Trade trade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewee_id")
    private User reviewee;

    /** DB 는 TINYINT. columnDefinition 이 없으면 validate 시 INTEGER 로 기대해 실패한다. */
    @Column(nullable = false, columnDefinition = "TINYINT")
    private int score;

    @Column(length = 500)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    private Review(Trade trade, User reviewer, User reviewee, int score, String content) {
        this.trade = trade;
        this.reviewer = reviewer;
        this.reviewee = reviewee;
        this.score = score;
        this.content = content;
    }

    public static Review write(Trade trade, User reviewer, int score, String content) {
        if (trade.getStatus() != TradeStatus.CONFIRMED) {
            throw new IllegalStateException("거래가 확정된 뒤에만 평가할 수 있습니다.");
        }
        if (!trade.isParticipant(reviewer.getId())) {
            throw new IllegalArgumentException("거래 당사자만 평가할 수 있습니다.");
        }
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("평점은 1~5 사이여야 합니다.");
        }
        User reviewee = trade.getSeller().getId().equals(reviewer.getId())
                ? trade.getBuyer()
                : trade.getSeller();

        Review review = new Review(trade, reviewer, reviewee, score, content);
        reviewee.adjustMannerTemp(review.toMannerDelta());
        return review;
    }

    /** 3점을 기준으로 위아래. 5점이면 +0.4, 1점이면 -0.4 */
    private BigDecimal toMannerDelta() {
        return BigDecimal.valueOf((this.score - 3) * 0.2);
    }
}
