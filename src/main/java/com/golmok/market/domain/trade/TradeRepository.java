package com.golmok.market.domain.trade;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
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

    /**
     * 상품 잠금을 먼저 잡은 뒤 거래를 잠근다. 다른 상품 쓰기와 잠금 순서를 통일한다.
     * 상품은 호출한 쪽이 이미 잠가 영속성 컨텍스트에 있으므로 fetch join 하지 않는다(잠금 쿼리의 조인은 조인 행까지 잠근다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trade t where t.id = :id")
    Optional<Trade> findByIdForUpdate(long id);

    /** 잠금 전에 엔티티를 읽으면 기다린 뒤에도 낡은 상태가 남으므로 id 만 읽는다. */
    @Query("select t.product.id from Trade t where t.id = :id and (t.buyer.id = :viewerId or t.seller.id = :viewerId)")
    Optional<Long> findProductIdForParticipant(long id, long viewerId);

    /**
     * 채팅방에 보여줄 거래. 가장 최근 거래 중 취소·환불이 아닌 것(진행 중이거나 완료).
     * 취소된 거래는 보여주지 않는다. 취소 뒤에는 다시 예약할 수 있는 상태로 돌아가야 하기 때문이다.
     */
    Optional<Trade> findFirstByChatRoomIdAndStatusNotInOrderByIdDesc(long chatRoomId, Collection<TradeStatus> excluded);

    /** 채팅방의 예약(REQUESTED) 거래를 잠가 읽는다. 상품·방 잠금을 먼저 잡은 뒤 호출한다. 잠금 쿼리에는 fetch join 을 넣지 않는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trade t where t.chatRoom.id = :chatRoomId and t.status = :status")
    Optional<Trade> findByChatRoomIdAndStatusForUpdate(long chatRoomId, TradeStatus status);

    /** 상품에 진행 중인 거래가 있는지. 상품 상태 수동 변경·삭제를 막는 데 쓴다. */
    boolean existsByProductIdAndStatusIn(long productId, Collection<TradeStatus> statuses);
}
