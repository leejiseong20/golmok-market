package com.golmok.market.domain.clienterror;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 화면 오류 보고 API. 로그인 없이 받고, 한 줄 로그로 남기며, 요청 수를 제한한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ClientErrorApiTest {

    private static final String VALID = """
            { "boundary": "본문", "kind": "RENDER", "message": "Cannot read properties of null (reading 'id')",
              "path": "/admin/users", "componentStack": "\\n    at AdminUsers\\n    at AdminApp" }
            """;

    @Autowired MockMvc mvc;
    @Autowired ClientErrorRateLimiter rateLimiter;

    @BeforeEach
    void 준비() {
        rateLimiter.reset();
    }

    private ResultActions send(String body, String ip) throws Exception {
        return mvc.perform(post("/api/client-errors")
                .header("X-Forwarded-For", ip)
                .header("User-Agent", "Mozilla/5.0 테스트")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 로그인_없이_받고_한_줄_로그로_남긴다(CapturedOutput output) throws Exception {
        send(VALID, "203.0.113.5").andExpect(status().isNoContent());

        String line = output.getAll().lines().filter((text) -> text.contains("[client-error]"))
                .findFirst().orElseThrow();
        assertThat(line).contains("kind=RENDER", "boundary=본문", "path=/admin/users",
                "message=Cannot read properties of null (reading 'id')", "userAgent=Mozilla/5.0 테스트",
                "stack=at AdminUsers at AdminApp");
    }

    @Test
    void 메시지의_줄바꿈으로_가짜_로그_줄을_만들_수_없다(CapturedOutput output) throws Exception {
        send("""
                { "boundary": "본문", "kind": "RENDER", "path": "/",
                  "message": "boom\\nINFO AuthService : 로그인 성공 forged@test.com" }
                """, "203.0.113.6").andExpect(status().isNoContent());

        // 가짜 문장은 [client-error] 줄 안에만 있어야 한다(따로 한 줄이 되면 안 된다).
        assertThat(output.getAll().lines().filter((text) -> text.contains("forged@test.com")))
                .isNotEmpty()
                .allMatch((text) -> text.contains("[client-error]"));
    }

    @Test
    void 경로에_쿼리가_있으면_받지_않는다() throws Exception {
        // 쿼리에는 검색어가 들어 있을 수 있다. 화면이 잘라 보내지만 서버도 거절한다.
        send("""
                { "boundary": "본문", "kind": "RENDER", "message": "boom", "path": "/?q=내 검색어" }
                """, "203.0.113.7")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("path"));
    }

    @Test
    void 필수값이_없거나_너무_길면_400() throws Exception {
        send("{ \"boundary\": \"본문\", \"kind\": \"RENDER\", \"path\": \"/\" }", "203.0.113.8")
                .andExpect(status().isBadRequest());
        send("{ \"boundary\": \"본문\", \"kind\": \"RENDER\", \"path\": \"/\", \"message\": \"" + "x".repeat(301) + "\" }",
                "203.0.113.8")
                .andExpect(status().isBadRequest());
        send("{ \"boundary\": \"본문\", \"kind\": \"NOPE\", \"path\": \"/\", \"message\": \"boom\" }", "203.0.113.8")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 같은_IP_에서_너무_많이_보내면_429() throws Exception {
        for (int i = 0; i < ClientErrorRateLimiter.PER_IP_LIMIT; i++) {
            send(VALID, "203.0.113.9").andExpect(status().isNoContent());
        }
        send(VALID, "203.0.113.9")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_CLIENT_ERRORS"));
        // 앞쪽 X-Forwarded-For 는 클라이언트가 지어낼 수 있어 믿지 않는다 — 맨 뒤 값이 같으면 같은 IP 다.
        send(VALID, "1.2.3.4, 203.0.113.9").andExpect(status().isTooManyRequests());
        send(VALID, "198.51.100.1").andExpect(status().isNoContent());
    }
}
