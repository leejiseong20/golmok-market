package com.golmok.market.domain.push;

import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 구독 API 와 발송. 실제 푸시 서비스로 보내지 않으려고 PushGateway 만 가짜로 바꾼다.
 * 암호화·서명은 진짜로 돈다 — 가짜 창구가 받은 본문을 브라우저처럼 풀어 내용까지 확인한다.
 */
@SpringBootTest(properties = {
        "app.push.public-key=" + PushTestKeys.VAPID_PUBLIC,
        "app.push.private-key=" + PushTestKeys.VAPID_PRIVATE,
        "app.push.subject=https://golmok-market-frontend.vercel.app"})
@AutoConfigureMockMvc
@Transactional
class PushApiTest {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/device-a";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PushSubscriptionRepository subscriptions;
    @Autowired PushService pushService;
    @Autowired JwtTokenProvider tokens;
    @MockitoBean PushGateway gateway;

    User me, other;
    String myToken, otherToken;

    @BeforeEach
    void 준비() {
        me = users.save(User.builder().email("push-me@test.com").password("hash").nickname("푸시받는사람").build());
        other = users.save(User.builder().email("push-other@test.com").password("hash").nickname("다른사람").build());
        myToken = tokens.createAccessToken(me.getId(), me.getRole());
        otherToken = tokens.createAccessToken(other.getId(), other.getRole());
    }

    private static String body(String endpoint, String p256dh, String auth) {
        return "{\"endpoint\":\"" + endpoint + "\",\"keys\":{\"p256dh\":\"" + p256dh + "\",\"auth\":\"" + auth + "\"}}";
    }

