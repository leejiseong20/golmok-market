package com.golmok.market.domain.trade;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
import com.golmok.market.domain.trade.dto.ReviewCreateRequest;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.security.AuthUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ReviewServiceTest {

    @Autowired ReviewService service;
    @Autowired ReviewRepository reviews;
    @Autowired TradeRepository trades;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired RegionRepository regions;
    @Autowired EntityManager em;
    private User seller;
    private User buyer;
    private Category category;
    private Region region;

    @BeforeEach
    void 준비() {
        seller = users.save(User.builder().email("s@example.test").password("hash").nickname("판매자").build());
        buyer = users.save(User.builder().email("b@example.test").password("hash").nickname("구매자").build());
        category = categories.save(Category.create(null, "가구", null, 1));
        region = regions.save(Region.create("서울", "강남", "역삼", 37.5, 127));
    }

    @ParameterizedTest
    @CsvSource({"1,36.1", "2,36.3", "3,36.5", "4,36.7", "5,36.9"})
    void 평점별_온도를_정확한_소수점으로_반영한다(int score, String expected) {
        Trade trade = completed();
        service.write(trade.getId(), viewer(), new ReviewCreateRequest(score, null));
        em.flush();
        em.clear();
        assertThat(users.findById(seller.getId()).orElseThrow().getMannerTemp()).isEqualByComparingTo(expected);
        assertThat(reviews.findAll().getFirst().getCreatedAt()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({"99.8,5,99.9", "0.1,1,0.0", "99.9,1,99.5", "0.0,5,0.4"})
    void 온도_상하한을_넘지_않는다(String initial, int score, String expected) {
        Trade trade = completed();
        em.createNativeQuery("update users set manner_temp = :temp where id = :id")
                .setParameter("temp", initial).setParameter("id", seller.getId()).executeUpdate();
        em.clear();
        service.write(trade.getId(), viewer(), new ReviewCreateRequest(score, null));
        em.clear();
        assertThat(users.findById(seller.getId()).orElseThrow().getMannerTemp()).isEqualByComparingTo(expected);
    }

    @Test
    void 같은_초의_후기도_커서로_빠짐없이_조회하고_작성_여부를_일괄_조회한다() {
        Trade first = completed();
        Trade second = completed();
        service.write(first.getId(), viewer(), new ReviewCreateRequest(4, "첫 후기"));
        service.write(second.getId(), viewer(), new ReviewCreateRequest(5, "둘째 후기"));
        em.createQuery("update Review r set r.createdAt = :time")
                .setParameter("time", LocalDateTime.of(2026, 1, 1, 12, 0)).executeUpdate();
        em.clear();
        var page = service.findReceived(seller.getId(), null, 1);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.content().getFirst().content()).isEqualTo("둘째 후기");
        var next = service.findReceived(seller.getId(), page.nextCursor(), 1);
        assertThat(next.hasNext()).isFalse();
        assertThat(next.content().getFirst().content()).isEqualTo("첫 후기");
        assertThat(service.reviewedTradeIds(buyer.getId(), List.of(first.getId(), second.getId())))
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    void 엔티티를_직접_만들어도_입력_검증을_우회하지_못한다() {
        Trade trade = completed();
        assertThatThrownBy(() -> Review.write(trade, buyer, 0, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Review.write(trade, buyer, 5, "가".repeat(501))).isInstanceOf(BusinessException.class);
    }

    private Trade completed() {
        Product product = products.save(Product.builder().seller(seller).category(category).region(region)
                .title("후기 상품").description("검증 상품").price(100).build());
        Trade trade = trades.save(Trade.request(product, null, buyer));
        trade.completeInPerson();
        em.flush();
        return trade;
    }

    private AuthUser viewer() {
        return new AuthUser(buyer.getId(), buyer.getRole());
    }
}
