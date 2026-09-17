package com.golmok.market.domain.search;

import com.golmok.market.domain.region.RegionRepository;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.config.AsyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 검색 로그 저장. 검색 응답이 로그 저장을 기다리지 않도록 전용 실행기에서 따로 돈다(API-SPEC 7절).
 *
 * 검색 조회 트랜잭션이 끝난 뒤(@TransactionalEventListener) 실행기에 넘긴다. 조회가 실패하면 기록하지 않는다.
 * 저장 실패는 기록만 하고 삼킨다. 로그는 인기 검색어용 부가 정보라 검색 결과에 영향을 주면 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchLogRecorder {

    private final SearchLogRepository searchLogRepository;
    private final RegionRepository regionRepository;
    private final UserRepository userRepository;

    @Async(AsyncConfig.SEARCH_LOG_EXECUTOR)
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ProductSearchedEvent event) {
        try {
            // 목록 API 는 없는 동네 id 도 빈 결과로 받아준다. 그대로 참조하면 외래키 위반이라 확인 후 없으면 비운다.
            var region = regionRepository.existsById(event.regionId()) ? regionRepository.getReferenceById(event.regionId()) : null;
            var user = event.userId() == null ? null : userRepository.getReferenceById(event.userId());
            searchLogRepository.save(SearchLog.of(user, region, event.keyword()));
        } catch (RuntimeException e) {
            log.warn("검색 로그 저장 실패 keyword={}", event.keyword(), e);
        }
    }
}
