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
import com.golmok.market.domain.user.UserRole;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

/** 별도 DB 와 커밋된 준비 데이터로 실제 트랜잭션 간 경합을 검증한다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:review-concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReviewConcurrencyTest {

    @Autowired ReviewService service;
    @MockitoSpyBean ReviewRepository reviews;
    @Autowired TradeRepository trades;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired RegionRepository regions;
    @Autowired PlatformTransactionManager manager;

    @Test
    void 같은_후기를_동시에_작성하면_하나만_성공하고_나머지는_업무_오류다() throws Exception {
        Fixture fixture = prepare();
        CountDownLatch checks = new CountDownLatch(2);
        var delegate = mockingDetails(reviews).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            Object result = delegate.answer(invocation);
            // 잠금이 있으면 두 번째 요청은 여기에 아직 못 온다. 없으면 둘 다 '후기 없음'을 읽도록 겹친다.
            checks.countDown();
            checks.await(400, TimeUnit.MILLISECONDS);
            return result;
        }).when(reviews).findByTradeIdAndReviewerId(anyLong(), anyLong());
        var results = concurrent(() -> write(fixture.firstTrade(), fixture.buyer()),
                () -> write(fixture.firstTrade(), fixture.buyer()));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(reviews.countByRevieweeId(fixture.seller())).isEqualTo(1);
        assertThat(temperature(fixture.seller())).isEqualByComparingTo("36.9");
    }

    @Test
    void 다른_거래의_후기를_동시에_받아도_증가분이_모두_남는다() throws Exception {
        Fixture fixture = prepare();
        assertThat(concurrent(() -> write(fixture.firstTrade(), fixture.buyer()),
                () -> write(fixture.secondTrade(), fixture.buyer()))).containsOnly(true);
        assertThat(temperature(fixture.seller())).isEqualByComparingTo("37.3");
    }

    @Test
    void 서로_다른_거래에서_상호_평가해도_교착되지_않는다() throws Exception {
        Fixture fixture = prepare();
        assertThat(concurrent(() -> write(fixture.firstTrade(), fixture.buyer()),
                () -> write(fixture.secondTrade(), fixture.seller()))).containsOnly(true);
        assertThat(temperature(fixture.seller())).isEqualByComparingTo("36.9");
        assertThat(temperature(fixture.buyer())).isEqualByComparingTo("36.9");
    }

    @Test
    void 오래된_사용자_엔티티로_로그인_기록을_저장해도_온도가_덮이지_않는다() throws Exception {
        Fixture fixture = prepare();
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch reviewed = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var login = executor.submit(() -> tx().execute(status -> {
                User stale = users.findById(fixture.seller()).orElseThrow();
                loaded.countDown();
                await(reviewed);
                stale.recordLogin();
                return true;
            }));
            await(loaded);
            try {
                write(fixture.firstTrade(), fixture.buyer());
            } finally {
                reviewed.countDown();
            }
            assertThat(login.get(15, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(temperature(fixture.seller())).isEqualByComparingTo("36.9");
    }

    @Test
    void 원자적_온도_갱신은_행을_미리_잠그지_않아도_증가분을_보존한다() throws Exception {
        Fixture fixture = prepare();
        assertThat(concurrent(() -> tx().execute(status -> users.adjustMannerTemp(fixture.seller(), new BigDecimal("0.4")) == 1),
                () -> tx().execute(status -> users.adjustMannerTemp(fixture.seller(), new BigDecimal("0.4")) == 1)))
                .containsOnly(true);
        assertThat(temperature(fixture.seller())).isEqualByComparingTo("37.3");
    }

    @Test
    void 트랜잭션이_롤백되면_후기와_온도가_함께_되돌아간다() {
        Fixture fixture = prepare();
        tx().executeWithoutResult(status -> {
            write(fixture.firstTrade(), fixture.buyer());
            status.setRollbackOnly();
        });
        assertThat(reviews.countByRevieweeId(fixture.seller())).isZero();
        assertThat(temperature(fixture.seller())).isEqualByComparingTo("36.5");
    }

    private boolean write(long trade, long reviewer) {
        try {
            service.write(trade, new AuthUser(reviewer, UserRole.USER), new ReviewCreateRequest(5, "친절해요"));
            return true;
        } catch (BusinessException e) {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ALREADY_REVIEWED);
            return false;
        }
    }

    private List<Boolean> concurrent(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { ready.countDown(); await(start); return first.call(); });
            var b = executor.submit(() -> { ready.countDown(); await(start); return second.call(); });
            await(ready);
            start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private BigDecimal temperature(long id) {
        return tx().execute(status -> users.findById(id).orElseThrow().getMannerTemp());
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(manager);
    }

    private Fixture prepare() {
        return tx().execute(status -> {
            String tag = UUID.randomUUID().toString();
            User seller = users.save(User.builder().email("s-" + tag).password("hash").nickname("s" + tag.substring(0, 8)).build());
            User buyer = users.save(User.builder().email("b-" + tag).password("hash").nickname("b" + tag.substring(0, 8)).build());
            Category category = categories.save(Category.create(null, "가구" + tag.substring(0, 8), null, 1));
            Region region = regions.save(Region.create("서울", "강남", tag.substring(0, 8), 37.5, 127));
            long first = completed(seller, buyer, category, region);
            long second = completed(seller, buyer, category, region);
            return new Fixture(seller.getId(), buyer.getId(), first, second);
        });
    }

    private long completed(User seller, User buyer, Category category, Region region) {
        Product product = products.save(Product.builder().seller(seller).category(category).region(region)
                .title("동시성 상품").description("검증용").price(100).build());
        Trade trade = trades.save(Trade.request(product, null, buyer));
        trade.completeInPerson();
        return trade.getId();
    }

    private record Fixture(long seller, long buyer, long firstTrade, long secondTrade) {
    }
}
