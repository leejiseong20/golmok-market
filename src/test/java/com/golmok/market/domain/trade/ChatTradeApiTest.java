package com.golmok.market.domain.trade;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.product.ProductStatus;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 채팅방 직거래: 예약 · 예약 취소 · 거래완료와 상품·구매내역 연동. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatTradeApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired EntityManager em;

    private Product product;
    private String sellerToken;
    private String buyerToken;
    private String otherBuyerToken;
    private long roomId;

    @BeforeEach
    void 준비() throws Exception {
        User seller = userRepository.save(User.builder().email("seller@example.com").password("hash").nickname("판매자").build());
        User buyer = userRepository.save(User.builder().email("buyer@example.com").password("hash").nickname("구매자").build());
        User otherBuyer = userRepository.save(User.builder().email("other@example.com").password("hash").nickname("다른구매자").build());
        Category category = categoryRepository.save(Category.create(null, "가구", null, 1));
        Region region = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동", 37.5006, 127.0366));
        product = productRepository.save(Product.builder().seller(seller).category(category).region(region)
                .title("원목 식탁").description("3년 사용했고 상태가 좋습니다.").price(80000).build());
        em.flush();
        em.clear();
        sellerToken = tokenProvider.createAccessToken(seller.getId(), seller.getRole());
        buyerToken = tokenProvider.createAccessToken(buyer.getId(), buyer.getRole());
        otherBuyerToken = tokenProvider.createAccessToken(otherBuyer.getId(), otherBuyer.getRole());
        roomId = openRoom(buyerToken);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private long openRoom(String token) throws Exception {
        String body = mockMvc.perform(auth(post("/api/products/{id}/chat-rooms", product.getId()), token))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.roomId")).longValue();
    }

    private ResultActions reserve(long room, String token) throws Exception {
        return mockMvc.perform(auth(post("/api/chat-rooms/{id}/reservation", room), token));
    }

    private ResultActions cancel(long room, String token) throws Exception {
        return mockMvc.perform(auth(delete("/api/chat-rooms/{id}/reservation", room), token));
    }

    private ResultActions complete(long room, String token) throws Exception {
        return mockMvc.perform(auth(post("/api/chat-rooms/{id}/completion", room), token));
    }

    private ProductStatus productStatus() {
        em.flush();
        em.clear();
        return productRepository.findById(product.getId()).orElseThrow().getStatus();
    }

    // ---------- 방 정보 ----------

    @Test
    void 거래가_없는_방은_판매자에게만_예약_버튼이_열린다() throws Exception {
        mockMvc.perform(auth(get("/api/chat-rooms/{id}", roomId), sellerToken))
                .andExpect(jsonPath("$.trade", nullValue()))
                .andExpect(jsonPath("$.tradeActions.reserve").value(true))
                .andExpect(jsonPath("$.tradeActions.cancel").value(false))
                .andExpect(jsonPath("$.tradeActions.complete").value(false));
        mockMvc.perform(auth(get("/api/chat-rooms/{id}", roomId), buyerToken))
                .andExpect(jsonPath("$.tradeActions.reserve").value(false))
                .andExpect(jsonPath("$.tradeActions.cancel").value(false))
                .andExpect(jsonPath("$.tradeActions.complete").value(false));
    }

    // ---------- 예약 ----------

    @Test
    void 판매자가_예약하면_거래와_상품이_예약중이_되고_시스템_메시지가_남는다() throws Exception {
        reserve(roomId, sellerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId))
                .andExpect(jsonPath("$.trade.id").isNumber())
                .andExpect(jsonPath("$.trade.status").value("REQUESTED"))
                .andExpect(jsonPath("$.trade.amount").value(80000))
                .andExpect(jsonPath("$.product.status").value("RESERVED"))
                .andExpect(jsonPath("$.tradeActions.reserve").value(false))
                .andExpect(jsonPath("$.tradeActions.cancel").value(true))
                .andExpect(jsonPath("$.tradeActions.complete").value(true));

        assertThat(productStatus()).isEqualTo(ProductStatus.RESERVED);
        mockMvc.perform(auth(get("/api/chat-rooms/{id}/messages", roomId), buyerToken))
                .andExpect(jsonPath("$.content[0].type").value("SYSTEM"))
                .andExpect(jsonPath("$.content[0].content").value("판매자가 예약했어요."));
        // 구매자에게는 취소만 열린다. 시스템 메시지로 메시지 없는 방도 목록에 나타난다.
        mockMvc.perform(auth(get("/api/chat-rooms/{id}", roomId), buyerToken))
                .andExpect(jsonPath("$.tradeActions.cancel").value(true))
                .andExpect(jsonPath("$.tradeActions.complete").value(false));
        mockMvc.perform(auth(get("/api/chat-rooms"), buyerToken))
                .andExpect(jsonPath("$.content[0].lastMessage").value("판매자가 예약했어요."));
        // 구매내역에 예약이 생기지만, 결제가 없으므로 구매확정 버튼은 열리지 않는다.
        mockMvc.perform(auth(get("/api/users/me/purchases"), buyerToken))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.content[0].canConfirm").value(false));
    }

    @Test
    void 구매자는_예약도_거래완료도_할_수_없다() throws Exception {
        reserve(roomId, buyerToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ONLY"));
        reserve(roomId, sellerToken).andExpect(status().isOk());
        complete(roomId, buyerToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ONLY"));
    }

    @Test
    void 다른_방에서_이미_예약된_상품은_예약할_수_없다() throws Exception {
        long otherRoom = openRoom(otherBuyerToken);
        reserve(roomId, sellerToken).andExpect(status().isOk());

        mockMvc.perform(auth(get("/api/chat-rooms/{id}", otherRoom), sellerToken))
                .andExpect(jsonPath("$.trade", nullValue()))
                .andExpect(jsonPath("$.tradeActions.reserve").value(false));
        reserve(otherRoom, sellerToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void 참여자가_아니면_거래_버튼도_404() throws Exception {
        reserve(roomId, otherBuyerToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_ROOM_NOT_FOUND"));
        reserve(999999, sellerToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_ROOM_NOT_FOUND"));
    }

    // ---------- 취소 ----------

    @Test
    void 구매자가_예약을_취소하면_판매중으로_돌아오고_다시_예약할_수_있다() throws Exception {
        reserve(roomId, sellerToken).andExpect(status().isOk());

        cancel(roomId, buyerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trade", nullValue()))
                .andExpect(jsonPath("$.product.status").value("ON_SALE"))
                .andExpect(jsonPath("$.tradeActions.cancel").value(false));
        assertThat(productStatus()).isEqualTo(ProductStatus.ON_SALE);
        mockMvc.perform(auth(get("/api/chat-rooms/{id}/messages", roomId), sellerToken))
                .andExpect(jsonPath("$.content[0].content").value("구매자가 예약을 취소했어요."));

        // 취소 뒤에는 판매자에게 다시 예약 버튼이 열리고, 새 거래가 만들어진다.
        mockMvc.perform(auth(get("/api/chat-rooms/{id}", roomId), sellerToken))
                .andExpect(jsonPath("$.tradeActions.reserve").value(true));
        reserve(roomId, sellerToken).andExpect(status().isOk());
        mockMvc.perform(auth(get("/api/users/me/purchases"), buyerToken))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.content[1].status").value("CANCELED"));
    }

    @Test
    void 판매자도_예약을_취소할_수_있다() throws Exception {
        reserve(roomId, sellerToken).andExpect(status().isOk());

        cancel(roomId, sellerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeActions.reserve").value(true));
        mockMvc.perform(auth(get("/api/chat-rooms/{id}/messages", roomId), buyerToken))
                .andExpect(jsonPath("$.content[0].content").value("판매자가 예약을 취소했어요."));
    }

    @Test
    void 예약이_없으면_취소도_거래완료도_400() throws Exception {
        cancel(roomId, sellerToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_RESERVATION"));
        complete(roomId, sellerToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_RESERVATION"));
    }

    // ---------- 거래완료 ----------

    @Test
    void 판매자가_거래완료하면_거래는_확정_상품은_판매완료가_된다() throws Exception {
        reserve(roomId, sellerToken).andExpect(status().isOk());

        complete(roomId, sellerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trade.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.trade.completedAt").isString())
                .andExpect(jsonPath("$.product.status").value("SOLD"))
                .andExpect(jsonPath("$.tradeActions.reserve").value(false))
                .andExpect(jsonPath("$.tradeActions.cancel").value(false))
                .andExpect(jsonPath("$.tradeActions.complete").value(false));

        assertThat(productStatus()).isEqualTo(ProductStatus.SOLD);
        mockMvc.perform(auth(get("/api/chat-rooms/{id}/messages", roomId), buyerToken))
                .andExpect(jsonPath("$.content[0].content").value("거래가 완료됐어요."));
        mockMvc.perform(auth(get("/api/users/me/purchases"), buyerToken))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.content[0].completedAt").isString())
                .andExpect(jsonPath("$.content[0].canConfirm").value(false));
        // 완료된 거래는 다시 취소할 수 없다.
        cancel(roomId, buyerToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_RESERVATION"));
    }

    @Test
    void 예약된_거래는_구매자_구매확정_API로_확정할_수_없다() throws Exception {
        String body = reserve(roomId, sellerToken).andReturn().getResponse().getContentAsString();
        long tradeId = ((Number) JsonPath.read(body, "$.trade.id")).longValue();

        mockMvc.perform(auth(patch("/api/trades/{id}/confirm", tradeId), buyerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    // ---------- 상품 쪽 차단 ----------

    @Test
    void 진행_중인_예약이_있으면_상품_상태_변경과_삭제는_409_이고_취소하면_다시_된다() throws Exception {
        reserve(roomId, sellerToken).andExpect(status().isOk());

        mockMvc.perform(auth(patch("/api/products/{id}/status", product.getId()), sellerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ON_SALE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRADE_IN_PROGRESS"));
        mockMvc.perform(auth(delete("/api/products/{id}", product.getId()), sellerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRADE_IN_PROGRESS"));

        cancel(roomId, sellerToken).andExpect(status().isOk());
        mockMvc.perform(auth(patch("/api/products/{id}/status", product.getId()), sellerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESERVED\"}"))
                .andExpect(status().isOk());
    }
}
