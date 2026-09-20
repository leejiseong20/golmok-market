package com.golmok.market.domain.product;

import com.golmok.market.domain.category.*;
import com.golmok.market.domain.region.*;
import com.golmok.market.domain.user.*;
import com.golmok.market.domain.image.*;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductWriteApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired UserRegionRepository userRegions;
    @Autowired RegionRepository regions;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ImageService images;
    @Autowired ImageProperties imageProperties;
    @Autowired JwtTokenProvider tokens;
    @Autowired EntityManager em;
    User seller;
    Category category;
    Region region;
    String token, otherToken, url;

    @BeforeEach void 준비() {
        seller = users.save(User.builder().email("write@test.com").password("hash").nickname("등록판매자").build());
        User other = users.save(User.builder().email("other@test.com").password("hash").nickname("다른판매자").build());
        token = tokens.createAccessToken(seller.getId(), seller.getRole());
        otherToken = tokens.createAccessToken(other.getId(), other.getRole());
        category = categories.save(Category.create(null, "등록가구", null, 1));
        region = regions.save(Region.create("서울", "강남", "역삼동", 37.5, 127.0));
        userRegions.save(UserRegion.verify(seller, region, true));
        url = images.upload(List.of(new MockMultipartFile("files", "photo.jpg", "image/jpeg",
                new byte[]{(byte)255, (byte)216, (byte)255, 0}))).getFirst();
    }

    @AfterEach void 파일정리() throws Exception {
        if (url != null) Files.deleteIfExists(Path.of(imageProperties.uploadDir(), url.substring(ImageService.URL_PREFIX.length())));
    }

    Map<String, Object> body() {
        return new HashMap<>(Map.of("title", "등록하는 책상", "description", "상태가 좋은 원목 책상을 판매합니다.",
                "price", 20000, "categoryId", category.getId(), "regionId", region.getId(),
                "isNegotiable", true, "tradeType", "DIRECT", "imageUrls", List.of(url)));
    }

    Product saved() {
        Product product = products.save(Product.builder().seller(seller).region(region).category(category)
                .title("기존 책상").description("상태가 좋은 책상입니다.").price(10000).build());
        product.addImage(url);
        em.flush();
        return product;
    }

    @Test void 등록은_이미지와_인증동네를_포함한_상세를_반환한다() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.regionId").value(region.getId()))
                .andExpect(jsonPath("$.images[0].id").isNumber()).andExpect(jsonPath("$.images[0].imageUrl").value(url))
                .andExpect(jsonPath("$.isMine").value(true)).andExpect(jsonPath("$.viewCount").value(0))
                .andExpect(jsonPath("$.status").value("ON_SALE"));
    }

    @Test void 미인증_동네_등록은_403이다() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REGION_NOT_VERIFIED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/a.jpg", "/api/images/../secret", "/api/images/%2e%2e/secret", "/api/images/2026/09/16/00000000-0000-0000-0000-000000000000.jpg"})
    void 외부_경로조작_없는파일은_거부한다(String bad) throws Exception {
        var body = body(); body.put("imageUrls", List.of(bad));
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IMAGE_URL"));
        assertThat(products.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "description", "price", "categoryId", "regionId", "isNegotiable", "tradeType", "imageUrls"})
    void 필수값_누락은_필드오류를_준다(String field) throws Exception {
        var body = body(); body.remove(field);
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
    }

    @Test void 입력_길이와_이미지개수와_가격을_검증한다() throws Exception {
        var body = body(); body.put("title", "한"); body.put("description", "짧음");
        body.put("price", -1); body.put("imageUrls", Collections.nCopies(11, url));
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors", hasSize(4)));
    }

    /**
     * 검증 문구는 그대로 사용자 화면에 뜬다(프론트는 서버가 준 message 를 그대로 보여 준다).
     * 기본 문구("크기가 10에서 2147483647 사이여야 합니다")가 새어 나가면 읽는 사람이 무엇을 고칠지 모른다.
     */
    @Test void 검증_문구는_사람이_읽을_수_있어야_한다() throws Exception {
        var body = body(); body.put("description", "짧음");
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"))
                .andExpect(jsonPath("$.errors[0].reason").value("설명은 10자 이상 입력해 주세요."));
    }

    @Test void 수정은_인증동네와_이미지를_교체하고_카운터를_보존한다() throws Exception {
        Product p = saved();
        Region another = regions.save(Region.create("서울", "마포", "서교동", 37.5, 126.9));
        userRegions.save(UserRegion.verify(seller, another, false));
        products.incrementViewCount(p.getId(), null);
        products.incrementFavoriteCount(p.getId());
        var body = body(); body.put("regionId", another.getId());
        mvc.perform(put("/api/products/{id}", p.getId()).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.regionId").value(another.getId()))
                .andExpect(jsonPath("$.images", hasSize(1))).andExpect(jsonPath("$.viewCount").value(1))
                .andExpect(jsonPath("$.favoriteCount").value(1));
        em.flush(); em.clear();
        assertThat(products.findById(p.getId()).orElseThrow().getFavoriteCount()).isEqualTo(1);
        assertThat(Files.exists(Path.of(imageProperties.uploadDir(), url.substring(ImageService.URL_PREFIX.length())))).isTrue();
    }

    @Test void 남의상품_쓰기와_미로그인은_거부한다() throws Exception {
        long id = saved().getId();
        mvc.perform(put("/api/products/{id}", id).header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body())))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/{id}", id).header("Authorization", "Bearer " + otherToken)).andExpect(status().isForbidden());
        mvc.perform(post("/api/products/{id}/bump", id).header("Authorization", "Bearer " + otherToken)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/products/{id}/status", id).header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SOLD\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/products/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body())))
                .andExpect(status().isUnauthorized());
    }

    /** 상태를 빠뜨리면 기본 문구("널이어서는 안됩니다")가 아니라 사람이 읽을 문장이 나가야 한다. */
    @Test void 상태를_빠뜨리면_사람이_읽을_문구를_준다() throws Exception {
        long id = saved().getId();
        mvc.perform(patch("/api/products/{id}/status", id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].reason").value("변경할 상태를 선택해 주세요."));
    }

    @Test void 삭제는_물리행과_사진을_남기고_조회에서는_숨긴다() throws Exception {
        long id = saved().getId();
        mvc.perform(delete("/api/products/{id}", id).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        em.flush(); em.clear();
        assertThat(products.findById(id).orElseThrow().isDeleted()).isTrue();
        mvc.perform(get("/api/products/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/me").header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(delete("/api/products/{id}", id).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
    }

    @Test void 판매완료는_수정과_끌어올리기와_역전이를_거부한다() throws Exception {
        long id = saved().getId();
        for (String state : List.of("RESERVED", "ON_SALE", "SOLD")) {
            mvc.perform(patch("/api/products/{id}/status", id).header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + state + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(state));
        }
        mvc.perform(put("/api/products/{id}", id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body()))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/products/{id}/bump", id).header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/products/{id}/status", id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ON_SALE\"}")).andExpect(status().isBadRequest());
    }

    @Test void 판매내역은_동일시각도_id로_페이징하고_상태를_필터한다() throws Exception {
        var a = saved(); var b = saved(); b.reserve();
        em.flush();
        em.createQuery("update Product p set p.createdAt = :time").setParameter("time", LocalDateTime.of(2026, 9, 16, 12, 0)).executeUpdate();
        em.clear();
        String response = mvc.perform(get("/api/products/me").param("size", "1").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.hasNext").value(true)).andExpect(jsonPath("$.content[0].id").value(b.getId()))
                .andReturn().getResponse().getContentAsString();
        String cursor = json.readTree(response).get("nextCursor").asText();
        mvc.perform(get("/api/products/me").param("size", "1").param("cursor", cursor).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.hasNext").value(false)).andExpect(jsonPath("$.content[0].id").value(a.getId()));
        mvc.perform(get("/api/products/me").param("status", "RESERVED").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].id").value(b.getId()));
        mvc.perform(get("/api/products/me").header("Authorization", "Bearer " + otherToken)).andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(get("/api/products/me").param("status", "BAD").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/me").param("cursor", "bad").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
    }

    @Test void 정확히_24시간부터_끌어올리고_직후에는_막는다() {
        var now = LocalDateTime.of(2026, 9, 16, 12, 0);
        var p = Product.builder().seller(seller).category(category).region(region).title("시간 테스트")
                .description("끌어올리기 경계를 확인합니다.").bumpedAt(now.minusHours(24)).build();
        assertThat(p.canBump(now.minusNanos(1))).isFalse();
        assertThat(p.canBump(now)).isTrue();
        p.bump(now);
        assertThat(p.getBumpedAt()).isEqualTo(now);
        assertThatThrownBy(() -> p.bump(now)).isInstanceOf(BusinessException.class);
    }

    @Test void 끌어올리기_API는_갱신시각을_반환하고_재요청을_거부한다() throws Exception {
        var p = products.save(Product.builder().seller(seller).region(region).category(category).title("오래된 상품")
                .description("끌어올리기 가능한 상품입니다.").bumpedAt(LocalDateTime.now().minusDays(2)).build());
        mvc.perform(post("/api/products/{id}/bump", p.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bumpedAt").isString());
        mvc.perform(post("/api/products/{id}/bump", p.getId()).header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
    }
}
