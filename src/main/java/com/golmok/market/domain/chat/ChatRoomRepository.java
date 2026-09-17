package com.golmok.market.domain.chat;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /**
     * 채팅하기 전에 기존 방을 찾는다. 상품 잠금을 잡은 뒤에 호출한다.
     *
     * 잠금 조회로 읽는 이유: MySQL 기본 격리수준(REPEATABLE READ)의 일반 SELECT 는 트랜잭션의 스냅샷을 읽어,
     * 상품 잠금을 기다리는 동안 먼저 커밋된 방을 못 볼 수 있다. 잠금 조회는 항상 최신 커밋을 읽는다.
     * 잠금 쿼리에는 fetch join 을 넣지 않는다. MySQL 은 조인한 상품·회원 행까지 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRoom r where r.product.id = :productId and r.buyer.id = :buyerId")
    Optional<ChatRoom> findByProductIdAndBuyerIdForUpdate(long productId, long buyerId);

    /** 메시지 전송·나가기. 같은 방의 전송을 한 줄로 세워 last_message 가 가장 최근 메시지와 어긋나지 않게 한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRoom r where r.id = :id")
    Optional<ChatRoom> findByIdForUpdate(long id);

    @Query("""
            select r from ChatRoom r
            join fetch r.product
            join fetch r.buyer
            join fetch r.seller
            where r.id = :id
            """)
    Optional<ChatRoom> findWithDetails(long id);

    /**
     * 내 채팅 목록 첫 페이지. 최근 메시지 순.
     *
     * 메시지가 한 번도 오가지 않은 방은 빼고, 내가 나간 방도 뺀다.
     * "채팅하기"만 누르고 아무 말 없이 떠난 방이 판매자 목록에 쌓이지 않게 하기 위함이다.
     *
     * 구매자·판매자 조건이 OR 라 한 인덱스로 끝나지 않는다. 스키마에 (buyer_id, last_message_at),
     * (seller_id, last_message_at) 인덱스가 따로 있어 옵티마이저가 둘을 합쳐 쓸 수 있다.
     * 방이 아주 많아지면 두 쿼리로 나눠 병합하는 방식을 검토한다.
     */
    @Query("""
            select r from ChatRoom r
            join fetch r.product
            join fetch r.buyer
            join fetch r.seller
            where r.lastMessageAt is not null
              and ((r.buyer.id = :userId and r.buyerLeft = false)
                or (r.seller.id = :userId and r.sellerLeft = false))
            order by r.lastMessageAt desc, r.id desc
            """)
    List<ChatRoom> findMyRooms(long userId, Pageable pageable);

    @Query("""
            select r from ChatRoom r
            join fetch r.product
            join fetch r.buyer
            join fetch r.seller
            where r.lastMessageAt is not null
              and ((r.buyer.id = :userId and r.buyerLeft = false)
                or (r.seller.id = :userId and r.sellerLeft = false))
              and (r.lastMessageAt < :cursorTime or (r.lastMessageAt = :cursorTime and r.id < :cursorId))
            order by r.lastMessageAt desc, r.id desc
            """)
    List<ChatRoom> findMyRoomsAfter(long userId, LocalDateTime cursorTime, long cursorId, Pageable pageable);
}
