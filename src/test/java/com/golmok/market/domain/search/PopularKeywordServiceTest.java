package com.golmok.market.domain.search;

import com.golmok.market.domain.search.dto.PopularKeywordResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 인기 검색어 집계와 1분 캐시. 시계를 테스트에서 움직여 실제로 기다리지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PopularKeywordServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-09-17T03:00:00Z");

    @Autowired SearchLogRepository searchLogRepository;
    @Autowired EntityManager em;
    @Autowired MockMvc mockMvc;

    private MutableClock clock;
    private PopularKeywordService service;

    /** 테스트에서 시간을 앞으로 돌릴 수 있는 시계. */
    static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @BeforeEach
    void 준비() {
        clock = new MutableClock(NOW);
        service = new PopularKeywordService(searchLogRepository, clock);
    }

    /** 검색 로그를 원하는 시각에 남긴다. 생성 시각은 저장 때 자동으로 채워지므로 저장 뒤 바꾼다. */
    private void log(String keyword, Duration ago, int times) {
        LocalDateTime at = LocalDateTime.ofInstant(clock.instant().minus(ago), ZONE);
        for (int i = 0; i < times; i++) {
            SearchLog saved = searchLogRepository.save(SearchLog.of(null, null, keyword));
            em.flush();
            em.createQuery("update SearchLog s set s.createdAt = :at where s.id = :id")
                    .setParameter("at", at).setParameter("id", saved.getId()).executeUpdate();
        }
        em.clear();
    }

    @Test
    void 최근_24시간_검색만_세고_많은_순_같으면_검색어_순이다() {
        log("에어팟", Duration.ofHours(1), 3);
        log("식탁", Duration.ofHours(23), 2);
        log("가방", Duration.ofMinutes(5), 2);
        log("정확히_24시간_전", Duration.ofHours(24), 1);
        log("24시간_넘음", Duration.ofHours(24).plusSeconds(1), 9);

        assertThat(service.findPopular()).containsExactly(
                new PopularKeywordResponse(1, "에어팟", 3),
                new PopularKeywordResponse(2, "가방", 2),
                new PopularKeywordResponse(3, "식탁", 2),
                new PopularKeywordResponse(4, "정확히_24시간_전", 1));
    }

    @Test
    void 다섯_개까지만_준다() {
        for (String keyword : new String[]{"a", "b", "c", "d", "e", "f", "g"}) {
            log(keyword, Duration.ofMinutes(1), 1);
        }

        assertThat(service.findPopular()).hasSize(5).extracting(PopularKeywordResponse::keyword)
                .containsExactly("a", "b", "c", "d", "e");
    }

    @Test
    void 검색_기록이_없으면_빈_목록이다() {
        assertThat(service.findPopular()).isEmpty();
    }

    @Test
    void 일분_동안은_기억한_결과를_주고_지나면_다시_집계한다() {
        log("에어팟", Duration.ofMinutes(1), 1);
        assertThat(service.findPopular()).extracting(PopularKeywordResponse::keyword).containsExactly("에어팟");

        log("식탁", Duration.ZERO, 5);
        clock.advance(Duration.ofSeconds(59));
        assertThat(service.findPopular()).extracting(PopularKeywordResponse::keyword).containsExactly("에어팟");

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.findPopular()).extracting(PopularKeywordResponse::keyword).containsExactly("식탁", "에어팟");
    }

    @Test
    void 비로그인도_API_로_조회할_수_있다() throws Exception {
        mockMvc.perform(get("/api/search/keywords/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
