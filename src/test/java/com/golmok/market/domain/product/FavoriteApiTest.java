package com.golmok.market.domain.product;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FavoriteApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired FavoriteRepository favoriteRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired EntityManager em;

    private Product product;
    private User seller;
    private String buyerToken;
    private String sellerToken;

    @BeforeEach
    void 준비() {
        seller = userRepository.save(User.builder().email("seller@example.com").password("hash").nickname("판매자").build());
        User buyer = userRepository.save(User.builder().email("buyer@example.com").password("hash").nickname("구매자").build());
        Category category = categoryRepository.save(Category.create(null, "가구", null, 1));
        Region region = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동", 37.5006, 127.0366));
        product = productRepository.save(Product.builder().seller(seller).category(category).region(region)
                .title("원목 식탁").description("3년 사용했고 상태가 좋습니다.").price(80000).negotiable(true).build());
        product.addImage("https://example.com/first.jpg");
        em.flush();
        em.clear();
        buyerToken = tokenProvider.createAccessToken(buyer.getId(), buyer.getRole());
        sellerToken = tokenProvider.createAccessToken(seller.getId(), seller.getRole());
    }

    private MockHttpServletRequestBuilder favorite(String token) {
        return post("/api/products/{id}/favorite", product.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder unfavorite(String token) {
        return delete("/api/products/{id}/favorite", product.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    // ---------- 찜하기 ----------

    @Test
    void 찜하면_isLiked_와_증가한_찜_수를_돌려준다() throws Exception {
        mockMvc.perform(favorite(buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isLiked").value(true))
                .andExpect(jsonPath("$.favoriteCount").value(1));
    }

    @Test
    void 이미_찜한_상품을_다시_찜하면_409() throws Exception {
        mockMvc.perform(favorite(buyerToken)).andExpect(status().isOk());

        mockMvc.perform(favorite(buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_FAVORITED"));
    }

    @Test
    void 자기_상품은_찜할_수_없다() throws Exception {
        mockMvc.perform(favorite(sellerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_FAVORITE_OWN_PRODUCT"));
    }

    @Test
    void 비로그인_찜은_401() throws Exception {
        mockMvc.perform(post("/api/products/{id}/favorite", product.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 없는_상품을_찜하면_404() throws Exception {
        mockMvc.perform(post("/api/products/{id}/favorite", 999999)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 삭제된_상품은_찜할_수_없다() throws Exception {
        productRepository.findById(product.getId()).orElseThrow().softDelete();
        em.flush();

        mockMvc.perform(favorite(buyerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    // ---------- 찜 해제 ----------

    @Test
    void 찜을_해제하면_찜_수가_줄어든다() throws Exception {
        mockMvc.perform(favorite(buyerToken)).andExpect(jsonPath("$.favoriteCount").value(1));

        mockMvc.perform(unfavorite(buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isLiked").value(false))
                .andExpect(jsonPath("$.favoriteCount").value(0));
    }

    @Test
    void 찜하지_않은_상품의_해제는_에러_없이_현재_상태를_돌려준다() throws Exception {
        mockMvc.perform(unfavorite(buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isLiked").value(false))
                .andExpect(jsonPath("$.favoriteCount").value(0));
    }

    @Test
    void 찜_수는_0_미만으로_내려가지_않는다() throws Exception {
        mockMvc.perform(favorite(buyerToken));
        mockMvc.perform(unfavorite(buyerToken));

        mockMvc.perform(unfavorite(buyerToken))
                .andExpect(jsonPath("$.favoriteCount").value(0));
    }

    // ---------- 목록 반영 ----------

    @Test
    void 찜하면_상품_목록의_isLiked_와_찜_수에_반영된다() throws Exception {
        mockMvc.perform(favorite(buyerToken));

        mockMvc.perform(get("/api/products").param("regionId", String.valueOf(product.getRegion().getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(jsonPath("$.content[0].isLiked").value(true))
                .andExpect(jsonPath("$.content[0].favoriteCount").value(1));
    }

    @Test
    void 다른_사람이_찜해도_내_목록의_isLiked_는_false_다() throws Exception {
        mockMvc.perform(favorite(buyerToken));

        mockMvc.perform(get("/api/products").param("regionId", String.valueOf(product.getRegion().getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(jsonPath("$.content[0].isLiked").value(false))
                .andExpect(jsonPath("$.content[0].favoriteCount").value(1));
    }
}
