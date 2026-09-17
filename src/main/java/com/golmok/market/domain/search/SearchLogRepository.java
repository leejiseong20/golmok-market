package com.golmok.market.domain.search;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

    /**
     * 기간 안의 검색어별 횟수, 많은 순. 횟수가 같으면 검색어 순으로 순위를 고정한다(요청마다 순위가 뒤섞이지 않게).
     * 인덱스 (created_at, keyword) 로 기간 범위를 좁힌 뒤 집계한다.
     */
    @Query("""
            select new com.golmok.market.domain.search.KeywordCount(s.keyword, count(s))
            from SearchLog s
            where s.createdAt >= :since
            group by s.keyword
            order by count(s) desc, s.keyword asc
            """)
    List<KeywordCount> countKeywordsSince(LocalDateTime since, Pageable pageable);
}
