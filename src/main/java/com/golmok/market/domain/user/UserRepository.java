package com.golmok.market.domain.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(long id);

    /** DB 의 현재 값에 반영한다. 로그인 등 일반 엔티티 UPDATE 는 이 컬럼을 건드리지 않는다. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update User u set u.mannerTemp =
              case when u.mannerTemp + :delta < 0.0 then 0.0
                   when u.mannerTemp + :delta > 99.9 then 99.9
                   else u.mannerTemp + :delta end,
              u.updatedAt = u.updatedAt
            where u.id = :id
            """)
    int adjustMannerTemp(long id, BigDecimal delta);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);
}
