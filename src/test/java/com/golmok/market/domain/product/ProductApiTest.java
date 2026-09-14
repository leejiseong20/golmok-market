package com.golmok.market.domain.product;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.security.JwtProperties;
import com.golmok.market.global.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductApiTest {

    private static final String DATE_PATTERN = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}";

    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository productRepository;
    @Autowired FavoriteRepository favoriteRepository;
    @Autowired UserRepository userRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired JwtProperties jwtProperties;
    @Autowired EntityManager em;

    private Product product;
    private User seller;
    private User buyer;
    private Region region;
    private Category category;

    @BeforeEach
    void 준비() {
        seller = userRepository.save(User.builder().email("seller@example.com").password("test-hash").nickname("판매자").build());
        buyer = userRepository.save(User.builder().email("buyer@example.com").password("test-hash").nickname("구매자").build());
        category = categoryRepository.save(Category.create(null, "가구", null, 1));
        region = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동", 37.5006, 127.0366));
        product = productRepository.save(Product.builder().seller(seller).category(category).region(region)
                .title("원목 식탁").description("3년 사용했고 상태가 좋습니다.").price(80000).negotiable(true).build());
        product.addImage("https://example.com/first.jpg");
        product.addImage("https://example.com/second.jpg");
        product.increaseFavoriteCount();
        product.increaseChatCount();
        product.increaseChatCount();
        favoriteRepository.save(Favorite.of(buyer, product));
        em.flush();
        em.clear();
    }

    @Test
    void 비로그인_상품_목록은_명세의_필드와_커서_응답을_반환한다() throws Exception {
        mockMvc.perform(list())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0]", aMapWithSize(14)))
                .andExpect(jsonPath("$.content[0].id").value(product.getId()))
                .andExpect(jsonPath("$.content[0].title").value("원목 식탁"))
                .andExpect(jsonPath("$.content[0].price").value(80000))
                .andExpect(jsonPath("$.content[0].categoryId").value(category.getId()))
                .andExpect(jsonPath("$.content[0].categoryName").value("가구"))
                .andExpect(jsonPath("$.content[0].regionName").value("역삼동"))
                .andExpect(jsonPath("$.content[0].sellerNickname").value("판매자"))
                .andExpect(jsonPath("$.content[0].thumbnailUrl").value("https://example.com/first.jpg"))
                .andExpect(jsonPath("$.content[0].status").value("ON_SALE"))
                .andExpect(jsonPath("$.content[0].favoriteCount").value(1))
                .andExpect(jsonPath("$.content[0].chatCount").value(2))
                .andExpect(jsonPath("$.content[0].isLiked").value(false))
                .andExpect(jsonPath("$.content[0].createdAt", matchesPattern(DATE_PATTERN)))
                .andExpect(jsonPath("$.content[0].bumpedAt", matchesPattern(DATE_PATTERN)))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    void 비로그인_상품_상세는_명세_필드와_증가한_조회수를_반환한다() throws Exception {
        mockMvc.perform(detail())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(18)))
                .andExpect(jsonPath("$.id").value(product.getId()))
                .andExpect(jsonPath("$.title").value("원목 식탁"))
                .andExpect(jsonPath("$.description").value("3년 사용했고 상태가 좋습니다."))
                .andExpect(jsonPath("$.price").value(80000))
                .andExpect(jsonPath("$.isNegotiable").value(true))
                .andExpect(jsonPath("$.status").value("ON_SALE"))
                .andExpect(jsonPath("$.tradeType").value("DIRECT"))
                .andExpect(jsonPath("$.categoryId").value(category.getId()))
                .andExpect(jsonPath("$.categoryName").value("가구"))
                .andExpect(jsonPath("$.regionName").value("역삼동"))
                .andExpect(jsonPath("$.images", hasSize(2)))
                .andExpect(jsonPath("$.images[0]", aMapWithSize(3)))
                .andExpect(jsonPath("$.images[0].id").isNumber())
                .andExpect(jsonPath("$.images[0].imageUrl").value("https://example.com/first.jpg"))
                .andExpect(jsonPath("$.images[0].sortOrder").value(0))
                .andExpect(jsonPath("$.images[1].imageUrl").value("https://example.com/second.jpg"))
                .andExpect(jsonPath("$.seller", aMapWithSize(4)))
                .andExpect(jsonPath("$.seller.id").value(seller.getId()))
                .andExpect(jsonPath("$.seller.nickname").value("판매자"))
                .andExpect(jsonPath("$.seller.profileImageUrl", nullValue()))
                .andExpect(jsonPath("$.seller.mannerTemp").value(36.5))
                .andExpect(jsonPath("$.viewCount").value(1))
                .andExpect(jsonPath("$.favoriteCount").value(1))
                .andExpect(jsonPath("$.chatCount").value(2))
                .andExpect(jsonPath("$.isLiked").value(false))
                .andExpect(jsonPath("$.isMine").value(false))
                .andExpect(jsonPath("$.createdAt", matchesPattern(DATE_PATTERN)));
        mockMvc.perform(detail()).andExpect(status().isOk()).andExpect(jsonPath("$.viewCount").value(2));
    }

    @Test
    void 로그인_사용자에_따라_찜과_본인_여부를_반영한다() throws Exception {
        mockMvc.perform(list().header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].isLiked").value(true));
        mockMvc.perform(detail().header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isMine").value(true))
                .andExpect(jsonPath("$.isLiked").value(false)).andExpect(jsonPath("$.viewCount").value(0));
        mockMvc.perform(detail().header(HttpHeaders.AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isMine").value(false))
                .andExpect(jsonPath("$.isLiked").value(true)).andExpect(jsonPath("$.viewCount").value(1));
    }

    @Test
    void 반환받은_커서로_다음_상품을_조회한다() throws Exception {
        Product second = productRepository.save(Product.builder().seller(seller).category(category).region(region)
                .title("두번째 상품").description("커서로 조회하는 두번째 상품").price(90000).build());
        em.flush();
        em.clear();
        String body = mockMvc.perform(list().param("sort", "PRICE_ASC").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.content[0].id").value(product.getId()))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(body, "$.nextCursor");

        mockMvc.perform(list().param("sort", "PRICE_ASC").param("size", "1").param("cursor", cursor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(second.getId()))
                .andExpect(jsonPath("$.hasNext").value(false)).andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    void 페이지_크기는_기본20_최대50으로_보정한다() throws Exception {
        for (int i = 0; i < 50; i++) {
            productRepository.save(Product.builder().seller(seller).category(category).region(region)
                    .title("상품" + i).description("페이지 크기를 확인할 상품").price(i).build());
        }
        em.flush();
        em.clear();
        mockMvc.perform(list()).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(20)));
        for (String size : new String[]{"0", "-1"}) {
            mockMvc.perform(list().param("size", size)).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(20)));
        }
        mockMvc.perform(list().param("size", "99")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(50))).andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void 존재하지_않는_필터는_404가_아닌_빈_목록이다() throws Exception {
        mockMvc.perform(get("/api/products").param("regionId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.hasNext").value(false)).andExpect(jsonPath("$.nextCursor", nullValue()));
        mockMvc.perform(list().param("categoryId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void 삭제된_상품은_목록에서_제외하고_상세는_404이다() throws Exception {
        productRepository.findById(product.getId()).orElseThrow().softDelete();
        em.flush();
        em.clear();
        mockMvc.perform(list()).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(detail()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("상품을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.timestamp").isString()).andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void 없는_상품의_상세도_같은_404이다() throws Exception {
        mockMvc.perform(get("/api/products/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 동네_ID를_생략하면_field가_있는_400이다() throws Exception {
        assertInvalid(get("/api/products"), "regionId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808", ""})
    void 잘못된_동네_ID는_400이다(String id) throws Exception {
        assertInvalid(get("/api/products").param("regionId", id), "regionId");
    }

    @ParameterizedTest
    @CsvSource({"categoryId, 0", "categoryId, -1", "categoryId, abc", "sort, WRONG", "size, abc", "size, 2147483648"})
    void 잘못된_선택_파라미터는_field가_있는_400이다(String field, String value) throws Exception {
        assertInvalid(list().param(field, value), field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void 잘못된_상품_ID는_400이다(String id) throws Exception {
        assertInvalid(get("/api/products/{id}", id), "id");
    }

    @Test
    void 검색어는_공백을_제거하고_50자까지_허용한다() throws Exception {
        mockMvc.perform(list().param("keyword", "  원목  ")).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)));
        mockMvc.perform(list().param("keyword", "   ")).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)));
        mockMvc.perform(list().param("keyword", "  " + "가".repeat(50) + "  ")).andExpect(status().isOk());
        assertInvalid(list().param("keyword", "가".repeat(51)), "keyword");
    }

    @ParameterizedTest
    @CsvSource({"LATEST, broken", "LATEST, 100_1", "LATEST, 2026-02-30T12:00:00_1", "LATEST, 0999-01-01T00:00:00_1",
            "PRICE_ASC, -1_1", "PRICE_ASC, 2147483648_1", "PRICE_ASC, 100_0", "PRICE_ASC, 100_bad"})
    void 정렬별로_잘못된_커서는_공통_400_응답이다(String sort, String cursor) throws Exception {
        // Cursor 의 기존 BusinessException 형식은 필드별 errors 없이 코드와 메시지를 반환한다.
        mockMvc.perform(list().param("sort", sort).param("cursor", cursor))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").isString()).andExpect(jsonPath("$.timestamp").isString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"목록", "상세"})
    void 공개_조회에도_위조_토큰은_401이다(String target) throws Exception {
        mockMvc.perform((target.equals("목록") ? list() : detail())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"목록", "상세"})
    void 공개_조회에도_만료_토큰은_401이다(String target) throws Exception {
        Clock past = Clock.fixed(Instant.now().minus(1, ChronoUnit.HOURS), ZoneId.of("Asia/Seoul"));
        String token = new JwtTokenProvider(jwtProperties, past).createAccessToken(buyer.getId(), buyer.getRole());
        mockMvc.perform((target.equals("목록") ? list() : detail()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("EXPIRED_TOKEN"));
    }

    private MockHttpServletRequestBuilder list() {
        return get("/api/products").param("regionId", region.getId().toString());
    }

    private MockHttpServletRequestBuilder detail() {
        return get("/api/products/{id}", product.getId());
    }

    private String bearer(User user) {
        return "Bearer " + tokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    private void assertInvalid(MockHttpServletRequestBuilder request, String field) throws Exception {
        mockMvc.perform(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)))
                .andExpect(jsonPath("$.errors[0].reason").isNotEmpty());
    }
}
