package com.golmok.market.domain.product;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.image.ImageUrlValidator;
import com.golmok.market.domain.product.dto.ProductBumpResponse;
import com.golmok.market.domain.product.dto.ProductDetailResponse;
import com.golmok.market.domain.product.dto.ProductSummaryResponse;
import com.golmok.market.domain.product.dto.ProductWriteRequest;
import com.golmok.market.domain.product.event.ProductPriceDroppedEvent;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.search.ProductSearchedEvent;
import com.golmok.market.domain.search.SearchKeywords;
import com.golmok.market.domain.trade.TradeRepository;
import com.golmok.market.domain.trade.TradeStatus;
import com.golmok.market.domain.user.UserRegionRepository;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.pagination.Cursor;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.pagination.PageSize;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
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
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final UserRegionRepository userRegionRepository;
    private final ImageUrlValidator imageUrlValidator;
    private final TradeRepository tradeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public ProductDetailResponse create(ProductWriteRequest request, AuthUser viewer) {
        Region region = verifiedRegion(viewer.id(), request.regionId());
        Category category = category(request.categoryId());
        imageUrlValidator.validate(request.imageUrls());
        Product product = Product.builder().seller(userRepository.getReferenceById(viewer.id()))
                .category(category).region(region).title(request.title()).description(request.description())
                .price(request.price()).negotiable(request.isNegotiable()).tradeType(request.tradeType())
                .bumpedAt(LocalDateTime.now(clock)).build();
        product.replaceImages(request.imageUrls());
        productRepository.saveAndFlush(product);
        return ownDetail(product);
    }

    @Transactional
    public ProductDetailResponse update(long id, ProductWriteRequest request, AuthUser viewer) {
        Product product = ownedForUpdate(id, viewer);
        // 엔티티 update() 도 같은 검사를 하지만, 동네·이미지 검증보다 먼저 막아야
        // 판매완료 상품에 잘못된 이미지를 보냈을 때 원인이 "이미지 오류"로 잘못 안내되지 않는다.
        if (product.getStatus() == ProductStatus.SOLD) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "판매완료 상품은 수정할 수 없습니다.");
        }
        Region region = verifiedRegion(viewer.id(), request.regionId());
        Category category = category(request.categoryId());
        imageUrlValidator.validate(request.imageUrls());
        int oldPrice = product.getPrice();
        product.update(request.title(), request.description(), request.price(), category, region,
                request.isNegotiable(), request.tradeType());
        product.replaceImages(request.imageUrls());
        productRepository.flush();
        if (request.price() < oldPrice) {
            // 찜한 사람에게만 알린다. 받는 사람 조회는 커밋 뒤 알림 쪽에서 한다.
            eventPublisher.publishEvent(new ProductPriceDroppedEvent(product.getId(), product.getTitle(), oldPrice, request.price()));
        }
        return ownDetail(product);
    }

    @Transactional
    public void delete(long id, AuthUser viewer) {
        Product product = ownedForUpdate(id, viewer);
        requireNoTradeInProgress(product);
        product.softDelete();
    }

    @Transactional
    public ProductDetailResponse changeStatus(long id, ProductStatus status, AuthUser viewer) {
        Product product = ownedForUpdate(id, viewer);
        requireNoTradeInProgress(product);
        switch (status) {
            case ON_SALE -> product.cancelReservation();
            case RESERVED -> product.reserve();
            case SOLD -> product.markSold();
        }
        return ownDetail(product);
    }

    @Transactional
    public ProductBumpResponse bump(long id, AuthUser viewer) {
        Product product = ownedForUpdate(id, viewer);
        product.bump(LocalDateTime.now(clock));
        return new ProductBumpResponse(product.getBumpedAt());
    }

    public CursorResponse<ProductSummaryResponse> findMyPage(AuthUser viewer, ProductStatus status,
                                                            String rawCursor, Integer size) {
        Cursor cursor = Cursor.parse(rawCursor);
        ProductSort.LATEST.validateCursor(cursor);
        PageSize pageSize = PageSize.of(size);
        var page = CursorResponse.of(productRepository.findMyPage(viewer.id(), status, cursor, pageSize),
                pageSize, product -> Cursor.of(product.getCreatedAt(), product.getId()));
        Map<Long, String> thumbnails = productThumbnails.of(page.content().stream().map(Product::getId).toList());
        return page.map(product -> ProductSummaryResponse.from(product, thumbnails.get(product.getId()), false));
    }

    private Product ownedForUpdate(long id, AuthUser viewer) {
        Product product = productRepository.findByIdForUpdate(id)
                .filter(p -> !p.isDeleted()).orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isOwnedBy(viewer.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return product;
    }

    /**
     * 진행 중 거래(예약·결제·발송)가 있으면 상품 상태를 직접 바꾸거나 지우지 못한다.
     * 막지 않으면 거래는 예약인데 상품만 판매중이 되는 식으로 둘이 어긋난다. 거래 상태는 채팅방에서 바꾼다.
     * 상품 잠금을 잡은 뒤 확인하므로, 같은 상품을 잠그는 예약과 동시에 들어와도 순서가 보장된다.
     */
    private void requireNoTradeInProgress(Product product) {
        if (tradeRepository.existsByProductIdAndStatusIn(product.getId(), TradeStatus.activeStatuses())) {
            throw new BusinessException(ErrorCode.TRADE_IN_PROGRESS);
        }
    }

    private Region verifiedRegion(long userId, long regionId) {
        return userRegionRepository.findByUserIdAndRegionId(userId, regionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_VERIFIED)).getRegion();
    }

    private Category category(long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private ProductDetailResponse ownDetail(Product product) {
        // 쓰기 응답은 상세 GET을 호출하지 않는다. 조회수 증가 같은 부수 효과를 피한다.
        return ProductDetailResponse.from(product, product.getImages(), false, true);
    }

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
        // 인기 검색어용 로그. "더 보기"(커서 요청)까지 세면 결과를 많이 넘겨본 검색어가 부풀려지므로 첫 페이지만 남긴다.
        // 저장은 조회가 끝난 뒤 별도 실행기에서 한다(SearchLogRecorder). 검색 응답은 기다리지 않는다.
        String loggedKeyword = SearchKeywords.normalize(normalizedKeyword);
        if (cursor == null && loggedKeyword != null) {
            eventPublisher.publishEvent(new ProductSearchedEvent(viewer == null ? null : viewer.id(), regionId, loggedKeyword));
        }
        // 차단한 사람의 상품은 목록에서 뺀다(리포지터리의 서브쿼리). 비로그인에게는 차단이 없다.
        List<Product> fetched = productRepository.findPage(regionId, categoryId, normalizedKeyword, order, cursor, pageSize,
                viewer == null ? null : viewer.id());
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
