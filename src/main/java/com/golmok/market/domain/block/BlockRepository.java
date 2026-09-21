package com.golmok.market.domain.block;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByBlockerIdAndBlockedId(long blockerId, long blockedId);

    Optional<Block> findByBlockerIdAndBlockedId(long blockerId, long blockedId);

    /**
     * 어느 쪽이 차단했든 true.
     * 채팅과 알림은 양방향으로 막는다. 차단당한 사람이 계속 말을 걸 수 있으면 차단이 아니다.
     */
    @Query("""
            select count(b) > 0 from Block b
            where (b.blocker.id = :oneId and b.blocked.id = :otherId)
               or (b.blocker.id = :otherId and b.blocked.id = :oneId)
            """)
    boolean existsBetween(long oneId, long otherId);

    /** 내가 차단한 사람. 목록에서 그 사람 상품을 빼는 데 쓴다(한 방향이다. 상대는 내 상품을 계속 본다). */
    @Query("select b.blocked.id from Block b where b.blocker.id = :blockerId")
    List<Long> findBlockedIds(long blockerId);

    /** 나와 차단 관계인 모든 사람(양방향). 채팅 목록에서 그 방을 숨기는 데 쓴다. */
    @Query("""
            select case when b.blocker.id = :userId then b.blocked.id else b.blocker.id end
            from Block b where b.blocker.id = :userId or b.blocked.id = :userId
            """)
    List<Long> findRelatedIds(long userId);

    /** 차단 목록. 최근에 차단한 순. 닉네임·사진을 함께 보여주므로 대상을 fetch 한다. */
    @Query("""
            select b from Block b join fetch b.blocked
            where b.blocker.id = :blockerId
            order by b.createdAt desc, b.id desc
            """)
    List<Block> findFirstPage(long blockerId, Pageable pageable);

    @Query("""
            select b from Block b join fetch b.blocked
            where b.blocker.id = :blockerId
              and (b.createdAt < :cursorTime or (b.createdAt = :cursorTime and b.id < :cursorId))
            order by b.createdAt desc, b.id desc
            """)
    List<Block> findNextPage(long blockerId, LocalDateTime cursorTime, long cursorId, Pageable pageable);

    /** 회원 탈퇴: 그 회원이 낀 차단 관계를 모두 지운다. 남겨도 쓸 곳이 없고 익명화된 행만 가리킨다. */
    // clearAutomatically 를 켜지 않는다. 영속성 컨텍스트를 비우면 탈퇴 서비스가 잠가 둔 User 가
    // 준영속이 되어 그 뒤의 user.withdraw() 가 DB 에 반영되지 않는다(찜·알림 삭제와 같은 이유).
    @Modifying(flushAutomatically = true)
    @Query("delete from Block b where b.blocker.id = :userId or b.blocked.id = :userId")
    int deleteAllRelatedTo(long userId);
}
