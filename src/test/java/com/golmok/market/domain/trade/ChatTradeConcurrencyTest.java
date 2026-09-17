package com.golmok.market.domain.trade;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.chat.ChatService;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.product.ProductStatus;
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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 작업 스레드가 데이터를 볼 수 있도록 준비 데이터를 커밋하고, 전용 H2 DB 를 컨텍스트 종료 시 폐기한다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:chat-trade-concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatTradeConcurrencyTest {

    @Autowired ChatTradeService chatTradeService;
    @Autowired ChatService chatService;
    @Autowired TradeRepository tradeRepository;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void 같은_상품을_두_채팅방에서_동시에_예약하면_한_건만_성공한다() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Fixture fixture = transaction.execute(status -> {
            User seller = userRepository.save(User.builder().email("seller@example.com").password("hash").nickname("판매자").build());
            User first = userRepository.save(User.builder().email("first@example.com").password("hash").nickname("첫구매자").build());
            User second = userRepository.save(User.builder().email("second@example.com").password("hash").nickname("둘째구매자").build());
            Category category = categoryRepository.save(Category.create(null, "가구", null, 1));
            Region region = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동", 37.5, 127));
            Product product = productRepository.save(Product.builder().seller(seller).category(category).region(region)
                    .title("동시 예약 상품").description("예약 중복을 확인할 상품입니다.").price(100).build());
            return new Fixture(product.getId(), seller.getId(), first.getId(), second.getId());
        });
        assertThat(fixture).isNotNull();
        long firstRoom = chatService.open(fixture.productId(), user(fixture.firstBuyerId())).room().roomId();
        long secondRoom = chatService.open(fixture.productId(), user(fixture.secondBuyerId())).room().roomId();
        AuthUser seller = user(fixture.sellerId());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(reserveTask(firstRoom, seller, ready, start));
            var second = executor.submit(reserveTask(secondRoom, seller, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
        }

        Long active = transaction.execute(status -> tradeRepository.findAll().stream()
                .filter(trade -> trade.getStatus().isActive()).count());
        assertThat(active).isEqualTo(1);
        ProductStatus productStatus = transaction.execute(status ->
                productRepository.findById(fixture.productId()).orElseThrow().getStatus());
        assertThat(productStatus).isEqualTo(ProductStatus.RESERVED);
    }

    private Callable<Boolean> reserveTask(long roomId, AuthUser seller, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                chatTradeService.reserve(roomId, seller);
                return true;
            } catch (BusinessException e) {
                // 늦은 요청은 상품이 이미 예약중이라 상태 오류로 실패해야 한다(500·UNIQUE 위반이 아니라).
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);
                return false;
            }
        };
    }

    private static AuthUser user(long id) {
        return new AuthUser(id, UserRole.USER);
    }

    private record Fixture(long productId, long sellerId, long firstBuyerId, long secondBuyerId) {
    }
}
