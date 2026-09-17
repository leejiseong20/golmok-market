package com.golmok.market.domain.search;

import com.golmok.market.domain.search.dto.PopularKeywordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 최근 24시간 인기 검색어 TOP 5.
 *
 * 집계 결과를 1분 동안 기억한다. 홈에 들어올 때마다 24시간치 로그를 GROUP BY 하면 방문자 수만큼 무거운 쿼리가 돈다.
 * 인기 검색어는 1분 늦어도 문제없는 정보다.
 *
 * 기억한 결과가 만료되는 순간 요청이 몰리면 모두가 동시에 집계하게 된다. 갱신은 한 번에 한 스레드만 하고,
 * 기다린 스레드는 방금 갱신된 결과를 쓴다. 서버 한 대 기준의 메모리 캐시다(서버를 늘리면 서버마다 따로 기억한다).
 */
@Service
@RequiredArgsConstructor
public class PopularKeywordService {

    static final Duration WINDOW = Duration.ofHours(24);
    static final Duration CACHE_TTL = Duration.ofMinutes(1);
    static final int LIMIT = 5;

    private final SearchLogRepository searchLogRepository;
    private final Clock clock;
    private final Object refreshLock = new Object();
    private volatile Snapshot snapshot;

    private record Snapshot(Instant loadedAt, List<PopularKeywordResponse> keywords) {

        boolean isFreshAt(Instant now) {
            return now.isBefore(loadedAt.plus(CACHE_TTL));
        }
    }

    public List<PopularKeywordResponse> findPopular() {
        Snapshot current = snapshot;
        if (current != null && current.isFreshAt(clock.instant())) {
            return current.keywords();
        }
        synchronized (refreshLock) {
            // 기다리는 동안 다른 스레드가 이미 갱신했을 수 있다.
            current = snapshot;
            Instant now = clock.instant();
            if (current != null && current.isFreshAt(now)) {
                return current.keywords();
            }
            Snapshot loaded = new Snapshot(now, load(now));
            snapshot = loaded;
            return loaded.keywords();
        }
    }

    private List<PopularKeywordResponse> load(Instant now) {
        LocalDateTime since = LocalDateTime.ofInstant(now.minus(WINDOW), clock.getZone());
        List<KeywordCount> counts = searchLogRepository.countKeywordsSince(since, PageRequest.of(0, LIMIT));
        return IntStream.range(0, counts.size())
                .mapToObj(index -> new PopularKeywordResponse(index + 1, counts.get(index).keyword(), counts.get(index).count()))
                .toList();
    }
}
