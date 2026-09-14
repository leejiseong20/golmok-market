package com.golmok.market.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRegionRepository extends JpaRepository<UserRegion, Long> {

    /** 로그인 응답의 primaryRegion 용. 동네 이름이 필요하므로 region 을 함께 가져온다. */
    @Query("""
            select ur from UserRegion ur
            join fetch ur.region
            where ur.user.id = :userId and ur.primary = true
            """)
    Optional<UserRegion> findPrimaryWithRegion(Long userId);
}
