package com.golmok.market.domain.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * token 컬럼에는 원문이 아니라 SHA-256 해시가 들어있다. 조회도 해시로 한다.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String tokenHash);

    /**
     * 재발급용 조회. SELECT ... FOR UPDATE 로 행을 잠근다.
     *
     * 같은 refresh token 으로 재발급 요청이 동시에 두 번 오면(탭 두 개, 네트워크 재시도),
     * 잠금이 없을 때 둘 다 조회에 성공해 서로 다른 새 토큰을 발급하고, 나중에 커밋한 쪽이 앞의 것을 덮어쓴다.
     * 먼저 받은 쪽은 DB 에 없는 토큰을 들고 있게 되어 다음 재발급에서 이유 없이 로그아웃된다.
     *
     * 잠금을 걸면 두 번째 요청은 첫 번째 커밋까지 기다렸다가 다시 읽는데,
     * 그때는 token 이 이미 바뀌어 조회 결과가 없으므로 명확하게 INVALID_REFRESH_TOKEN 이 된다.
     *
     * user 는 fetch join 하지 않는다. 함께 조회하면 users 행까지 잠겨 불필요한 경합이 생긴다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rt from RefreshToken rt where rt.token = :tokenHash")
    Optional<RefreshToken> findByTokenForUpdate(String tokenHash);

    /** 회원 탈퇴: 모든 기기의 로그인 연장을 막는다. 이미 발급된 access token 은 만료(최대 30분)까지 유효하다. */
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken rt where rt.user.id = :userId")
    int deleteAllByUserId(long userId);
}
