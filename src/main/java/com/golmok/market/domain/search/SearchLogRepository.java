package com.golmok.market.domain.search;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
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

    /**
     * 정리 배치용. 기준 시각보다 오래된 로그 id 를 읽는다. 인덱스 (created_at, keyword) 범위 조건만으로 찾는다.
     * 정렬하지 않는다. ORDER BY id 를 붙이면 MySQL 이 범위 안 대상 전체를 정렬(filesort)한 뒤 잘라, 지울 로그가 많을수록
     * 묶음마다 비싸진다(EXPLAIN 으로 확인). 어느 로그부터 지우든 결과는 같다.
     */
    @Query("select s.id from SearchLog s where s.createdAt < :before")
    List<Long> findIdsCreatedBefore(LocalDateTime before, Pageable pageable);

    @Modifying
    @Query("delete from SearchLog s where s.id in :ids")
    int deleteByIdIn(Collection<Long> ids);
}
