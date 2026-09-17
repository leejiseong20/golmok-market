package com.golmok.market.domain.trade;

import com.golmok.market.domain.notification.NotificationType;
import com.golmok.market.domain.notification.event.NotificationRequestedEvent;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.trade.dto.ReviewCreateRequest;
import com.golmok.market.domain.trade.dto.ReviewResponse;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.pagination.Cursor;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.pagination.PageSize;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final TradeRepository tradeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 후기와 온도는 함께 커밋한다. 상품 삭제나 채팅방 나가기는 완료된 거래의 평가 자격을 없애지 않는다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewResponse write(long tradeId, AuthUser viewer, ReviewCreateRequest request) {
        long productId = tradeRepository.findProductIdForParticipant(tradeId, viewer.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_NOT_FOUND));
        productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        Trade trade = tradeRepository.findByIdForUpdate(tradeId)
                .filter(found -> found.isParticipant(viewer.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_NOT_FOUND));
        if (trade.getStatus() != TradeStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
        }
        if (reviewRepository.findByTradeIdAndReviewerId(tradeId, viewer.id()).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_REVIEWED);
        }

        // 서로 다른 거래에서 상호 평가할 때 FK 공유 잠금의 승격이 교착되지 않도록 두 사용자를 같은 순서로 잠근다.
        long sellerId = trade.getSeller().getId();
        long buyerId = trade.getBuyer().getId();
        User first = lockUser(Math.min(sellerId, buyerId));
        User second = lockUser(Math.max(sellerId, buyerId));
        User reviewer = first.getId().equals(viewer.id()) ? first : second;
        Review review = reviewRepository.saveAndFlush(Review.write(trade, reviewer, request.score(), request.content()));
        ReviewResponse response = ReviewResponse.from(review);
        userRepository.adjustMannerTemp(review.getReviewee().getId(), review.toMannerDelta());
        eventPublisher.publishEvent(new NotificationRequestedEvent(review.getReviewee().getId(), NotificationType.TRADE,
                "새 후기를 받았어요", "%s님 · %d점".formatted(reviewer.getNickname(), review.getScore()),
                NotificationRequestedEvent.MY_REVIEWS_URL));
        return response;
    }

    public boolean canReview(Trade trade, long viewerId) {
        return trade != null && trade.getStatus() == TradeStatus.CONFIRMED && trade.isParticipant(viewerId)
                && !reviewRepository.existsByTradeIdAndReviewerId(trade.getId(), viewerId);
    }

    /** 구매내역 한 페이지의 작성 여부를 한 번에 읽는다. 카드마다 조회하면 N+1 이 된다. */
    public Set<Long> reviewedTradeIds(long viewerId, Collection<Long> tradeIds) {
        return tradeIds.isEmpty() ? Set.of() : reviewRepository.findReviewedTradeIds(viewerId, tradeIds);
    }

    public CursorResponse<ReviewResponse> findReceived(long userId, String rawCursor, Integer size) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        Cursor cursor = Cursor.parse(rawCursor);
        PageSize pageSize = PageSize.of(size);
        var limit = PageRequest.of(0, pageSize.fetchSize());
        List<Review> fetched = cursor == null ? reviewRepository.findReceived(userId, limit)
                : reviewRepository.findReceivedAfter(userId, cursor.valueAsDateTime(), cursor.id(), limit);
        return CursorResponse.of(fetched, pageSize, review -> Cursor.of(review.getCreatedAt(), review.getId()))
                .map(ReviewResponse::from);
    }

    private User lockUser(long id) {
        return userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
