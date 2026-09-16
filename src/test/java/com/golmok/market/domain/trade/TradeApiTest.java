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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 구매내역 조회와 구매확정.
 * 거래 생성 API 는 아직 없으므로 엔티티로 직접 거래를 만들어 상황을 구성한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TradeApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired TradeRepository tradeRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired EntityManager em;

    private User seller;
    private User buyer;
    private String buyerToken;
    private String otherToken;
    private Category category;
    private Region region;

    @BeforeEach
    void 준비() {
        seller = userRepository.save(User.builder().email("seller@example.com").password("hash").nickname("판매자").build());
        buyer = userRepository.save(User.builder().email("buyer@example.com").password("hash").nickname("구매자").build());
        User other = userRepository.save(User.builder().email("other@example.com").password("hash").nickname("남").build());
        category = categoryRepository.save(Category.create(null, "가구", null, 1));
        region = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동", 37.5006, 127.0366));
        buyerToken = tokenProvider.createAccessToken(buyer.getId(), buyer.getRole());
        otherToken = tokenProvider.createAccessToken(other.getId(), other.getRole());
    }

    private Product product(String title) {
        Product product = productRepository.save(Product.builder().seller(seller).category(category).region(region)
                .title(title).description("상태 좋습니다. 직접 보고 가세요.").price(50000).negotiable(false).build());
        product.addImage("https://example.com/" + title + ".jpg");
        return product;
    }

    /** REQUESTED 상태의 거래를 만든다. 상품은 예약중이 된다. */
    private Trade trade(String title) {
        Trade trade = tradeRepository.save(Trade.request(product(title), null, buyer));
        em.flush();
        return trade;
    }

    private Trade paidTrade(String title) {
        Trade trade = trade(title);
        trade.markPaid();
        em.flush();
        return trade;
    }

    // ---------- 구매내역 ----------

    @Test
    void 판매자가_먼저_판매완료해도_구매확정은_가능하다() throws Exception {
        Trade trade = paidTrade("수동 판매완료");
        trade.getProduct().markSold();
        em.flush(); em.clear();
        mockMvc.perform(patch("/api/trades/{id}/confirm", trade.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.product.status").value("SOLD"));
    }

    @Test
    void 구매내역은_거래와_상품_판매자_정보를_준다() throws Exception {
        Trade trade = trade("원목 식탁");
        em.clear();

        mockMvc.perform(get("/api/users/me/purchases").header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].tradeId").value(trade.getId()))
                .andExpect(jsonPath("$.content[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.content[0].amount").value(50000))
                .andExpect(jsonPath("$.content[0].product.title").value("원목 식탁"))
                .andExpect(jsonPath("$.content[0].product.thumbnailUrl").value("https://example.com/원목 식탁.jpg"))
                .andExpect(jsonPath("$.content[0].product.deleted").value(false))
                .andExpect(jsonPath("$.content[0].seller.nickname").value("판매자"))
                .andExpect(jsonPath("$.content[0].completedAt").isEmpty());
    }

    @Test
    void 결제_전에는_구매확정을_할_수_없다() throws Exception {
        trade("원목 식탁");
        em.clear();

        mockMvc.perform(get("/api/users/me/purchases").header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(jsonPath("$.content[0].canConfirm").value(false));
    }

    @Test
    void 결제가_끝나면_구매확정을_할_수_있다() throws Exception {
        paidTrade("원목 식탁");
        em.clear();

        mockMvc.perform(get("/api/users/me/purchases").header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(jsonPath("$.content[0].canConfirm").value(true));
    }

    @Test
    void 남의_구매내역은_보이지_않는다() throws Exception {
        trade("원목 식탁");
        em.clear();

        mockMvc.perform(get("/api/users/me/purchases").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void 삭제된_상품의_구매내역도_남는다() throws Exception {
        Trade trade = trade("원목 식탁");
        productRepository.findById(trade.getProduct().getId()).orElseThrow().softDelete();
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/users/me/purchases").header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].product.deleted").value(true));
    }

    @Test
    void 최근_거래가_먼저_나오고_커서로_이어받는다() throws Exception {
        trade("상품1");
        trade("상품2");
        trade("상품3");
        em.clear();

        String cursor = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(get("/api/users/me/purchases").param("size", "2")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                        .andExpect(jsonPath("$.content", hasSize(2)))
                        .andExpect(jsonPath("$.content[0].product.title").value("상품3"))
                        .andExpect(jsonPath("$.hasNext").value(true))
                        .andReturn().getResponse().getContentAsString(), "$.nextCursor");

        mockMvc.perform(get("/api/users/me/purchases").param("size", "2").param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].product.title").value("상품1"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 비로그인_구매내역은_401() throws Exception {
        mockMvc.perform(get("/api/users/me/purchases"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 구매확정 ----------

    @Test
    void 구매확정하면_거래가_확정되고_상품이_판매완료가_된다() throws Exception {
        Trade trade = paidTrade("원목 식탁");
        long productId = trade.getProduct().getId();
        em.clear();

        mockMvc.perform(patch("/api/trades/{id}/confirm", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.canConfirm").value(false))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.product.status").value("SOLD"));

        // flush 없이 clear 하면 아직 반영되지 않은 변경이 버려져 DB 까지 갔는지 확인할 수 없다.
        em.flush();
        em.clear();
        assertThat(productRepository.findById(productId).orElseThrow().getStatus()).isEqualTo(ProductStatus.SOLD);
    }

    @Test
    void 결제_전_거래는_구매확정에서_400() throws Exception {
        Trade trade = trade("원목 식탁");
        em.clear();

        mockMvc.perform(patch("/api/trades/{id}/confirm", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void 이미_확정한_거래는_다시_확정할_수_없다() throws Exception {
        Trade trade = paidTrade("원목 식탁");
        trade.confirm();
        em.flush();
        em.clear();

        mockMvc.perform(patch("/api/trades/{id}/confirm", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void 남의_거래는_확정할_수_없고_존재_여부도_알려주지_않는다() throws Exception {
        Trade trade = paidTrade("원목 식탁");
        em.clear();

        mockMvc.perform(patch("/api/trades/{id}/confirm", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRADE_NOT_FOUND"));
    }

    @Test
    void 없는_거래를_확정하면_404() throws Exception {
        mockMvc.perform(patch("/api/trades/{id}/confirm", 999999)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRADE_NOT_FOUND"));
    }

    @Test
    void 비로그인_구매확정은_401() throws Exception {
        Trade trade = paidTrade("원목 식탁");
        em.clear();

        mockMvc.perform(patch("/api/trades/{id}/confirm", trade.getId()))
                .andExpect(status().isUnauthorized());
    }
}
