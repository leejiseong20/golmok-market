package com.golmok.market.domain.push;

import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** VAPID 키가 없으면(로컬·테스트 기본값) 푸시만 꺼지고 나머지는 그대로다. 화면은 enabled 를 보고 "알림 받기"를 숨긴다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PushDisabledTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokens;

    @Test
    void 키가_없으면_꺼져_있다고_알리고_구독을_받지_않는다() throws Exception {
        User me = users.save(User.builder().email("nopush@test.com").password("hash").nickname("푸시없음").build());
        String token = tokens.createAccessToken(me.getId(), me.getRole());

        mvc.perform(get("/api/push/public-key").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.publicKey").doesNotExist());
        mvc.perform(post("/api/push/subscriptions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/x\",\"keys\":{\"p256dh\":\""
                                + PushTestKeys.BROWSER_PUBLIC + "\",\"auth\":\"" + PushTestKeys.BROWSER_AUTH + "\"}}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PUSH_DISABLED"));
    }
}
