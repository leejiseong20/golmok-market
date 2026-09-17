package com.golmok.market.domain.notification;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.image.ImageProperties;
import com.golmok.market.domain.image.ImageService;
import com.golmok.market.domain.notification.event.NotificationRequestedEvent;
import com.golmok.market.domain.product.FavoriteRepository;
import com.golmok.market.domain.product.FavoriteService;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRegion;
import com.golmok.market.domain.user.UserRegionRepository;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.security.AuthUser;
import com.golmok.market.global.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 원래 동작(찜·가격 인하·거래·후기)에서 알림이 만들어지는 흐름.
 *
 * 알림은 커밋 뒤에 만들어지므로 테스트 메서드를 롤백 트랜잭션으로 감싸면 알림이 생기지 않는다.
 * 그래서 이 클래스만 요청마다 실제로 커밋하고, 전용 H2 DB 를 컨텍스트 종료 시 폐기한다.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:notification-flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired UserRegionRepository userRegionRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired FavoriteRepository favoriteRepository;
    @Autowired FavoriteService favoriteService;
    @Autowired ImageService imageService;
    @Autowired ImageProperties imageProperties;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean NotificationService notificationService;

    private User seller;
    private User buyer;
    private User other;
    private String sellerToken;
    private String buyerToken;
    private String otherToken;
    private Category category;
    private Region region;
    private Product product;
    private String imageUrl;

    @BeforeEach
    void 준비() {
        // 요청마다 커밋되므로 테스트끼리 이메일·닉네임이 겹치지 않게 한다.
        String tag = UUID.randomUUID().toString().substring(0, 8);
        seller = user("seller", tag);
        buyer = user("buyer", tag);
        other = user("other", tag);
        sellerToken = tokenProvider.createAccessToken(seller.getId(), seller.getRole());
        buyerToken = tokenProvider.createAccessToken(buyer.getId(), buyer.getRole());
        otherToken = tokenProvider.createAccessToken(other.getId(), other.getRole());
        category = categoryRepository.save(Category.create(null, "가구" + tag, null, 1));
        region = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동" + tag, 37.5, 127));
        userRegionRepository.save(UserRegion.verify(seller, region, true));
        imageUrl = imageService.upload(List.of(new MockMultipartFile("files", "photo.jpg", "image/jpeg",
                new byte[]{(byte) 255, (byte) 216, (byte) 255, 0}))).getFirst();
        product = new TransactionTemplate(transactionManager).execute(status -> {
            Product saved = productRepository.save(Product.builder().seller(seller).category(category).region(region)
                    .title("원목 식탁").description("3년 사용했고 상태가 좋습니다.").price(80000).build());
            saved.addImage(imageUrl);
            return saved;
        });
    }

    @AfterEach
    void 파일_정리() throws Exception {
        Files.deleteIfExists(Path.of(imageProperties.uploadDir(), imageUrl.substring(ImageService.URL_PREFIX.length())));
    }

    private User user(String prefix, String tag) {
        return userRepository.save(User.builder().email(prefix + tag + "@example.com").password("hash")
                .nickname(prefix + tag).build());
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private ResultActions notifications(String token) throws Exception {
        return mockMvc.perform(auth(get("/api/notifications"), token)).andExpect(status().isOk());
    }

    private long roomId() throws Exception {
        String body = mockMvc.perform(auth(post("/api/products/{id}/chat-rooms", product.getId()), buyerToken))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.roomId")).longValue();
    }

    private void updatePrice(int price) throws Exception {
        String body = """
                {"title":"원목 식탁","description":"3년 사용했고 상태가 좋습니다.","price":%d,"categoryId":%d,
                 "regionId":%d,"isNegotiable":false,"tradeType":"DIRECT","imageUrls":["%s"]}
                """.formatted(price, category.getId(), region.getId(), imageUrl);
        mockMvc.perform(auth(put("/api/products/{id}", product.getId()), sellerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void 탈퇴한_회원에게는_알림을_저장하지_않는다() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                userRepository.findById(other.getId()).orElseThrow().withdraw(java.time.LocalDateTime.now()));

        notificationService.create(new NotificationRequestedEvent(other.getId(), NotificationType.TRADE,
                "판매자가 예약을 취소했어요", "원목 식탁", "/chat-rooms/1"));

        notifications(otherToken).andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ---------- 찜 ----------

    @Test
    void 찜하면_판매자에게_알림이_가고_안_읽은_찜_알림은_하나로_합친다() throws Exception {
        mockMvc.perform(auth(post("/api/products/{id}/favorite", product.getId()), buyerToken)).andExpect(status().isOk());

        notifications(sellerToken)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].type").value("FAVORITE"))
                .andExpect(jsonPath("$.content[0].title").value("누군가 내 상품을 찜했어요"))
                .andExpect(jsonPath("$.content[0].content").value("원목 식탁"))
                .andExpect(jsonPath("$.content[0].targetUrl").value("/products/" + product.getId()));

        // 찜을 풀었다 다시 누르거나 다른 사람이 찜해도, 읽지 않은 찜 알림이 있으면 쌓지 않는다.
        mockMvc.perform(auth(delete("/api/products/{id}/favorite", product.getId()), buyerToken));
        mockMvc.perform(auth(post("/api/products/{id}/favorite", product.getId()), buyerToken));
        mockMvc.perform(auth(post("/api/products/{id}/favorite", product.getId()), otherToken));
        notifications(sellerToken).andExpect(jsonPath("$.content", hasSize(1)));

        // 읽은 뒤에 새로 찜하면 다시 알린다.
        mockMvc.perform(auth(patch("/api/notifications/read-all"), sellerToken)).andExpect(status().isNoContent());
        mockMvc.perform(auth(delete("/api/products/{id}/favorite", product.getId()), otherToken));
        mockMvc.perform(auth(post("/api/products/{id}/favorite", product.getId()), otherToken));
        notifications(sellerToken)
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].read").value(false));
        // 찜한 사람에게는 알림이 없다.
        notifications(buyerToken).andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ---------- 가격 인하 ----------

    @Test
    void 가격을_내리면_찜한_사람에게만_알리고_올리면_알리지_않는다() throws Exception {
        mockMvc.perform(auth(post("/api/products/{id}/favorite", product.getId()), buyerToken)).andExpect(status().isOk());

        updatePrice(70000);

        notifications(buyerToken)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].type").value("PRICE_DROP"))
                .andExpect(jsonPath("$.content[0].title").value("찜한 상품의 가격이 내려갔어요"))
                .andExpect(jsonPath("$.content[0].content").value("원목 식탁 · 80,000원 → 70,000원"))
                .andExpect(jsonPath("$.content[0].targetUrl").value("/products/" + product.getId()));
        notifications(otherToken).andExpect(jsonPath("$.content", hasSize(0)));

        updatePrice(75000);
        updatePrice(75000);
        notifications(buyerToken).andExpect(jsonPath("$.content", hasSize(1)));

        updatePrice(0);
        notifications(buyerToken)
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].content").value("원목 식탁 · 75,000원 → 나눔"));
    }

    // ---------- 거래 · 후기 ----------

    @Test
    void 예약_취소_거래완료_후기는_상대에게_알림으로_간다() throws Exception {
        long roomId = roomId();
        String roomUrl = "/chat-rooms/" + roomId;

        mockMvc.perform(auth(post("/api/chat-rooms/{id}/reservation", roomId), sellerToken)).andExpect(status().isOk());
        notifications(buyerToken)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].type").value("TRADE"))
                .andExpect(jsonPath("$.content[0].title").value("판매자가 예약했어요"))
                .andExpect(jsonPath("$.content[0].content").value("원목 식탁"))
                .andExpect(jsonPath("$.content[0].targetUrl").value(roomUrl));
        // 버튼을 누른 사람에게는 알림이 없다.
        notifications(sellerToken).andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(auth(delete("/api/chat-rooms/{id}/reservation", roomId), buyerToken)).andExpect(status().isOk());
        notifications(sellerToken)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("구매자가 예약을 취소했어요"))
                .andExpect(jsonPath("$.content[0].targetUrl").value(roomUrl));

        mockMvc.perform(auth(post("/api/chat-rooms/{id}/reservation", roomId), sellerToken)).andExpect(status().isOk());
        String completed = mockMvc.perform(auth(post("/api/chat-rooms/{id}/completion", roomId), sellerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        notifications(buyerToken)
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].title").value("거래가 완료됐어요. 후기를 남겨 주세요"));

        long tradeId = ((Number) JsonPath.read(completed, "$.trade.id")).longValue();
        mockMvc.perform(auth(post("/api/trades/{id}/reviews", tradeId), buyerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\":5}"))
                .andExpect(status().isCreated());
        notifications(sellerToken)
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].title").value("새 후기를 받았어요"))
                .andExpect(jsonPath("$.content[0].content").value(buyer.getNickname() + "님 · 5점"))
                .andExpect(jsonPath("$.content[0].targetUrl").value(NotificationRequestedEvent.MY_REVIEWS_URL));
    }

    // ---------- 트랜잭션 경계 ----------

    @Test
    void 원래_동작이_롤백되면_알림도_만들지_않는다() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            favoriteService.favorite(product.getId(), new AuthUser(buyer.getId(), buyer.getRole()));
            status.setRollbackOnly();
        });

        assertThat(favoriteRepository.existsByUserIdAndProductId(buyer.getId(), product.getId())).isFalse();
        notifications(sellerToken).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void 알림_저장이_실패해도_원래_동작은_성공한다() throws Exception {
        doThrow(new IllegalStateException("알림 저장소 장애")).when(notificationService).create(any());

        mockMvc.perform(auth(post("/api/products/{id}/favorite", product.getId()), buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isLiked").value(true));

        assertThat(favoriteRepository.existsByUserIdAndProductId(buyer.getId(), product.getId())).isTrue();
        notifications(sellerToken).andExpect(jsonPath("$.content", hasSize(0)));
    }
}
