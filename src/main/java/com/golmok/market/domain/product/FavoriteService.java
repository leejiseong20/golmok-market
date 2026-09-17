package com.golmok.market.domain.product;

import com.golmok.market.domain.notification.NotificationType;
import com.golmok.market.domain.notification.event.NotificationRequestedEvent;
import com.golmok.market.domain.product.dto.FavoriteResponse;
import com.golmok.market.domain.product.dto.ProductSummaryResponse;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.pagination.Cursor;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.pagination.PageSize;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 찜하기 · 찜 해제 · 내 찜 목록.
 *
 * favorites 테이블의 (user_id, product_id) UNIQUE 가 중복 찜의 최종 방어선이고,
 * products.favorite_count 는 목록에서 COUNT 쿼리를 피하려고 둔 비정규화 컬럼이라
 * 찜 행과 카운터를 항상 같은 트랜잭션에서 함께 바꾼다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductThumbnails productThumbnails;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public FavoriteResponse favorite(long productId, AuthUser viewer) {
        Product product = productRepository.findVisibleById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.isOwnedBy(viewer.id())) {
            throw new BusinessException(ErrorCode.CANNOT_FAVORITE_OWN_PRODUCT);
        }
        if (favoriteRepository.existsByUserIdAndProductId(viewer.id(), productId)) {
            throw new BusinessException(ErrorCode.ALREADY_FAVORITED);
        }
        try {
            // 위 확인과 INSERT 사이에 같은 요청이 한 번 더 들어올 수 있다(더블클릭).
            // 최종 방어선은 (user_id, product_id) UNIQUE 이고, flush 를 여기서 해야 예외를 잡을 수 있다.
            favoriteRepository.saveAndFlush(
                    Favorite.of(userRepository.getReferenceById(viewer.id()), product));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_FAVORITED);
        }
        // 누가 찜했는지는 알리지 않는다. 같은 상품의 안 읽은 찜 알림은 하나로 합쳐지므로 닉네임을 넣으면 틀린 정보가 된다.
        eventPublisher.publishEvent(new NotificationRequestedEvent(product.getSeller().getId(), NotificationType.FAVORITE,
                "누군가 내 상품을 찜했어요", product.getTitle(), NotificationRequestedEvent.productUrl(productId)));
        productRepository.incrementFavoriteCount(productId);
        return new FavoriteResponse(true, productRepository.findFavoriteCount(productId));
    }

    /**
     * 찜하지 않은 상품을 취소해도 에러 없이 현재 상태를 돌려준다(멱등).
     * 화면의 하트 상태가 서버와 어긋났을 때 사용자가 에러를 보는 것보다,
     * 한 번 더 눌러서 맞춰지는 편이 낫다.
     */
    @Transactional
    public FavoriteResponse unfavorite(long productId, AuthUser viewer) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        favoriteRepository.findByUserIdAndProductId(viewer.id(), productId)
                .ifPresent(favorite -> {
                    favoriteRepository.delete(favorite);
                    favoriteRepository.flush();
                    productRepository.decrementFavoriteCount(productId);
                });
        return new FavoriteResponse(false, productRepository.findFavoriteCount(productId));
    }

    /** 내가 찜한 상품 목록. 최근에 찜한 순. */
    public CursorResponse<ProductSummaryResponse> findMyFavorites(AuthUser viewer, String rawCursor, Integer size) {
        Cursor cursor = Cursor.parse(rawCursor);
        PageSize pageSize = PageSize.of(size);
        Pageable limit = PageRequest.of(0, pageSize.fetchSize());

        List<Favorite> fetched = cursor == null
                ? favoriteRepository.findFirstPage(viewer.id(), limit)
                // 형식이 틀린 커서는 여기서 INVALID_INPUT 으로 걸러진다.
                : favoriteRepository.findNextPage(viewer.id(), cursor.valueAsDateTime(), cursor.id(), limit);

        CursorResponse<Favorite> page = CursorResponse.of(fetched, pageSize,
                favorite -> Cursor.of(favorite.getCreatedAt(), favorite.getId()));
        Map<Long, String> thumbnails = productThumbnails.of(
                page.content().stream().map(favorite -> favorite.getProduct().getId()).toList());

        // 내 찜 목록이므로 isLiked 는 항상 true 다. 따로 조회할 필요가 없다.
        return page.map(favorite -> ProductSummaryResponse.from(
                favorite.getProduct(), thumbnails.get(favorite.getProduct().getId()), true));
    }
}
