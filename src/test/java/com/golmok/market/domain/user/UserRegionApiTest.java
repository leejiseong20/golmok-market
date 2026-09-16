package com.golmok.market.domain.user;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserRegionApiTest {

    // 역삼동 좌표. 세 동네 중 이 좌표에서 가장 가까운 곳이 역삼동이다.
    private static final String YEOKSAM = "{\"lat\": 37.5006, \"lng\": 127.0366}";
    private static final String SEOGYO = "{\"lat\": 37.5520, \"lng\": 126.9180}";
    private static final String SEONGSU = "{\"lat\": 37.5446, \"lng\": 127.0559}";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired EntityManager em;

    private String token;
    private Region yeoksam;
    private Region seogyo;

    @BeforeEach
    void 준비() {
        User user = userRepository.save(User.builder().email("me@example.com").password("hash").nickname("골목이").build());
        token = tokenProvider.createAccessToken(user.getId(), user.getRole());
        yeoksam = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동", 37.5006, 127.0366));
        seogyo = regionRepository.save(Region.create("서울특별시", "마포구", "서교동", 37.5520, 126.9180));
        regionRepository.save(Region.create("서울특별시", "성동구", "성수동", 37.5446, 127.0559));
        em.flush();
        em.clear();
    }

    private MockHttpServletRequestBuilder verify(String body) {
        return post("/api/users/me/regions").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ---------- 인증 ----------

    @Test
    void 좌표로_가장_가까운_동네가_인증되고_첫_동네는_대표가_된다() throws Exception {
        mockMvc.perform(verify(YEOKSAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(yeoksam.getId()))
                .andExpect(jsonPath("$[0].name").value("역삼동"))
                .andExpect(jsonPath("$[0].isPrimary").value(true))
                .andExpect(jsonPath("$[0].verifyCount").value(1));
    }

    @Test
    void 같은_동네를_다시_인증하면_행이_늘지_않고_인증_횟수만_올라간다() throws Exception {
        mockMvc.perform(verify(YEOKSAM));
        mockMvc.perform(verify(YEOKSAM))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].verifyCount").value(2));
    }

    @Test
    void 두_번째_동네는_대표가_아니다() throws Exception {
        mockMvc.perform(verify(YEOKSAM));

        mockMvc.perform(verify(SEOGYO))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("역삼동"))
                .andExpect(jsonPath("$[0].isPrimary").value(true))
                .andExpect(jsonPath("$[1].name").value("서교동"))
                .andExpect(jsonPath("$[1].isPrimary").value(false));
    }

    @Test
    void 세_번째_동네는_409() throws Exception {
        mockMvc.perform(verify(YEOKSAM));
        mockMvc.perform(verify(SEOGYO));

        mockMvc.perform(verify(SEONGSU))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGION_LIMIT_EXCEEDED"));
    }

    @Test
    void 이미_2개여도_기존_동네_재인증은_허용된다() throws Exception {
        mockMvc.perform(verify(YEOKSAM));
        mockMvc.perform(verify(SEOGYO));

        mockMvc.perform(verify(YEOKSAM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verifyCount").value(2));
    }

    @Test
    void 좌표_범위를_벗어나면_400() throws Exception {
        mockMvc.perform(verify("{\"lat\": 91, \"lng\": 127}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("lat"));
    }

    @Test
    void 좌표가_없으면_400() throws Exception {
        mockMvc.perform(verify("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void 비로그인_인증은_401() throws Exception {
        mockMvc.perform(post("/api/users/me/regions").contentType(MediaType.APPLICATION_JSON).content(YEOKSAM))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 대표 지정 ----------

    @Test
    void 대표_동네를_바꾸면_기존_대표는_해제된다() throws Exception {
        mockMvc.perform(verify(YEOKSAM));
        mockMvc.perform(verify(SEOGYO));

        mockMvc.perform(patch("/api/users/me/regions/{id}/primary", seogyo.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("서교동"))
                .andExpect(jsonPath("$[0].isPrimary").value(true))
                .andExpect(jsonPath("$[1].isPrimary").value(false));
    }

    @Test
    void 인증하지_않은_동네를_대표로_지정하면_404() throws Exception {
        mockMvc.perform(verify(YEOKSAM));

        mockMvc.perform(patch("/api/users/me/regions/{id}/primary", seogyo.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_REGION_NOT_FOUND"));
    }

    // ---------- 삭제 ----------

    @Test
    void 대표_동네를_삭제하면_남은_동네가_대표가_된다() throws Exception {
        mockMvc.perform(verify(YEOKSAM));
        mockMvc.perform(verify(SEOGYO));

        mockMvc.perform(delete("/api/users/me/regions/{id}", yeoksam.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("서교동"))
                .andExpect(jsonPath("$[0].isPrimary").value(true));
    }

    @Test
    void 마지막_동네를_삭제하면_빈_목록이_된다() throws Exception {
        mockMvc.perform(verify(YEOKSAM));

        mockMvc.perform(delete("/api/users/me/regions/{id}", yeoksam.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 인증하지_않은_동네_삭제는_404() throws Exception {
        mockMvc.perform(delete("/api/users/me/regions/{id}", seogyo.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_REGION_NOT_FOUND"));
    }

    // ---------- 내 정보 ----------

    @Test
    void 내_정보에_인증한_동네가_포함된다() throws Exception {
        mockMvc.perform(verify(YEOKSAM));

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.regions", hasSize(1)))
                .andExpect(jsonPath("$.regions[0].name").value("역삼동"))
                .andExpect(jsonPath("$.regions[0].isPrimary").value(true));
    }

    @Test
    void 인증_전에는_동네가_빈_배열이다() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.regions", hasSize(0)));
    }
}
