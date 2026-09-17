package com.golmok.market.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRegionRepository extends JpaRepository<UserRegion, Long> {

    /** 로그인 응답의 primaryRegion 용. 동네 이름이 필요하므로 region 을 함께 가져온다. */
    @Query("""
            select ur from UserRegion ur
            join fetch ur.region
            where ur.user.id = :userId and ur.primary = true
            """)
    Optional<UserRegion> findPrimaryWithRegion(Long userId);

    /** 내 동네 목록. 대표 동네를 먼저, 그다음 최근 인증 순. */
    @Query("""
            select ur from UserRegion ur
            join fetch ur.region
            where ur.user.id = :userId
            order by ur.primary desc, ur.verifiedAt desc, ur.id desc
            """)
    List<UserRegion> findAllWithRegion(long userId);

    Optional<UserRegion> findByUserIdAndRegionId(long userId, long regionId);

    long countByUserId(long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from UserRegion ur where ur.user.id = :userId")
    int deleteAllByUserId(long userId);
}
