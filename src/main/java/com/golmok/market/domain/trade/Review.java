package com.golmok.market.domain.trade;

import com.golmok.market.domain.user.User;
import com.golmok.market.global.entity.BaseCreatedTimeEntity;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 거래 후 매너평가. (trade_id, reviewer_id) UNIQUE 로 1인 1회만 허용.
 */
@Entity
@Getter
// 거래당 1인 1회. 매너온도가 여러 번 반영되는 것을 DB 가 막는다.
@Table(name = "reviews", uniqueConstraints =
        @UniqueConstraint(name = "uk_review", columnNames = {"trade_id", "reviewer_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseCreatedTimeEntity {

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

    private Review(Trade trade, User reviewer, User reviewee, int score, String content) {
        this.trade = trade;
        this.reviewer = reviewer;
        this.reviewee = reviewee;
        this.score = score;
        this.content = content;
    }

    public static Review write(Trade trade, User reviewer, int score, String content) {
        if (trade.getStatus() != TradeStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
        }
        if (!trade.isParticipant(reviewer.getId())) {
            throw new BusinessException(ErrorCode.TRADE_NOT_FOUND);
        }
        if (score < 1 || score > 5) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "평점은 1~5 사이여야 합니다.");
        }
        if (content != null && content.length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "후기는 500자 이하여야 합니다.");
        }
        User reviewee = trade.getSeller().getId().equals(reviewer.getId())
                ? trade.getBuyer()
                : trade.getSeller();

        return new Review(trade, reviewer, reviewee, score,
                content == null || content.isBlank() ? null : content.strip());
    }

    /** 3점을 기준으로 위아래. 5점이면 +0.4, 1점이면 -0.4 */
    public BigDecimal toMannerDelta() {
        return new BigDecimal("0.2").multiply(BigDecimal.valueOf(this.score - 3));
    }
}
