package com.golmok.market.domain.product;

import com.golmok.market.domain.product.dto.ProductDetailResponse;
import com.golmok.market.domain.product.dto.ProductSummaryResponse;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.pagination.Cursor;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.pagination.PageSize;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final FavoriteRepository favoriteRepository;
    private final ProductThumbnails productThumbnails;

    public CursorResponse<ProductSummaryResponse> findPage(long regionId, Long categoryId, String keyword,
                                                          ProductSort sort, String rawCursor, Integer size, AuthUser viewer) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (regionId <= 0 || (categoryId != null && categoryId <= 0)
                || (normalizedKeyword != null && normalizedKeyword.length() > 50)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        ProductSort order = sort == null ? ProductSort.LATEST : sort;
        Cursor cursor = Cursor.parse(rawCursor);
        order.validateCursor(cursor);
        PageSize pageSize = PageSize.of(size);
        List<Product> fetched = productRepository.findPage(regionId, categoryId, normalizedKeyword, order, cursor, pageSize);
        CursorResponse<Product> page = CursorResponse.of(fetched, pageSize, order::cursorOf);
        List<Long> ids = page.content().stream().map(Product::getId).toList();
        // 페이징을 마친 상품에 대해서만 이미지·찜을 일괄 조회한다. 컬렉션 지연 로딩을 유발하지 않는다.
        Map<Long, String> thumbnails = productThumbnails.of(ids);
        Set<Long> likedIds = new HashSet<>();
        if (!ids.isEmpty() && viewer != null) {
            likedIds.addAll(favoriteRepository.findLikedProductIds(viewer.id(), ids));
        }
        return page.map(product -> ProductSummaryResponse.from(product, thumbnails.get(product.getId()), likedIds.contains(product.getId())));
    }

    @Transactional
    public ProductDetailResponse findDetail(long id, AuthUser viewer) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Long viewerId = viewer == null ? null : viewer.id();
        // 같은 트랜잭션에서 UPDATE 를 먼저 한다. 갱신 전 조회의 스냅샷이나 엔티티 캐시를 응답에 쓰지 않는다.
        productRepository.incrementViewCount(id, viewerId);
        Product product = productRepository.findVisibleById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        List<ProductImage> images = productImageRepository.findOrderedByProductIds(List.of(id));
        boolean liked = viewerId != null && favoriteRepository.existsByUserIdAndProductId(viewerId, id);
        return ProductDetailResponse.from(product, images, liked, product.isOwnedBy(viewerId));
    }
}
