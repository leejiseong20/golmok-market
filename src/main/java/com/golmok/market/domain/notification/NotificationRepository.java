package com.golmok.market.domain.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
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
}
