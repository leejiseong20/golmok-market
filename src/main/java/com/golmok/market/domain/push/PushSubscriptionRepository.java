package com.golmok.market.domain.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findAllByUserId(long userId);

    /** 내 구독만 지운다. 남의 기기 주소를 알아도 그 사람의 구독은 지울 수 없다. */
    @Modifying(flushAutomatically = true)
    @Query("delete from PushSubscription s where s.endpoint = :endpoint and s.user.id = :userId")
    int deleteMine(String endpoint, long userId);

    /**
     * 회원 탈퇴. clearAutomatically 를 켜지 않는다 — 영속성 컨텍스트를 비우면 탈퇴 서비스가 잠가 둔 User 가
     * 준영속이 되어 그 뒤의 user.withdraw() 가 반영되지 않는다(차단·찜·알림 삭제와 같은 이유).
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from PushSubscription s where s.user.id = :userId")
    int deleteAllByUserId(long userId);
}