    private ResultActions subscribe(String token, String json) throws Exception {
        return mvc.perform(post("/api/push/subscriptions").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ---------- 구독 ----------

    @Test
    void 길이는_맞아도_곡선_밖의_공개키는_저장하지_않는다() throws Exception {
        byte[] invalid = new byte[65];
        invalid[0] = 4;
        subscribe(myToken, body(ENDPOINT, Base64.getUrlEncoder().withoutPadding().encodeToString(invalid), PushTestKeys.BROWSER_AUTH))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PUSH_SUBSCRIPTION"));
        assertThat(subscriptions.findAll()).isEmpty();
    }

    @Test
    void 한_기기의_발송_예외가_다른_기기를_막지_않는다() throws Exception {
        subscribe(myToken, body(ENDPOINT, PushTestKeys.BROWSER_PUBLIC, PushTestKeys.BROWSER_AUTH));
        String second = ENDPOINT + "-second";
        subscribe(myToken, body(second, PushTestKeys.BROWSER_PUBLIC, PushTestKeys.BROWSER_AUTH));
        when(gateway.send(anyString(), any(), anyString(), any(), any()))
                .thenThrow(new IllegalStateException("첫 기기 실패")).thenReturn(PushGateway.Result.SENT);
        pushService.send(me.getId(), new PushMessage("제목", "내용", "/chat", "test", PushGateway.Urgency.NORMAL));
        verify(gateway).send(eq(ENDPOINT), any(), anyString(), any(), any());
        verify(gateway).send(eq(second), any(), anyString(), any(), any());
    }

    @Test
    void 공개키를_내려준다() throws Exception {
        mvc.perform(get("/api/push/public-key").header("Authorization", "Bearer " + myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.publicKey").value(PushTestKeys.VAPID_PUBLIC));
    }

    @Test
    void 구독을_저장한다() throws Exception {
        subscribe(myToken, body(ENDPOINT, PushTestKeys.BROWSER_PUBLIC, PushTestKeys.BROWSER_AUTH))
                .andExpect(status().isNoContent());
        assertThat(subscriptions.findByEndpoint(ENDPOINT)).hasValueSatisfying(s ->
                assertThat(s.getUser().getId()).isEqualTo(me.getId()));
    }

    /** 같은 브라우저에서 다른 계정으로 로그인해 구독하면 주인이 바뀐다. 이전 사람의 알림이 이 기기로 오면 안 된다. */
    @Test
    void 같은_기기를_다른_계정이_구독하면_주인이_바뀐다() throws Exception {
        subscribe(myToken, body(ENDPOINT, PushTestKeys.BROWSER_PUBLIC, PushTestKeys.BROWSER_AUTH));
        subscribe(otherToken, body(ENDPOINT, PushTestKeys.BROWSER_PUBLIC, PushTestKeys.BROWSER_AUTH))
                .andExpect(status().isNoContent());

        assertThat(subscriptions.findAll()).hasSize(1);
        assertThat(subscriptions.findByEndpoint(ENDPOINT).orElseThrow().getUser().getId()).isEqualTo(other.getId());
    }

    /** 허용 목록이 없으면 서버가 아무 주소로나 POST 를 보낸다(SSRF). 클라우드 메타데이터 주소가 대표적이다. */
    @Test
    void 푸시_서비스가_아닌_주소는_거부한다() throws Exception {
        for (String endpoint : new String[]{
                "http://169.254.169.254/latest/meta-data",
                "http://fcm.googleapis.com/fcm/send/x",        // https 아님
                "https://evil.example.com/fcm/send/x",
                "https://fcm.googleapis.com.evil.com/x",        // 뒤에 붙인 가짜 호스트
                "https://fcm.googleapis.com:8443/x",            // 다른 포트
                "https://user@fcm.googleapis.com/x"}) {         // 사용자 정보로 호스트 흐리기
            subscribe(myToken, body(endpoint, PushTestKeys.BROWSER_PUBLIC, PushTestKeys.BROWSER_AUTH))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PUSH_SUBSCRIPTION"));
        }
        assertThat(subscriptions.findAll()).isEmpty();
    }

    @Test
    void 실제_브라우저가_쓰는_푸시_서비스는_받는다() throws Exception {
        for (String endpoint : new String[]{
                "https://updates.push.services.mozilla.com/wpush/v2/x",
                "https://web.push.apple.com/QGz1",
                "https://wns2-par02p.notify.windows.com/w/?token=x"}) {
            subscribe(myToken, body(endpoint, PushTestKeys.BROWSER_PUBLIC, PushTestKeys.BROWSER_AUTH))
                    .andExpect(status().isNoContent());
        }
    }

    /** 틀린 키로 저장하면 보낼 때마다 암호화가 실패한다. 저장 전에 막는다. */
    @Test
    void 형식이_틀린_키는_거부한다() throws Exception {
        subscribe(myToken, body(ENDPOINT, "AAAA", PushTestKeys.BROWSER_AUTH))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PUSH_SUBSCRIPTION"));
        subscribe(myToken, body(ENDPOINT, PushTestKeys.BROWSER_PUBLIC, "AAAA"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PUSH_SUBSCRIPTION"));
        subscribe(myToken, "{\"endpoint\":\"" + ENDPOINT + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].reason").value("구독 키가 없습니다."));
    }

    @Test
    void 구독을_지우면_이_기기로_보내지_않는다() throws Exception {
        subscribe(myToken, body(ENDPOINT, PushTestKeys.BROWSER_PUBLIC, PushTestKeys.BROWSER_AUTH));
        mvc.perform(delete("/api/push/subscriptions").header("Authorization", "Bearer " + myToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + ENDPOINT + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(subscriptions.findAll()).isEmpty();
    }

    /** 남의 기기 주소를 알아도 그 사람의 구독은 지울 수 없다(조용히 무시한다). */
    @Test
    void 남의_구독은_지울_수_없다() throws Exception {
        subscribe(myToken, body(ENDPOINT, PushTestKeys.BROWSER_PUBLIC, PushTestKeys.BROWSER_AUTH));
        mvc.perform(delete("/api/push/subscriptions").header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + ENDPOINT + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(subscriptions.findAll()).hasSize(1);
    }

    @Test
    void 로그인하지_않으면_구독할_수_없다() throws Exception {
        mvc.perform(post("/api/push/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content(body(ENDPOINT, PushTestKeys.BROWSER_PUBLIC, PushTestKeys.BROWSER_AUTH)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 발송 ----------

    /** 가짜 창구가 받은 본문을 브라우저처럼 풀어, 서비스 워커가 읽을 JSON 이 그대로 나오는지 본다. */
    @Test
    void 보내면_그_사람_기기로_암호화된_알림이_간다() throws Exception {
        subscribe(myToken, body(ENDPOINT, PushTestKeys.BROWSER_PUBLIC, PushTestKeys.BROWSER_AUTH));
        when(gateway.send(anyString(), any(), anyString(), any(), any())).thenReturn(PushGateway.Result.SENT);

        pushService.send(me.getId(), new PushMessage("이웃", "아직 판매하시나요?", "/chat-rooms/7", "chat-room-7",
                PushGateway.Urgency.HIGH));

        ArgumentCaptor<byte[]> sent = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> authorization = ArgumentCaptor.forClass(String.class);
        verify(gateway).send(eq(ENDPOINT), sent.capture(), authorization.capture(),
                eq(Duration.ofHours(24)), eq(PushGateway.Urgency.HIGH));
        assertThat(new String(PushTestKeys.decryptAsBrowser(sent.getValue()), StandardCharsets.UTF_8))
                .isEqualTo("{\"title\":\"이웃\",\"body\":\"아직 판매하시나요?\",\"url\":\"/chat-rooms/7\",\"tag\":\"chat-room-7\"}");
        assertThat(authorization.getValue()).startsWith("vapid t=").endsWith(", k=" + PushTestKeys.VAPID_PUBLIC);
    }

    /** 사용자가 알림 권한을 끄거나 앱을 지우면 푸시 서비스가 410 을 준다. 계속 보내지 않게 지운다. */
    @Test
    void 없어진_구독은_지운다() throws Exception {
        subscribe(myToken, body(ENDPOINT, PushTestKeys.BROWSER_PUBLIC, PushTestKeys.BROWSER_AUTH));
        when(gateway.send(anyString(), any(), anyString(), any(), any())).thenReturn(PushGateway.Result.GONE);

        pushService.send(me.getId(), new PushMessage("t", "b", "/", "x", PushGateway.Urgency.NORMAL));

        assertThat(subscriptions.findAll()).isEmpty();
    }

    /** 일시적인 실패(5xx·연결 끊김)로 구독을 지우면, 다시 알림 받기를 켜기 전까지 영영 못 받는다. */
    @Test
    void 일시적인_실패는_구독을_남긴다() throws Exception {
        subscribe(myToken, body(ENDPOINT, PushTestKeys.BROWSER_PUBLIC, PushTestKeys.BROWSER_AUTH));
        when(gateway.send(anyString(), any(), anyString(), any(), any())).thenReturn(PushGateway.Result.FAILED);

        pushService.send(me.getId(), new PushMessage("t", "b", "/", "x", PushGateway.Urgency.NORMAL));

        assertThat(subscriptions.findAll()).hasSize(1);
    }

    @Test
    void 구독이_없으면_보내지_않는다() {
        pushService.send(me.getId(), new PushMessage("t", "b", "/", "x", PushGateway.Urgency.NORMAL));
        verify(gateway, never()).send(anyString(), any(), anyString(), any(), any());
    }
}
