package com.golmok.market.domain.trade;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 상품·거래 잠금과 READ_COMMITTED 로 중복을 검사한다. 없는 후기의 gap lock 은 잡지 않는다. */
    Optional<Review> findByTradeIdAndReviewerId(long tradeId, long reviewerId);

    boolean existsByTradeIdAndReviewerId(long tradeId, long reviewerId);

    long countByRevieweeId(long revieweeId);

    @Query("select r.trade.id from Review r where r.reviewer.id = :reviewerId and r.trade.id in :tradeIds")
    Set<Long> findReviewedTradeIds(long reviewerId, Collection<Long> tradeIds);

    @Query("""
            select r from Review r join fetch r.reviewer
            where r.reviewee.id = :userId order by r.createdAt desc, r.id desc
            """)
    List<Review> findReceived(long userId, Pageable pageable);

    @Query("""
            select r from Review r join fetch r.reviewer
            where r.reviewee.id = :userId
              and (r.createdAt < :time or (r.createdAt = :time and r.id < :id))
            order by r.createdAt desc, r.id desc
            """)
    List<Review> findReceivedAfter(long userId, LocalDateTime time, long id, Pageable pageable);
}
