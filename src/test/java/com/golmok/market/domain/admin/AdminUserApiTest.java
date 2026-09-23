package com.golmok.market.domain.admin;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
import com.golmok.market.domain.report.Report;
import com.golmok.market.domain.report.ReportReason;
import com.golmok.market.domain.report.ReportRepository;
import com.golmok.market.domain.report.ReportTarget;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.domain.user.UserRole;
import com.golmok.market.domain.user.UserStatus;
import com.golmok.market.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 회원 관리.
 *
 * 권한(관리자가 아니면 404), 이메일 가리기, 검색, 신고 없는 정지·정지 해제와 그 기록을 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired RegionRepository regions;
    @Autowired ReportRepository reports;
    @Autowired AdminActionRepository adminActions;
    @Autowired JwtTokenProvider tokens;
    @Autowired EntityManager em;
    @Autowired PasswordEncoder passwordEncoder;

    User admin, member, reporter;
    String adminToken, userToken;

    @BeforeEach
    void 준비() {
        admin = users.save(User.builder().email("admin@test.com").password("hash").nickname("운영자").build());
        em.createQuery("update User u set u.role = :role where u.id = :id")
                .setParameter("role", UserRole.ADMIN).setParameter("id", admin.getId()).executeUpdate();
        em.clear();
        admin = users.findById(admin.getId()).orElseThrow();

        member = users.save(User.builder().email("member@test.com").password("hash").nickname("골목회원%").build());
        reporter = users.save(User.builder().email("reporter@test.com").password("hash").nickname("신고자").build());
        adminToken = tokens.createAccessToken(admin.getId(), UserRole.ADMIN);
        userToken = tokens.createAccessToken(member.getId(), member.getRole());
    }

    private ResultActions act(String path, long userId, String reason, String token) throws Exception {
        return mvc.perform(post("/api/admin/users/{id}/" + path, userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reason == null ? "{}" : "{ \"reason\": \"" + reason + "\" }"));
    }

    // ---------- 권한 ----------

    @Test
    void 관리자가_아니면_404_다() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        act("suspend", reporter.getId(), "이유", userToken).andExpect(status().isNotFound());
        assertThat(users.findById(reporter.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    // ---------- 목록·검색 ----------

    @Test
    void 목록의_이메일은_가려서_준다() throws Exception {
        mvc.perform(get("/api/admin/users").param("q", "골목회원")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value("me***@test.com"))
                .andExpect(jsonPath("$.content[0].admin").value(false));
    }

    @Test
    void 이메일은_전체가_같아야_찾는다() throws Exception {
        mvc.perform(get("/api/admin/users").param("q", "member@test.com")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nickname").value("골목회원%"));
        // 앞부분만 적으면 찾지 않는다(가린 이메일을 한 글자씩 맞혀 보는 식으로 알아내지 못하게).
        mvc.perform(get("/api/admin/users").param("q", "member@")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void 닉네임의_퍼센트는_글자_그대로_찾는다() throws Exception {
        // % 를 이스케이프하지 않으면 "%" 검색이 모든 회원에 걸린다.
        mvc.perform(get("/api/admin/users").param("q", "%")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(member.getId()));
    }

    @Test
    void 상세는_받은_신고를_본인과_상품으로_나눠_센다() throws Exception {
        Category category = categories.save(Category.create(null, "회원관리카테고리", null, 1));
        Region region = regions.save(Region.create("서울", "강남", "역삼동", 37.5, 127.0));
        Product product = products.save(Product.builder().seller(member).category(category).region(region)
                .title("회원 상품").description("회원 관리 확인용 상품입니다.").price(1000).build());
        reports.saveAndFlush(Report.of(reporter, ReportTarget.USER, member.getId(), ReportReason.ABUSE, null));
        reports.saveAndFlush(Report.of(admin, ReportTarget.PRODUCT, product.getId(), ReportReason.FRAUD, null));
        reports.saveAndFlush(Report.of(reporter, ReportTarget.PRODUCT, product.getId(), ReportReason.FRAUD, null));

        mvc.perform(get("/api/admin/users/{id}", member.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("me***@test.com"))
                .andExpect(jsonPath("$.activeProductCount").value(1))
                .andExpect(jsonPath("$.reportsOnUser").value(1))
                .andExpect(jsonPath("$.reportsOnProducts").value(2))
                .andExpect(jsonPath("$.actions.length()").value(0));
    }

    // ---------- 정지·해제 ----------

    @Test
    void 신고_없이_정지하고_풀면_두_번_모두_기록이_남는다() throws Exception {
        act("suspend", member.getId(), "직접 확인한 사기 시도", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.actions[0].action").value("SUSPEND_USER"))
                .andExpect(jsonPath("$.actions[0].adminNickname").value("운영자"));

        act("unsuspend", member.getId(), "소명 확인", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.actions.length()").value(2))
                .andExpect(jsonPath("$.actions[0].action").value("UNSUSPEND_USER"))
                .andExpect(jsonPath("$.actions[0].reason").value("소명 확인"));

        em.flush();
        em.clear();
        assertThat(adminActions.findByTargetTypeAndTargetIdOrderByIdDesc(AdminActionTarget.USER, member.getId()))
                .extracting(AdminAction::getReportId).containsOnlyNulls();
    }

    @Test
    void 이유가_없으면_정지하지_않는다() throws Exception {
        act("suspend", member.getId(), null, adminToken)
                .andExpect(status().isBadRequest());
        act("suspend", member.getId(), "  ", adminToken)
                .andExpect(status().isBadRequest());
        assertThat(users.findById(member.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(adminActions.findByTargetTypeAndTargetIdOrderByIdDesc(AdminActionTarget.USER, member.getId()))
                .isEmpty();
    }

    @Test
    void 이미_그_상태면_409_이고_기록을_남기지_않는다() throws Exception {
        act("unsuspend", member.getId(), "해제", adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_ACTION_NOT_ALLOWED"));
        act("suspend", member.getId(), "정지", adminToken).andExpect(status().isOk());
        act("suspend", member.getId(), "또 정지", adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 정지된 회원입니다."));

        assertThat(adminActions.findByTargetTypeAndTargetIdOrderByIdDesc(AdminActionTarget.USER, member.getId()))
                .hasSize(1);
    }

    @Test
    void 관리자와_탈퇴_회원은_정지할_수_없다() throws Exception {
        act("suspend", admin.getId(), "관리자 정지 시도", adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_SUSPEND_ADMIN"));

        member.withdraw(LocalDateTime.now());
        users.saveAndFlush(member);
        act("suspend", member.getId(), "탈퇴 회원 정지 시도", adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("탈퇴한 회원은 정지할 수 없습니다."));
    }

    @Test
    void 정지된_회원은_로그인할_수_없고_풀면_다시_된다() throws Exception {
        User real = users.save(User.builder().email("login@test.com")
                .password(passwordHash()).nickname("로그인확인").build());
        act("suspend", real.getId(), "로그인 차단 확인", adminToken).andExpect(status().isOk());
        em.flush();
        login("login@test.com").andExpect(status().is4xxClientError());

        act("unsuspend", real.getId(), "로그인 복구 확인", adminToken).andExpect(status().isOk());
        em.flush();
        login("login@test.com").andExpect(status().isOk());
    }

    private String passwordHash() {
        return passwordEncoder.encode("Password123!");
    }

    private ResultActions login(String email) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"" + email + "\", \"password\": \"Password123!\" }"));
    }
}
