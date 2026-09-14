package com.golmok.market.domain.auth;

import com.golmok.market.domain.user.UserRole;
import com.golmok.market.global.security.JwtProperties;
import com.golmok.market.global.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP → Security 필터 → 컨트롤러 → 서비스 전체 흐름을 검증한다.
 * 서비스 단위 규칙은 AuthServiceTest 에서, 여기서는 "필터와 응답 형식"을 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProperties jwtProperties;

    private static final String SIGNUP_BODY = """
            { "email": "user@example.com", "password": "Password123!", "nickname": "골목이", "phone": "01012345678" }
            """;
    private static final String LOGIN_BODY = """
            { "email": "user@example.com", "password": "Password123!" }
            """;

    private String loginAndGetBody() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY))
                .andExpect(status().isCreated());
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ---------- 정상 흐름 ----------

    @Test
    void 가입_로그인_응답이_명세_형식과_같다() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.nickname").value("골목이"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.user.nickname").value("골목이"))
                .andExpect(jsonPath("$.user.profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.user.primaryRegion").isEmpty());
    }

    @Test
    void 만료된_access_token_을_달고도_재발급은_필터에_막히지_않는다() throws Exception {
        String refreshToken = JsonPath.read(loginAndGetBody(), "$.refreshToken");

        mockMvc.perform(post("/api/auth/reissue")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"refreshToken\": \"" + refreshToken + "\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString());
    }

    @Test
    void 로그아웃은_토큰이_있으면_204() throws Exception {
        String body = loginAndGetBody();
        String accessToken = JsonPath.read(body, "$.accessToken");
        String refreshToken = JsonPath.read(body, "$.refreshToken");

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"refreshToken\": \"" + refreshToken + "\" }"))
                .andExpect(status().isNoContent());
    }

    @Test
    void 중복_확인은_비로그인으로_호출할_수_있다() throws Exception {
        mockMvc.perform(get("/api/auth/check-email").param("email", "new@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    // ---------- 401 / 403 형식 ----------

    @Test
    void 토큰_없이_보호된_경로에_접근하면_401_UNAUTHORIZED() throws Exception {
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{ \"refreshToken\": \"x\" }"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void 만료된_토큰은_401_EXPIRED_TOKEN() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EXPIRED_TOKEN"));
    }

    @Test
    void 위조된_토큰은_공개_경로에서도_401_INVALID_TOKEN() throws Exception {
        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void me_경로는_공개_GET_패턴보다_먼저_막힌다() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ---------- 입력 검증 ----------

    @Test
    void 한글이_섞인_비밀번호는_BCrypt_에_닿기_전에_400() throws Exception {
        String body = """
                { "email": "user@example.com", "password": "비밀번호가길어요1234!", "nickname": "골목이" }
                """;

        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void 중복_가입은_409() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    // ---------- CORS ----------

    @Test
    void 프론트_개발_서버의_preflight_요청을_허용한다() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void 허용하지_않은_출처의_preflight_는_거부한다() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    /** 같은 키로, 1시간 전 시각 기준으로 발급한 토큰 → 30분 만료가 이미 지났다 */
    private String expiredAccessToken() {
        Clock anHourAgo = Clock.fixed(Instant.now().minus(1, ChronoUnit.HOURS), ZoneId.of("Asia/Seoul"));
        return new JwtTokenProvider(jwtProperties, anHourAgo).createAccessToken(1L, UserRole.USER);
    }
}
