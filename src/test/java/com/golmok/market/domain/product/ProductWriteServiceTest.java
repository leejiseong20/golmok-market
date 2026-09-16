package com.golmok.market.domain.product;

import com.golmok.market.domain.category.*;
import com.golmok.market.domain.region.*;
import com.golmok.market.domain.user.*;
import com.golmok.market.domain.image.*;
import com.golmok.market.global.security.AuthUser;
import com.golmok.market.global.error.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class ProductWriteServiceTest {
    @Autowired ProductRepository products;
    @Autowired ProductImageRepository images;
    @Autowired FavoriteRepository favorites;
    @Autowired ProductThumbnails thumbnails;
    @Autowired CategoryRepository categories;
    @Autowired RegionRepository regions;
    @Autowired UserRepository users;
    @Autowired UserRegionRepository userRegions;
    @Autowired ImageUrlValidator validator;
    @Autowired EntityManager em;

    @Test void 서비스는_주입된_시계로_정확히_24시간_경계를_판단한다() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-16T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        ProductService service = new ProductService(products, images, favorites, thumbnails, categories, users, userRegions, validator, clock);
        User seller = users.save(User.builder().email("clock@test.com").password("hash").nickname("시계판매자").build());
        Category category = categories.save(Category.create(null, "시계가구", null, 1));
        Region region = regions.save(Region.create("서울", "강남", "역삼동", 37.5, 127));
        Product product = products.save(Product.builder().seller(seller).category(category).region(region)
                .title("시계 상품").description("시계 기준 경계를 검증합니다.")
                .bumpedAt(LocalDateTime.now(clock).minusHours(24)).build());
        var viewer = new AuthUser(seller.getId(), seller.getRole());
        assertThat(service.bump(product.getId(), viewer).bumpedAt()).isEqualTo(LocalDateTime.now(clock));
        em.flush(); em.clear();
        assertThat(products.findById(product.getId()).orElseThrow().getBumpedAt()).isEqualTo(LocalDateTime.now(clock));
        assertThatThrownBy(() -> service.bump(product.getId(), viewer)).isInstanceOf(BusinessException.class);
    }
}
