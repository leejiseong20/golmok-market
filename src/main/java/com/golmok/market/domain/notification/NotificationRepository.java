package com.golmok.market.domain.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 내 알림 첫 페이지. 최신순. 인덱스 (user_id, is_read, created_at DESC) 의 앞 컬럼을 탄다. */
    @Query("""
            select n from Notification n
            where n.user.id = :userId
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findFirstPage(long userId, Pageable pageable);

    @Query("""
            select n from Notification n
            where n.user.id = :userId
              and (n.createdAt < :cursorTime or (n.createdAt = :cursorTime and n.id < :cursorId))
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findNextPage(long userId, LocalDateTime cursorTime, long cursorId, Pageable pageable);

    long countByUserIdAndReadFalse(long userId);

    /** 남의 알림은 없는 것처럼 다룬다(채팅방·거래와 같은 규칙). */
    Optional<Notification> findByIdAndUserId(long id, long userId);

    boolean existsByUserIdAndTypeAndTargetUrlAndReadFalse(long userId, NotificationType type, String targetUrl);

    /** 한 번의 UPDATE 로 모두 읽음. 알림을 하나씩 읽어 들이지 않는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.user.id = :userId and n.read = false")
    int markAllAsRead(long userId);

    /**
     * 정리 배치용. 읽음 여부별로 기준 시각보다 오래된 알림 id 를 id 순으로 읽는다.
     * 인덱스 (user_id, is_read, created_at) 는 사용자 조건이 없어 쓰이지 않는다. 오래된 행이 id 앞쪽에 몰려 있어
     * 지금 규모에서는 괜찮지만, "더 없음"을 확인하는 마지막 조회는 테이블을 끝까지 볼 수 있다(알려진 과제).
     */
    @Query("select n.id from Notification n where n.read = :read and n.createdAt < :before order by n.id")
    List<Long> findIdsCreatedBefore(boolean read, LocalDateTime before, Pageable pageable);

    @Modifying
    @Query("delete from Notification n where n.id in :ids")
    int deleteByIdIn(Collection<Long> ids);

    @Modifying(flushAutomatically = true)
    @Query("delete from Notification n where n.user.id = :userId")
    int deleteAllByUserId(long userId);
}
