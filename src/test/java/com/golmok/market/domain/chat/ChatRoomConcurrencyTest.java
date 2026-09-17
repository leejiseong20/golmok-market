package com.golmok.market.domain.chat;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.domain.user.UserRole;
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

import static org.assertj.core.api.Assertions.assertThat;

/** 작업 스레드가 데이터를 볼 수 있도록 준비 데이터를 커밋하고, 전용 H2 DB 를 컨텍스트 종료 시 폐기한다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:chat-concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatRoomConcurrencyTest {

    private static final int REQUESTS = 5;

    @Autowired ChatService chatService;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void 같은_구매자가_동시에_채팅하기를_눌러도_방은_하나고_채팅_수는_1_이다() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Fixture fixture = transaction.execute(status -> {
            User seller = userRepository.save(User.builder().email("seller@example.com").password("hash").nickname("판매자").build());
            User buyer = userRepository.save(User.builder().email("buyer@example.com").password("hash").nickname("구매자").build());
            Category category = categoryRepository.save(Category.create(null, "가구", null, 1));
            Region region = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동", 37.5, 127));
            Product product = productRepository.save(Product.builder().seller(seller).category(category).region(region)
                    .title("동시 채팅 상품").description("방 중복 생성을 확인할 상품입니다.").price(100).build());
            return new Fixture(product.getId(), buyer.getId());
        });
        assertThat(fixture).isNotNull();
        AuthUser buyer = new AuthUser(fixture.buyerId(), UserRole.USER);

        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ChatService.OpenResult>> tasks = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(REQUESTS)) {
            for (int i = 0; i < REQUESTS; i++) {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return chatService.open(fixture.productId(), buyer);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ChatService.OpenResult> results = new ArrayList<>();
            for (Future<ChatService.OpenResult> task : tasks) {
                results.add(task.get(20, TimeUnit.SECONDS));
            }

            // 모두 성공하고, 한 요청만 방을 새로 만들며, 전부 같은 방을 받는다.
            assertThat(results).filteredOn(ChatService.OpenResult::created).hasSize(1);
            assertThat(results).extracting(result -> result.room().roomId()).containsOnly(results.get(0).room().roomId());
            assertThat(chatRoomRepository.count()).isEqualTo(1);
            Integer chatCount = transaction.execute(status ->
                    productRepository.findById(fixture.productId()).orElseThrow().getChatCount());
            assertThat(chatCount).isEqualTo(1);
        } finally {
            start.countDown();
        }
    }

    private record Fixture(long productId, long buyerId) {
    }
}
