package com.golmok.market.domain.trade;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    /**
     * 내 구매내역 첫 페이지. 최근 거래 순.
     *
     * 삭제된 상품도 그대로 보여준다. 찜 목록과 다른 점인데, 구매내역은 "내가 산 기록"이라
     * 판매자가 글을 내렸다고 해서 영수증이 사라지면 안 되기 때문이다.
     */
    @Query("""
            select t from Trade t
            join fetch t.product p
            join fetch p.seller
            where t.buyer.id = :buyerId
            order by t.createdAt desc, t.id desc
            """)
    List<Trade> findPurchases(long buyerId, Pageable pageable);

    @Query("""
            select t from Trade t
            join fetch t.product p
            join fetch p.seller
            where t.buyer.id = :buyerId
              and (t.createdAt < :cursorTime or (t.createdAt = :cursorTime and t.id < :cursorId))
            order by t.createdAt desc, t.id desc
            """)
    List<Trade> findPurchasesAfter(long buyerId, LocalDateTime cursorTime, long cursorId, Pageable pageable);

    @Query("select t.product.id from Trade t where t.id = :id and t.buyer.id = :buyerId")
    Optional<Long> findProductIdForBuyer(long id, long buyerId);

    /** 상품 잠금을 먼저 잡은 뒤 거래를 잠근다. 다른 상품 쓰기와 잠금 순서를 통일한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trade t join fetch t.product where t.id = :id")
    Optional<Trade> findWithProduct(long id);
}
