package com.golmok.market.domain.region;

import com.golmok.market.domain.user.UserRole;
import com.golmok.market.global.security.JwtProperties;
import com.golmok.market.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegionApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired RegionRepository regionRepository;
    @Autowired JwtProperties jwtProperties;

    @Test
    void 동네_검색은_비로그인으로_명세의_배열을_반환한다() throws Exception {
        Region region = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동", 37.5006, 127.0366));

        mockMvc.perform(get("/api/regions").param("keyword", "  강남구 역삼  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0]", aMapWithSize(5)))
                .andExpect(jsonPath("$[0].id").value(region.getId()))
                .andExpect(jsonPath("$[0].sido").value("서울특별시"))
                .andExpect(jsonPath("$[0].sigungu").value("강남구"))
                .andExpect(jsonPath("$[0].dong").value("역삼동"))
                .andExpect(jsonPath("$[0].fullName").value("서울특별시 강남구 역삼동"));
    }

    @Test
    void 근처_조회는_비로그인으로_가까운_순서의_동네_배열을_반환한다() throws Exception {
        regionRepository.save(Region.create("서울특별시", "마포구", "서교동", 37.5520, 126.9180));
        regionRepository.save(Region.create("서울특별시", "성동구", "성수동", 37.5446, 127.0559));
        Region nearest = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동", 37.5006, 127.0366));

        mockMvc.perform(nearby())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0]", aMapWithSize(5)))
                .andExpect(jsonPath("$[0].id").value(nearest.getId()))
                .andExpect(jsonPath("$[0].sido").value("서울특별시"))
                .andExpect(jsonPath("$[0].sigungu").value("강남구"))
                .andExpect(jsonPath("$[0].dong").value("역삼동"))
                .andExpect(jsonPath("$[0].fullName").value("서울특별시 강남구 역삼동"))
                .andExpect(jsonPath("$[1].dong").value("성수동"))
                .andExpect(jsonPath("$[2].dong").value("서교동"));
    }

    @Test
    void 근처_응답은_10개를_넘지_않는다() throws Exception {
        for (int i = 0; i < 12; i++) {
            regionRepository.save(Region.create("서울특별시", "강남구", "동" + i, 37.5006, 127.0366));
        }

        mockMvc.perform(nearby()).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    void 일치하는_데이터가_없으면_두_조회_모두_빈_배열이다() throws Exception {
        mockMvc.perform(get("/api/regions").param("keyword", "역삼"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(nearby()).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 검색어를_생략하면_field가_포함된_400이다() throws Exception {
        assertInvalid(get("/api/regions"), "keyword");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void 빈_검색어와_공백_검색어는_400이다(String keyword) throws Exception {
        assertInvalid(get("/api/regions").param("keyword", keyword), "keyword");
    }

    @Test
    void 공백_제거_후_82자는_허용하고_83자는_거부한다() throws Exception {
        mockMvc.perform(get("/api/regions").param("keyword", "  " + "가".repeat(82) + "  "))
                .andExpect(status().isOk());
        assertInvalid(get("/api/regions").param("keyword", "가".repeat(83)), "keyword");
    }

    @ParameterizedTest
    @ValueSource(strings = {"lat", "lng"})
    void 좌표를_생략하면_해당_field가_포함된_400이다(String missing) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/regions/nearby");
        request.param(missing.equals("lat") ? "lng" : "lat", "0");
        assertInvalid(request, missing);
    }

    @ParameterizedTest
    @CsvSource({
            "lat, -90.001", "lat, 90.001", "lng, -180.001", "lng, 180.001",
            "lat, abc", "lng, abc", "lat, NaN", "lng, NaN",
            "lat, Infinity", "lng, Infinity", "lat, -Infinity", "lng, -Infinity",
            "lat, 1e309", "lng, 1e309", "lat, ''", "lng, ''"
    })
    void 좌표_범위_초과와_비정상_숫자는_field가_포함된_400이다(String field, String value) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/regions/nearby")
                .param("lat", field.equals("lat") ? value : "37.5")
                .param("lng", field.equals("lng") ? value : "127");
        assertInvalid(request, field);
    }

    @ParameterizedTest
    @CsvSource({"-90, -180", "90, 180", "0, 0"})
    void 좌표_경계값과_영점은_허용한다(String lat, String lng) throws Exception {
        mockMvc.perform(get("/api/regions/nearby").param("lat", lat).param("lng", lng))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/regions", "/api/regions/nearby"})
    void 공개_조회에도_위조_토큰은_401이다(String path) throws Exception {
        mockMvc.perform(get(path).param("keyword", "역삼").param("lat", "37.5").param("lng", "127")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/regions", "/api/regions/nearby"})
    void 공개_조회에도_만료_토큰은_401이다(String path) throws Exception {
        Clock past = Clock.fixed(Instant.now().minus(1, ChronoUnit.HOURS), ZoneId.of("Asia/Seoul"));
        String token = new JwtTokenProvider(jwtProperties, past).createAccessToken(1L, UserRole.USER);

        mockMvc.perform(get(path).param("keyword", "역삼").param("lat", "37.5").param("lng", "127")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EXPIRED_TOKEN"));
    }

    private MockHttpServletRequestBuilder nearby() {
        return get("/api/regions/nearby").param("lat", "37.5006").param("lng", "127.0366");
    }

    private void assertInvalid(MockHttpServletRequestBuilder request, String field) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)))
                .andExpect(jsonPath("$.errors[0].reason").isNotEmpty());
    }
}
