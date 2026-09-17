package com.golmok.market.domain.notification;

import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 알림함 조회·읽음. 알림은 저장소로 직접 만든다(생성 흐름은 NotificationFlowTest). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired EntityManager em;

    private User owner;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void 준비() {
        owner = userRepository.save(User.builder().email("owner@example.com").password("hash").nickname("주인").build());
        User other = userRepository.save(User.builder().email("other@example.com").password("hash").nickname("남").build());
        ownerToken = tokenProvider.createAccessToken(owner.getId(), owner.getRole());
        otherToken = tokenProvider.createAccessToken(other.getId(), other.getRole());
    }

    private Notification notification(User user, String title) {
        return notificationRepository.save(Notification.of(user, NotificationType.TRADE, title, "원목 식탁", "/chat-rooms/7"));
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    @Test
    void 내_알림은_최신순이고_남의_알림은_보이지_않는다() throws Exception {
        notification(owner, "첫 번째");
        notification(owner, "두 번째");
        notification(userRepository.findByEmail("other@example.com").orElseThrow(), "남의 알림");
        em.flush();

        mockMvc.perform(auth(get("/api/notifications"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].title").value("두 번째"))
                .andExpect(jsonPath("$.content[0].type").value("TRADE"))
                .andExpect(jsonPath("$.content[0].content").value("원목 식탁"))
                .andExpect(jsonPath("$.content[0].targetUrl").value("/chat-rooms/7"))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.content[0].createdAt").isString())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 커서로_이어_불러온다() throws Exception {
        for (int i = 1; i <= 3; i++) {
            notification(owner, "알림 " + i);
        }
        // 메모리의 엔티티는 DB 가 반올림해 저장하기 전의 더 정밀한 시각을 들고 있다. 실제 요청처럼 DB 값으로 커서를 만들게 비운다.
        em.flush();
        em.clear();

        String first = mockMvc.perform(auth(get("/api/notifications").param("size", "2"), ownerToken))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(auth(get("/api/notifications").param("size", "2")
                        .param("cursor", (String) JsonPath.read(first, "$.nextCursor")), ownerToken))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("알림 1"))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    void 안_읽은_수와_하나_읽음_모두_읽음() throws Exception {
        Notification first = notification(owner, "첫 번째");
        notification(owner, "두 번째");
        em.flush();

        mockMvc.perform(auth(get("/api/notifications/unread-count"), ownerToken))
                .andExpect(jsonPath("$.count").value(2));

        mockMvc.perform(auth(patch("/api/notifications/{id}/read", first.getId()), ownerToken))
                .andExpect(status().isNoContent());
        // 이미 읽은 알림을 다시 읽어도 성공이다.
        mockMvc.perform(auth(patch("/api/notifications/{id}/read", first.getId()), ownerToken))
                .andExpect(status().isNoContent());
        em.flush();
        em.clear();
        mockMvc.perform(auth(get("/api/notifications/unread-count"), ownerToken))
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(auth(patch("/api/notifications/read-all"), ownerToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(auth(get("/api/notifications/unread-count"), ownerToken))
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(auth(get("/api/notifications"), ownerToken))
                .andExpect(jsonPath("$.content[0].read").value(true))
                .andExpect(jsonPath("$.content[1].read").value(true));
    }

    @Test
    void 남의_알림이나_없는_알림을_읽으면_404() throws Exception {
        Notification mine = notification(owner, "내 알림");
        em.flush();

        mockMvc.perform(auth(patch("/api/notifications/{id}/read", mine.getId()), otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        mockMvc.perform(auth(patch("/api/notifications/{id}/read", 999999), ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 비로그인은_401() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
    }

    @Test
    void 긴_제목과_본문은_잘라서_저장한다() {
        Notification saved = notificationRepository.saveAndFlush(Notification.of(owner, NotificationType.PRICE_DROP,
                "가".repeat(150), "나".repeat(300), "/products/1"));

        assertThat(saved.getTitle()).hasSize(100);
        assertThat(saved.getContent()).hasSize(255);
    }
}
