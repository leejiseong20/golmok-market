package com.golmok.market.domain.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 화면이 쓰는 회원 조회. 가입·로그인(UserRepository)과 목적이 달라 따로 둔다(AdminReportRepository 와 같은 선택).
 */
public interface AdminUserRepository extends JpaRepository<User, Long> {

    /**
     * 최신 가입순 한 페이지. 이메일은 전체 일치(uk_users_email), 닉네임은 부분 일치로 찾는다.
     * 닉네임 검색어의 %·_ 는 호출하는 쪽이 '!' 로 이스케이프해 넘긴다(글자 그대로 찾게).
     * 부분 일치는 인덱스를 못 타지만 관리자만 쓰고 회원 수가 적어 괜찮다.
     */
    @Query("""
            select u from User u
            where (:status is null or u.status = :status)
              and (:email is null or u.email = :email)
              and (:nickname is null or u.nickname like concat('%', :nickname, '%') escape '!')
              and (:cursorId is null or u.id < :cursorId)
            order by u.id desc
            """)
    List<User> findPage(UserStatus status, String email, String nickname, Long cursorId, Pageable pageable);

    long countByStatus(UserStatus status);

    long countByCreatedAtGreaterThanEqual(LocalDateTime from);
}
