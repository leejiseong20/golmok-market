package com.golmok.market.domain.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 메시지 첫 페이지. 최신 메시지부터 내려준다(화면은 뒤집어서 아래부터 쌓는다).
     * id 는 방 안에서 보낸 순서와 같으므로 정렬 기준을 id 하나로 둔다. 인덱스 (room_id, id DESC) 그대로다.
     */
    @Query("select m from ChatMessage m where m.room.id = :roomId order by m.id desc")
    List<ChatMessage> findLatest(long roomId, Pageable pageable);

    /** 위로 스크롤해 이전 메시지를 불러온다. */
    @Query("select m from ChatMessage m where m.room.id = :roomId and m.id < :cursorId order by m.id desc")
    List<ChatMessage> findBefore(long roomId, long cursorId, Pageable pageable);

    /**
     * 방별 안 읽은 메시지 수. 목록 한 페이지의 방 id 로 한 번에 센다(방마다 COUNT 를 보내지 않는다).
     * 인덱스 (room_id, sender_id, is_read) 를 탄다. 안 읽은 메시지가 없는 방은 결과에 없다.
     */
    @Query("""
            select new com.golmok.market.domain.chat.RoomUnreadCount(m.room.id, count(m))
            from ChatMessage m
            where m.room.id in :roomIds and m.sender.id <> :userId and m.read = false
            group by m.room.id
            """)
    List<RoomUnreadCount> countUnread(long userId, Collection<Long> roomIds);

    /**
     * 안 읽은 메시지 합계(뱃지). 방별 수(countUnread)와 같은 조건에 "내가 나가지 않은 방"을 더한다.
     * 나갈 때 읽음 처리하므로 나간 방에는 보통 안 읽은 메시지가 없지만, 목록에 없는 방의 수가 뱃지에 섞이지 않게 명시한다.
     */
    @Query("""
            select count(m) from ChatMessage m join m.room r
            where m.sender.id <> :userId and m.read = false
              and ((r.buyer.id = :userId and r.buyerLeft = false)
                or (r.seller.id = :userId and r.sellerLeft = false))
              and r.buyer.id not in :excludedIds and r.seller.id not in :excludedIds
            """)
    long countAllUnread(long userId, List<Long> excludedIds);

    /** 상대가 보낸 안 읽은 메시지를 한 번에 읽음 처리한다. 메시지를 하나씩 읽어 들이지 않는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ChatMessage m set m.read = true
            where m.room.id = :roomId and m.sender.id <> :userId and m.read = false
            """)
    int markOpponentMessagesAsRead(long roomId, long userId);
}
