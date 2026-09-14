package com.golmok.market.domain.product;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.product.dto.ProductDetailResponse;
import com.golmok.market.domain.product.dto.ProductSummaryResponse;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.pagination.Cursor;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.security.AuthUser;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ProductServiceTest {

    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductImageRepository imageRepository;
    @Autowired FavoriteRepository favoriteRepository;
    @Autowired UserRepository userRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    private User seller;
    private User buyer;
    private Category category;
    private Region region;

    @BeforeEach
    void 준비() {
        seller = userRepository.save(User.builder().email("seller@example.com").password("test-hash").nickname("판매자").build());
        buyer = userRepository.save(User.builder().email("buyer@example.com").password("test-hash").nickname("구매자").build());
        category = categoryRepository.save(Category.create(null, "가구", null, 1));
        region = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동", 37.5006, 127.0366));
    }

    private Product save(String title, int price) {
        return productRepository.save(Product.builder().seller(seller).category(category).region(region)
                .title(title).description("사용감이 적고 상태가 좋은 상품입니다.").price(price).build());
    }

    private void reload() {
        em.flush();
        em.clear();
    }

    private AuthUser viewer(User user) {
        return new AuthUser(user.getId(), user.getRole());
    }

    private CursorResponse<ProductSummaryResponse> page(ProductSort sort, String cursor, Integer size) {
        return productService.findPage(region.getId(), null, null, sort, cursor, size, null);
    }

    @ParameterizedTest
    @EnumSource(ProductSort.class)
    void 정렬값이_같아도_커서로_전체를_중복과_누락없이_조회한다(ProductSort sort) {
        int[] prices = {100, 0, 100, 200, 0, 100, 300};
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            Product product = save("상품" + i, prices[i]);
            ids.add(product.getId());
            em.flush();
            jdbc.update("update products set bumped_at = ? where id = ?",
                    LocalDateTime.of(2026, 9, 14, 12, 0).plusSeconds(i / 2), product.getId());
        }
        em.clear();
        List<Long> actual = new ArrayList<>();
        String cursor = null;
        for (int i = 0; i < 4; i++) {
            CursorResponse<ProductSummaryResponse> result = page(sort, cursor, 2);
            actual.addAll(result.content().stream().map(ProductSummaryResponse::id).toList());
            assertThat(result.hasNext()).isEqualTo(i < 3);
            cursor = result.nextCursor();
        }
        List<Long> expected = new ArrayList<>(ids);
        if (sort == ProductSort.LATEST) {
            Collections.reverse(expected);
        } else {
            expected = List.of(ids.get(1), ids.get(4), ids.get(0), ids.get(2), ids.get(5), ids.get(3), ids.get(6));
        }
        assertThat(actual).containsExactlyElementsOf(expected);
        assertThat(cursor).isNull();
    }

    @Test
    void 커서의_상품이_삭제돼도_다음_페이지를_조회한다() {
        Product first = save("첫상품", 100);
        Product second = save("둘째상품", 200);
        Product third = save("셋째상품", 300);
        reload();
        CursorResponse<ProductSummaryResponse> before = page(ProductSort.PRICE_ASC, null, 2);
        productRepository.findById(second.getId()).orElseThrow().softDelete();
        reload();

        assertThat(before.content()).extracting(ProductSummaryResponse::id).containsExactly(first.getId(), second.getId());
        assertThat(page(ProductSort.PRICE_ASC, before.nextCursor(), 2).content())
                .extracting(ProductSummaryResponse::id).containsExactly(third.getId());
    }

    @Test
    void 날짜_커서는_DB의_소수초_정밀도를_유지한다() {
        Product older = save("이전상품", 1);
        Product newer = save("다음상품", 1);
        em.flush();
        LocalDateTime time = LocalDateTime.of(2026, 9, 14, 12, 0, 0, 123456000);
        jdbc.update("update products set bumped_at = ? where id = ?", time, older.getId());
        jdbc.update("update products set bumped_at = ? where id = ?", time, newer.getId());
        em.clear();

        CursorResponse<ProductSummaryResponse> first = page(ProductSort.LATEST, null, 1);
        assertThat(Cursor.parse(first.nextCursor()).valueAsDateTime()).isEqualTo(time);
        assertThat(page(ProductSort.LATEST, first.nextCursor(), 1).content())
                .extracting(ProductSummaryResponse::id).containsExactly(older.getId());
    }

    @Test
    void 동네_카테고리_검색_필터를_조합하고_삭제된_상품은_제외한다() {
        Product target = save("원목 식탁", 500);
        save("철제 의자", 100);
        Product deleted = save("원목 삭제상품", 100);
        deleted.softDelete();
        Category otherCategory = categoryRepository.save(Category.create(null, "기타", null, 2));
        Product wrongCategory = save("원목 다른카테고리", 100);
        wrongCategory.update(wrongCategory.getTitle(), wrongCategory.getDescription(), 100, otherCategory, false, TradeType.DIRECT);
        Region otherRegion = regionRepository.save(Region.create("서울특별시", "마포구", "서교동", 37.55, 126.92));
        productRepository.save(Product.builder().seller(seller).category(category).region(otherRegion)
                .title("원목 다른동네").description("다른 동네의 상품입니다.").price(100).build());
        reload();

        assertThat(productService.findPage(region.getId(), category.getId(), " 원목 ", ProductSort.LATEST, null, 20, null).content())
                .extracting(ProductSummaryResponse::id).containsExactly(target.getId());
    }

    @Test
    void 검색은_본문도_찾고_빈_검색어는_필터를_생략한다() {
        Product product = save("의자", 100);
        reload();
        assertThat(productService.findPage(region.getId(), null, "상태가 좋은", null, null, null, null).content())
                .extracting(ProductSummaryResponse::id).containsExactly(product.getId());
        assertThat(productService.findPage(region.getId(), null, "   ", null, null, null, null).content()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "!%_"})
    void 검색_특수문자는_문자_그대로_취급한다(String literal) {
        Product matching = save("할인" + literal + "상품", 100);
        save("일반상품", 100);
        reload();
        assertThat(productService.findPage(region.getId(), null, literal, null, null, null, null).content())
                .extracting(ProductSummaryResponse::id).containsExactly(matching.getId());
    }

    @Test
    void 없는_동네나_카테고리와_검색_결과는_빈_커서_응답이다() {
        save("상품", 100);
        reload();
        assertThat(productService.findPage(Long.MAX_VALUE, null, null, null, null, null, null).content()).isEmpty();
        assertThat(productService.findPage(region.getId(), Long.MAX_VALUE, null, null, null, null, null).content()).isEmpty();
        CursorResponse<ProductSummaryResponse> result = productService.findPage(region.getId(), null, "없는검색어", null, null, null, null);
        assertThat(result.content()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 예약과_판매완료도_목록과_상세에서_조회된다() {
        Product reserved = save("예약상품", 100);
        reserved.reserve();
        Product sold = save("판매완료", 100);
        sold.markSold();
        reload();
        assertThat(page(ProductSort.PRICE_ASC, null, 20).content()).extracting(ProductSummaryResponse::status)
                .containsExactly(ProductStatus.RESERVED, ProductStatus.SOLD);
        assertThat(productService.findDetail(sold.getId(), null).status()).isEqualTo(ProductStatus.SOLD);
    }

    @Test
    void 찜은_로그인한_사용자의_것만_반영하고_목록_조회는_조회수를_올리지_않는다() {
        Product liked = save("찜상품", 100);
        Product other = save("다른상품", 200);
        favoriteRepository.save(Favorite.of(buyer, liked));
        favoriteRepository.save(Favorite.of(seller, other));
        reload();
        assertThat(productService.findPage(region.getId(), null, null, ProductSort.PRICE_ASC, null, 20, viewer(buyer)).content())
                .extracting(ProductSummaryResponse::isLiked).containsExactly(true, false);
        assertThat(page(ProductSort.PRICE_ASC, null, 20).content())
                .extracting(ProductSummaryResponse::isLiked).containsExactly(false, false);
        assertThat(productRepository.findById(liked.getId()).orElseThrow().getViewCount()).isZero();
    }

    @Test
    void 이미지_순서가_같으면_id로_정렬하고_첫_이미지를_썸네일로_쓴다() {
        Product product = save("이미지상품", 100);
        product.addImage("https://example.com/first.png");
        product.addImage("https://example.com/second.png");
        product.addImage("https://example.com/third.png");
        em.flush();
        List<ProductImage> images = product.getImages();
        jdbc.update("update product_images set sort_order = 2 where id = ?", images.get(0).getId());
        jdbc.update("update product_images set sort_order = 0 where id in (?, ?)", images.get(1).getId(), images.get(2).getId());
        em.clear();

        assertThat(page(null, null, 20).content().getFirst().thumbnailUrl()).isEqualTo("https://example.com/second.png");
        assertThat(productService.findDetail(product.getId(), viewer(seller)).images())
                .extracting(ProductDetailResponse.ImageInfo::imageUrl)
                .containsExactly("https://example.com/second.png", "https://example.com/third.png", "https://example.com/first.png");
    }

    @Test
    void 이미지가_없으면_썸네일은_null이고_상세_이미지는_빈_배열이다() {
        Product product = save("이미지없음", 100);
        reload();
        assertThat(page(null, null, 20).content().getFirst().thumbnailUrl()).isNull();
        assertThat(productService.findDetail(product.getId(), null).images()).isEmpty();
    }

    @Test
    void 이미지와_찜_생성_시각은_H2에서도_JPA가_채운다() {
        Product product = save("상품", 100);
        product.addImage("https://example.com/image.png");
        Favorite favorite = favoriteRepository.save(Favorite.of(buyer, product));
        reload();
        assertThat(favoriteRepository.findById(favorite.getId()).orElseThrow().getCreatedAt()).isNotNull();
        assertThat(imageRepository.findOrderedByProductIds(List.of(product.getId())).getFirst().getCreatedAt()).isNotNull();
    }

    @Test
    void 상세_조회는_본인을_제외하고_매번_증가한_조회수를_반환한다() {
        Product product = save("상품", 100);
        favoriteRepository.save(Favorite.of(buyer, product));
        reload();
        ProductDetailResponse mine = productService.findDetail(product.getId(), viewer(seller));
        assertThat(mine.isMine()).isTrue();
        assertThat(mine.viewCount()).isZero();
        ProductDetailResponse anonymous = productService.findDetail(product.getId(), null);
        assertThat(anonymous.viewCount()).isEqualTo(1);
        assertThat(anonymous.isLiked()).isFalse();
        assertThat(anonymous.isMine()).isFalse();
        ProductDetailResponse others = productService.findDetail(product.getId(), viewer(buyer));
        assertThat(others.viewCount()).isEqualTo(2);
        assertThat(others.isLiked()).isTrue();
        assertThat(others.isMine()).isFalse();
        assertThat(productService.findDetail(product.getId(), null).viewCount()).isEqualTo(3);
    }

    @Test
    void 조회수_증가로_수정시각이나_끌어올리기_시각은_바뀌지_않는다() {
        Product product = save("상품", 100);
        reload();
        Product before = productRepository.findById(product.getId()).orElseThrow();
        LocalDateTime updated = before.getUpdatedAt();
        LocalDateTime bumped = before.getBumpedAt();
        productService.findDetail(product.getId(), null);
        Product after = productRepository.findById(product.getId()).orElseThrow();
        assertThat(after.getUpdatedAt()).isEqualTo(updated);
        assertThat(after.getBumpedAt()).isEqualTo(bumped);
    }

    @Test
    void 오래된_상품_엔티티를_수정해도_DB_조회수를_덮어쓰지_않는다() {
        Product product = save("이전제목", 100);
        em.flush();
        jdbc.update("update products set view_count = view_count + 5 where id = ?", product.getId());
        product.update("수정제목", product.getDescription(), 200, category, false, TradeType.DIRECT);
        reload();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getViewCount()).isEqualTo(5);
    }

    @Test
    void 없는_상품과_삭제된_상품은_같은_에러이고_삭제_상품_조회수는_증가하지_않는다() {
        Product deleted = save("삭제상품", 100);
        deleted.softDelete();
        reload();
        for (long id : List.of(deleted.getId(), Long.MAX_VALUE)) {
            assertThatThrownBy(() -> productService.findDetail(id, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        }
        assertThat(productRepository.findById(deleted.getId()).orElseThrow().getViewCount()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"LATEST, invalid", "LATEST, 100_1", "LATEST, 2026-02-30T12:00:00_1",
            "PRICE_ASC, -1_1", "PRICE_ASC, 2147483648_1", "PRICE_ASC, 1_0", "PRICE_ASC, 2026-09-14T12:00:00_1"})
    void 빈_목록에서도_잘못된_정렬별_커서는_거부한다(ProductSort sort, String cursor) {
        assertThatThrownBy(() -> page(sort, cursor, 20)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 상품과_이미지가_늘어도_로그인_목록은_쿼리_3번으로_조회한다() {
        for (int i = 0; i < 12; i++) {
            Product product = save("상품" + i, i);
            product.addImage("https://example.com/first.png");
            product.addImage("https://example.com/second.png");
            favoriteRepository.save(Favorite.of(buyer, product));
        }
        reload();
        Statistics statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            CursorResponse<ProductSummaryResponse> result = productService.findPage(
                    region.getId(), null, null, ProductSort.LATEST, null, 10, viewer(buyer));
            assertThat(result.content()).hasSize(10);
            assertThat(result.hasNext()).isTrue();
            assertThat(result.content()).allMatch(ProductSummaryResponse::isLiked);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }
    }
}
