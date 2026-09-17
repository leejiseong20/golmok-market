package com.golmok.market.domain.trade;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.chat.ChatRoom;
import com.golmok.market.domain.chat.ChatRoomRepository;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewApiTest {

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired RegionRepository regions;
    @Autowired TradeRepository trades;
    @Autowired ReviewRepository reviews;
    @Autowired ChatRoomRepository rooms;
    @Autowired JwtTokenProvider tokens;
    private User seller;
    private User buyer;
    private User outsider;
    private Trade trade;
    private ChatRoom room;

    @BeforeEach
    void 준비() {
        seller = users.save(User.builder().email("review-s@example.test").password("hash").nickname("판매자").build());
        buyer = users.save(User.builder().email("review-b@example.test").password("hash").nickname("구매자").build());
        outsider = users.save(User.builder().email("review-o@example.test").password("hash").nickname("외부인").build());
        Category category = categories.save(Category.create(null, "가구", null, 1));
        Region region = regions.save(Region.create("서울", "강남구", "역삼동", 37.5, 127));
        Product product = products.save(Product.builder().seller(seller).category(category).region(region)
                .title("후기 확인 상품").description("후기를 검증합니다.").price(100).build());
        room = rooms.save(ChatRoom.open(product, buyer));
        trade = trades.save(Trade.request(product, room, buyer));
        trade.completeInPerson();
        em.flush();
        em.clear();
    }

    @Test
    void 양쪽이_각각_작성하고_온도와_공개_프로필에_반영된다() throws Exception {
        write(buyer, "{\"score\":5,\"content\":\"  친절해요  \"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.content").value("친절해요"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty()).andExpect(jsonPath("$.tradeId").doesNotExist());
        write(seller, "{\"score\":1}").andExpect(status().isCreated());
        em.flush();
        em.clear();
        mvc.perform(get("/api/users/{id}", seller.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.mannerTemp").value(36.9)).andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.productCount").value(1)).andExpect(jsonPath("$.email").doesNotExist());
        mvc.perform(get("/api/users/{id}/reviews", seller.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].score").value(5))
                .andExpect(jsonPath("$.content[0].reviewer.nickname").value("구매자"))
                .andExpect(jsonPath("$.content[0].tradeId").doesNotExist());
        assertThat(users.findById(buyer.getId()).orElseThrow().getMannerTemp()).isEqualByComparingTo("36.1");
    }

    @Test
    void 중복은_409이고_온도를_한번만_반영한다() throws Exception {
        write(buyer, "{\"score\":5}").andExpect(status().isCreated());
        write(buyer, "{\"score\":1}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REVIEWED"));
        em.clear();
        assertThat(reviews.countByRevieweeId(seller.getId())).isEqualTo(1);
        assertThat(users.findById(seller.getId()).orElseThrow().getMannerTemp()).isEqualByComparingTo("36.9");
    }

    @Test
    void 비로그인과_타인과_없는_거래는_거부한다() throws Exception {
        mvc.perform(post("/api/trades/{id}/reviews", trade.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":5}")).andExpect(status().isUnauthorized());
        write(outsider, "{\"score\":5}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRADE_NOT_FOUND"));
        mvc.perform(post("/api/trades/{id}/reviews", Long.MAX_VALUE).header("Authorization", token(buyer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"score\":5}"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"REQUESTED", "PAID", "SHIPPING", "CANCELED", "REFUNDED"})
    void 완료_외의_상태는_409다(String state) throws Exception {
        em.createQuery("update Trade t set t.status = :status where t.id = :id")
                .setParameter("status", TradeStatus.valueOf(state)).setParameter("id", trade.getId()).executeUpdate();
        em.clear();
        write(buyer, "{\"score\":5}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"score\":null}", "{\"score\":0}", "{\"score\":6}"})
    void 잘못된_점수는_필드_오류로_돌려준다(String body) throws Exception {
        write(buyer, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("score"));
    }

    @Test
    void 본문은_500자까지이고_공백만_있으면_비운다() throws Exception {
        write(buyer, "{\"score\":3,\"content\":\"" + "가".repeat(501) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("content"));
        write(buyer, "{\"score\":3,\"content\":\"" + "가".repeat(500) + "\"}")
                .andExpect(status().isCreated());
        write(seller, "{\"score\":3,\"content\":\"   \"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void 작성_전후_채팅과_구매내역의_작성_버튼이_바뀐다() throws Exception {
        checkActions(true);
        write(buyer, "{\"score\":5}").andExpect(status().isCreated());
        checkActions(false);
        mvc.perform(get("/api/chat-rooms/{id}", room.getId()).header("Authorization", token(seller)))
                .andExpect(jsonPath("$.tradeActions.review").value(true));
    }

    @Test
    void 삭제된_상품과_나간_채팅방도_거래_후기는_쓸_수_있다() throws Exception {
        products.findById(trade.getProduct().getId()).orElseThrow().softDelete();
        rooms.findById(room.getId()).orElseThrow().leave(buyer.getId());
        em.flush();
        em.clear();
        write(buyer, "{\"score\":4}").andExpect(status().isCreated());
        mvc.perform(get("/api/users/{id}", seller.getId())).andExpect(jsonPath("$.productCount").value(0));
    }

    @Test
    void 공개_조회도_없는_사용자와_잘못된_커서를_검증한다() throws Exception {
        mvc.perform(get("/api/users/{id}/reviews", Long.MAX_VALUE)).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/{id}/reviews?cursor=bad", seller.getId())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/{id}/reviews?size=0", seller.getId())).andExpect(status().isOk());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    private void checkActions(boolean allowed) throws Exception {
        mvc.perform(get("/api/chat-rooms/{id}", room.getId()).header("Authorization", token(buyer)))
                .andExpect(jsonPath("$.tradeActions.review").value(allowed));
        mvc.perform(get("/api/users/me/purchases").header("Authorization", token(buyer)))
                .andExpect(jsonPath("$.content[0].canReview").value(allowed));
    }

    private ResultActions write(User viewer, String body) throws Exception {
        return mvc.perform(post("/api/trades/{id}/reviews", trade.getId()).header("Authorization", token(viewer))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String token(User user) {
        return "Bearer " + tokens.createAccessToken(user.getId(), user.getRole());
    }
}
