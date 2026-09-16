package com.golmok.market.domain.product;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 작업 스레드가 데이터를 볼 수 있도록 준비 데이터를 커밋하고, 전용 H2 DB 를 컨텍스트 종료 시 폐기한다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:product-concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductViewCountConcurrencyTest {

    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void 비로그인과_다른_사용자의_동시_조회는_모두_누적하고_본인은_제외한다() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Fixture fixture = transaction.execute(status -> {
            User seller = userRepository.save(User.builder().email("seller@example.com").password("test").nickname("판매자").build());
            User buyer = userRepository.save(User.builder().email("buyer@example.com").password("test").nickname("구매자").build());
            Category category = categoryRepository.save(Category.create(null, "가구", null, 1));
            Region region = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동", 37.5, 127));
            Product product = productRepository.save(Product.builder().seller(seller).category(category).region(region)
                    .title("동시 조회 상품").description("조회수 증가분을 확인할 상품입니다.").price(100).build());
            return new Fixture(product.getId(), seller.getId(), buyer.getId());
        });
        assertThat(fixture).isNotNull();
        var executor = Executors.newFixedThreadPool(5);
        CountDownLatch ready = new CountDownLatch(5);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<Integer>>> tasks = new ArrayList<>();
        try {
            for (int worker = 0; worker < 5; worker++) {
                boolean owner = worker == 4;
                AuthUser viewer = owner ? new AuthUser(fixture.sellerId(), UserRole.USER)
                        : worker % 2 == 0 ? null : new AuthUser(fixture.buyerId(), UserRole.USER);
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    List<Integer> counts = new ArrayList<>();
                    for (int i = 0; i < 5; i++) {
                        var detail = productService.findDetail(fixture.productId(), viewer);
                        assertThat(detail.isMine()).isEqualTo(owner);
                        if (!owner) {
                            counts.add(detail.viewCount());
                        }
                    }
                    return counts;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> actual = new ArrayList<>();
            for (Future<List<Integer>> task : tasks) {
                actual.addAll(task.get(20, TimeUnit.SECONDS));
            }
            // 최종 누적값뿐 아니라 각각의 응답이 자기 트랜잭션에서 증가한 값을 읽는지도 검증한다.
            assertThat(actual).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 20).boxed().toList());
            Integer stored = transaction.execute(status -> productRepository.findById(fixture.productId()).orElseThrow().getViewCount());
            assertThat(stored).isEqualTo(20);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private record Fixture(long productId, long sellerId, long buyerId) {
    }

    @Test
    void 동시에_끌어올려도_한_요청만_성공한다() throws Exception {
        var transaction = new TransactionTemplate(transactionManager);
        var fixture = transaction.execute(status -> {
            User seller = userRepository.save(User.builder().email("bump@test.com").password("hash").nickname("끌어올림판매자").build());
            Category category = categoryRepository.save(Category.create(null, "끌어올림가구", null, 1));
            Region region = regionRepository.save(Region.create("서울", "강남", "시간동", 37.5, 127));
            Product product = productRepository.save(Product.builder().seller(seller).category(category).region(region)
                    .title("동시 끌어올리기").description("중복 처리를 확인하는 상품입니다.")
                    .bumpedAt(java.time.LocalDateTime.now().minusDays(2)).build());
            return new Fixture(product.getId(), seller.getId(), seller.getId());
        });
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> request = () -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                try {
                    productService.bump(fixture.productId(), new AuthUser(fixture.sellerId(), UserRole.USER));
                    return true;
                } catch (BusinessException e) {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
                    return false;
                }
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally { start.countDown(); }
    }
}
