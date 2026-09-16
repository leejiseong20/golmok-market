package com.golmok.market.domain.user;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.product.Favorite;
import com.golmok.market.domain.product.FavoriteRepository;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
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

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 마이페이지: 내 정보 + 내가 찜한 상품 목록.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MyPageApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired FavoriteRepository favoriteRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired EntityManager em;

    private User me;
    private String myToken;
    private Region region;
    private Category category;
    private User seller;

    @BeforeEach
    void 준비() {
        me = userRepository.save(User.builder().email("me@example.com").password("hash").nickname("골목이").build());
        seller = userRepository.save(User.builder().email("seller@example.com").password("hash").nickname("판매자").build());
        category = categoryRepository.save(Category.create(null, "가구", null, 1));
        region = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동", 37.5006, 127.0366));
        myToken = tokenProvider.createAccessToken(me.getId(), me.getRole());
    }

    private Product product(String title) {
        Product product = productRepository.save(Product.builder().seller(seller).category(category).region(region)
                .title(title).description("상태 좋습니다. 직접 보고 가세요.").price(10000).negotiable(false).build());
        product.addImage("https://example.com/" + title + ".jpg");
        return product;
    }

    private List<Product> favoriteAll(int count) {
        List<Product> products = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Product saved = product("상품" + i);
            products.add(saved);
            favoriteRepository.save(Favorite.of(me, saved));
            em.flush();
            // 찜한 시각이 같은 초에 몰려도 id 로 순서가 갈리는지 함께 확인한다.
        }
        em.clear();
        return products;
    }

    // ---------- 내 정보 ----------

    @Test
    void 내_정보는_닉네임과_매너온도를_준다() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(me.getId()))
                .andExpect(jsonPath("$.nickname").value("골목이"))
                .andExpect(jsonPath("$.mannerTemp").value(36.5))
                .andExpect(jsonPath("$.profileImageUrl").isEmpty());
    }

    @Test
    void 비로그인이면_내_정보는_401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ---------- 찜 목록 ----------

    @Test
    void 찜_목록은_상품_목록과_같은_형식이다() throws Exception {
        favoriteAll(1);

        mockMvc.perform(get("/api/users/me/favorites").header(HttpHeaders.AUTHORIZATION, "Bearer " + myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("상품1"))
                .andExpect(jsonPath("$.content[0].thumbnailUrl").value("https://example.com/상품1.jpg"))
                .andExpect(jsonPath("$.content[0].isLiked").value(true))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void 찜이_없으면_빈_목록이다() throws Exception {
        mockMvc.perform(get("/api/users/me/favorites").header(HttpHeaders.AUTHORIZATION, "Bearer " + myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 남이_찜한_상품은_내_목록에_없다() throws Exception {
        Product other = product("남의관심상품");
        favoriteRepository.save(Favorite.of(seller, other));
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/users/me/favorites").header(HttpHeaders.AUTHORIZATION, "Bearer " + myToken))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void 삭제된_상품은_찜_목록에서_빠진다() throws Exception {
        List<Product> products = favoriteAll(2);
        productRepository.findById(products.get(0).getId()).orElseThrow().softDelete();
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/users/me/favorites").header(HttpHeaders.AUTHORIZATION, "Bearer " + myToken))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("상품2"));
    }

    @Test
    void 최근에_찜한_상품이_먼저_나온다() throws Exception {
        favoriteAll(3);

        mockMvc.perform(get("/api/users/me/favorites").header(HttpHeaders.AUTHORIZATION, "Bearer " + myToken))
                .andExpect(jsonPath("$.content[0].title").value("상품3"))
                .andExpect(jsonPath("$.content[2].title").value("상품1"));
    }

    @Test
    void 커서로_다음_페이지를_이어_받는다() throws Exception {
        favoriteAll(5);

        String cursor = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(get("/api/users/me/favorites").param("size", "2")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + myToken))
                        .andExpect(jsonPath("$.content", hasSize(2)))
                        .andExpect(jsonPath("$.content[0].title").value("상품5"))
                        .andExpect(jsonPath("$.hasNext").value(true))
                        .andReturn().getResponse().getContentAsString(), "$.nextCursor");

        mockMvc.perform(get("/api/users/me/favorites").param("size", "2").param("cursor", cursor)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + myToken))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].title").value("상품3"))
                .andExpect(jsonPath("$.content[1].title").value("상품2"))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void 형식이_틀린_커서는_400() throws Exception {
        mockMvc.perform(get("/api/users/me/favorites").param("cursor", "이상한커서")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + myToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 비로그인_찜_목록은_401() throws Exception {
        mockMvc.perform(get("/api/users/me/favorites"))
                .andExpect(status().isUnauthorized());
    }
}
