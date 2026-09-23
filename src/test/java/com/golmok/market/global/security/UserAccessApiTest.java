package com.golmok.market.global.security;

import com.golmok.market.domain.report.Report;
import com.golmok.market.domain.report.ReportReason;
import com.golmok.market.domain.report.ReportRepository;
import com.golmok.market.domain.report.ReportTarget;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.domain.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정지·탈퇴·관리자 해제가 이미 발급된 access token 에도 곧바로 적용되는지.
 *
 * 예전에는 토큰의 서명·만료만 봐서, 정지해도 토큰 만료(최대 30분)까지 모든 API 가 열려 있었다.
 * 각 테스트는 먼저 한 번 요청해 인증 캐시에 "정상"을 담아 둔다 — 캐시를 비우지 못하면 그 값 때문에 통과해 버린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserAccessApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ReportRepository reports;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserAccessCache userAccessCache;
    @Autowired EntityManager em;

    User admin, member;
    String adminToken, memberToken;

    @BeforeEach
    void 준비() {
        admin = users.save(User.builder().email("admin@test.com").password("hash").nickname("운영자").build());
        em.createQuery("update User u set u.role = :role where u.id = :id")
                .setParameter("role", UserRole.ADMIN).setParameter("id", admin.getId()).executeUpdate();
        em.clear();
        member = users.save(User.builder().email("member@test.com").password("hash").nickname("회원").build());
        adminToken = tokens.createAccessToken(admin.getId(), UserRole.ADMIN);
        memberToken = tokens.createAccessToken(member.getId(), UserRole.USER);
    }

    private ResultActions me(String token) throws Exception {
        return mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token));
    }

    private void adminPost(String path, String body) throws Exception {
        mvc.perform(post(path).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void 정지하면_이미_받은_토큰도_곧바로_막히고_풀면_다시_된다() throws Exception {
        me(memberToken).andExpect(status().isOk());

        adminPost("/api/admin/users/" + member.getId() + "/suspend", "{ \"reason\": \"사기 시도\" }");

        me(memberToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_NOT_ACTIVE"));
        // 공개 API 도 그 토큰으로는 막는다(토큰을 보냈으면 그 계정으로 쓰려는 것이다). 토큰 없이는 그대로 열린다.
        mvc.perform(get("/api/categories").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/categories")).andExpect(status().isOk());

        adminPost("/api/admin/users/" + member.getId() + "/unsuspend", "{ \"reason\": \"소명 확인\" }");
        me(memberToken).andExpect(status().isOk());
    }

    @Test
    void 신고_처리로_정지해도_곧바로_막힌다() throws Exception {
        me(memberToken).andExpect(status().isOk());
        long reportId = reports.saveAndFlush(Report.of(admin, ReportTarget.USER, member.getId(),
                ReportReason.ABUSE, null)).getId();

        adminPost("/api/admin/reports/" + reportId + "/resolve", "{ \"action\": \"SUSPEND_USER\", \"reason\": \"욕설\" }");

        me(memberToken).andExpect(status().isForbidden());
    }

    @Test
    void 권한은_토큰이_아니라_DB_기준이다() throws Exception {
        mvc.perform(get("/api/admin/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // DB 에서 관리자를 내린다. 이 경로(직접 SQL)는 이벤트가 없어 캐시가 60초 뒤에 비워진다 — 그 만료를 대신한다.
        em.createQuery("update User u set u.role = :role where u.id = :id")
                .setParameter("role", UserRole.USER).setParameter("id", admin.getId()).executeUpdate();
        userAccessCache.invalidate(admin.getId());

        // 토큰에는 아직 ADMIN 이라고 적혀 있지만 관리자 API 는 없는 주소처럼 404 다.
        mvc.perform(get("/api/admin/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 없는_회원의_토큰은_막는다() throws Exception {
        String ghost = tokens.createAccessToken(Long.MAX_VALUE, UserRole.USER);
        me(ghost).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_NOT_ACTIVE"));
    }
}
