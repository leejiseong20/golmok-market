package com.golmok.market.domain.search;

import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.config.AsyncConfig;
import com.golmok.market.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 검색 → 검색 로그가 비동기로 남는 흐름. 커밋 뒤·별도 실행기에서 저장되므로
 * 이 클래스만 요청을 실제로 커밋하고, 실행기가 일을 다 마칠 때까지 기다린 뒤 확인한다(전용 H2 DB).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:search-log-flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SearchLogFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired SearchLogRepository searchLogRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired @Qualifier(AsyncConfig.SEARCH_LOG_EXECUTOR) ThreadPoolTaskExecutor executor;

    private Region region;

    @BeforeEach
    void 준비() {
        searchLogRepository.deleteAll();
        region = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동" + UUID.randomUUID().toString().substring(0, 6), 37.5, 127));
    }

    /** 실행기의 대기열과 실행 중인 작업이 모두 끝날 때까지 기다린다. "기록하지 않았다"를 확인하려면 이것이 필요하다. */
    private void awaitIdle() throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            var pool = executor.getThreadPoolExecutor();
            if (pool.getQueue().isEmpty() && pool.getActiveCount() == 0) {
                Thread.sleep(50);
                if (pool.getQueue().isEmpty() && pool.getActiveCount() == 0) {
                    return;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("검색 로그 실행기가 끝나지 않았다");
    }

    private List<SearchLog> logs() {
        return new TransactionTemplate(transactionManager).execute(status -> searchLogRepository.findAll().stream()
                .peek(log -> {
                    if (log.getRegion() != null) {
                        log.getRegion().getId();
                    }
                }).toList());
    }

    @Test
    void 첫_페이지_검색은_정규화한_검색어와_동네_로그인_사용자를_남긴다() throws Exception {
        User user = userRepository.save(User.builder().email(UUID.randomUUID() + "@example.com").password("hash")
                .nickname("검색" + UUID.randomUUID().toString().substring(0, 6)).build());
        String token = tokenProvider.createAccessToken(user.getId(), user.getRole());

        mockMvc.perform(get("/api/products").param("regionId", String.valueOf(region.getId()))
                        .param("keyword", "  iPad   Pro ").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/products").param("regionId", String.valueOf(region.getId())).param("keyword", "식탁"))
                .andExpect(status().isOk());
        awaitIdle();

        List<SearchLog> logs = logs();
        assertThat(logs).extracting(SearchLog::getKeyword).containsExactlyInAnyOrder("ipad pro", "식탁");
        SearchLog loggedIn = logs.stream().filter(log -> log.getKeyword().equals("ipad pro")).findFirst().orElseThrow();
        assertThat(loggedIn.getUser().getId()).isEqualTo(user.getId());
        assertThat(loggedIn.getRegion().getId()).isEqualTo(region.getId());
        assertThat(loggedIn.getCreatedAt()).isNotNull();
        assertThat(logs.stream().filter(log -> log.getKeyword().equals("식탁")).findFirst().orElseThrow().getUser()).isNull();
    }

    @Test
    void 더_보기_요청과_검색어_없는_목록은_기록하지_않는다() throws Exception {
        mockMvc.perform(get("/api/products").param("regionId", String.valueOf(region.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/products").param("regionId", String.valueOf(region.getId())).param("keyword", "   "))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/products").param("regionId", String.valueOf(region.getId())).param("keyword", "식탁")
                        .param("cursor", "2026-09-17T10:00:00_5"))
                .andExpect(status().isOk());
        awaitIdle();

        assertThat(searchLogRepository.count()).isZero();
    }

    @Test
    void 없는_동네로_검색해도_로그는_남고_동네는_비운다() throws Exception {
        mockMvc.perform(get("/api/products").param("regionId", "999999").param("keyword", "에어팟"))
                .andExpect(status().isOk());
        awaitIdle();

        List<SearchLog> logs = logs();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getKeyword()).isEqualTo("에어팟");
        assertThat(logs.getFirst().getRegion()).isNull();
    }
}
