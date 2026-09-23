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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 신고 처리.
 *
 * 권한(관리자가 아니면 404), 조치(상품 내리기·사용자 정지), 같은 대상 신고 일괄 종료, 감사 로그를 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminReportApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired RegionRepository regions;
    @Autowired ReportRepository reports;
    @Autowired AdminActionRepository adminActions;
    @Autowired JwtTokenProvider tokens;
    @Autowired EntityManager em;

    User admin, reporter, otherReporter, seller;
    Product product;
    String adminToken, userToken;

    @BeforeEach
    void 준비() {
        admin = users.save(User.builder().email("admin@test.com").password("hash").nickname("관리자").build());
        // 관리자는 운영에서도 DB 에서 직접 지정한다(권한 상승 API 를 만들지 않는다).
        em.createQuery("update User u set u.role = :role where u.id = :id")
                .setParameter("role", UserRole.ADMIN).setParameter("id", admin.getId()).executeUpdate();
        em.clear();
        admin = users.findById(admin.getId()).orElseThrow();

        reporter = users.save(User.builder().email("reporter@test.com").password("hash").nickname("신고한사람").build());
        otherReporter = users.save(User.builder().email("other@test.com").password("hash").nickname("다른신고자").build());
        seller = users.save(User.builder().email("seller@test.com").password("hash").nickname("판매자").build());
        adminToken = tokens.createAccessToken(admin.getId(), UserRole.ADMIN);
        userToken = tokens.createAccessToken(reporter.getId(), reporter.getRole());

        Category category = categories.save(Category.create(null, "관리카테고리", null, 1));
        Region region = regions.save(Region.create("서울", "강남", "역삼동", 37.5, 127.0));
        product = products.save(Product.builder().seller(seller).category(category).region(region)
                .title("문제 있는 상품").description("관리자 처리 확인용 상품입니다.").price(10000).build());
    }

    private Report report(User who, ReportTarget type, long targetId) {
        return reports.saveAndFlush(Report.of(who, type, targetId, ReportReason.FRAUD, "사기 같아요"));
    }

    private ResultActions handle(String path, long reportId, String bodyJson, String token) throws Exception {
        return mvc.perform(post("/api/admin/reports/{id}/" + path, reportId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(bodyJson));
    }

    // ---------- 권한 ----------

    @Test
    void 관리자가_아니면_404_다_존재를_알리지_않는다() throws Exception {
        long id = report(reporter, ReportTarget.PRODUCT, product.getId()).getId();

        mvc.perform(get("/api/admin/reports").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
        handle("resolve", id, """
                { "action": "DELETE_PRODUCT", "reason": "확인함" }
                """, userToken).andExpect(status().isNotFound());
        // 비로그인은 그대로 401 이다(인증이 먼저다).
        mvc.perform(get("/api/admin/reports")).andExpect(status().isUnauthorized());
    }

    // ---------- 목록·상세 ----------

    @Test
    void 목록은_처리_전_신고만_보여주고_같은_대상_건수를_함께_준다() throws Exception {
        report(reporter, ReportTarget.PRODUCT, product.getId());
        report(otherReporter, ReportTarget.PRODUCT, product.getId());

        mvc.perform(get("/api/admin/reports").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].targetName").value("문제 있는 상품"))
                .andExpect(jsonPath("$.content[0].reportCount").value(2))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void 상세에는_같은_대상의_다른_신고가_함께_나온다() throws Exception {
        long first = report(reporter, ReportTarget.PRODUCT, product.getId()).getId();
        report(otherReporter, ReportTarget.PRODUCT, product.getId());

        mvc.perform(get("/api/admin/reports/{id}", first).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sameTarget.length()").value(1))
                .andExpect(jsonPath("$.reporterNickname").value("신고한사람"));
    }

    // ---------- 조치 ----------

    @Test
    void 상품을_내리면_같은_상품의_대기_신고가_함께_닫히고_기록이_남는다() throws Exception {
        long first = report(reporter, ReportTarget.PRODUCT, product.getId()).getId();
        long second = report(otherReporter, ReportTarget.PRODUCT, product.getId()).getId();

        handle("resolve", first, """
                { "action": "DELETE_PRODUCT", "reason": "판매 금지 물품입니다." }
                """, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.handledByNickname").value("관리자"));
        em.flush();
        em.clear();

        assertThat(products.findById(product.getId()).orElseThrow().isDeleted()).isTrue();
        // 같은 상품을 신고 수만큼 반복해서 내리지 않도록, 나머지 신고도 함께 닫힌다.
        assertThat(reports.findById(second).orElseThrow().isPending()).isFalse();
        assertThat(adminActions.findByTargetTypeAndTargetIdOrderByIdDesc(AdminActionTarget.PRODUCT, product.getId()))
                .singleElement()
                .satisfies(action -> {
                    assertThat(action.getAction()).isEqualTo(AdminActionType.DELETE_PRODUCT);
                    assertThat(action.getReason()).isEqualTo("판매 금지 물품입니다.");
                    assertThat(action.getAdmin().getId()).isEqualTo(admin.getId());
                });
    }

    @Test
    void 사용자를_정지하면_상태가_바뀌고_로그인이_막힌다() throws Exception {
        long id = report(reporter, ReportTarget.USER, seller.getId()).getId();

        handle("resolve", id, """
                { "action": "SUSPEND_USER", "reason": "반복 사기 신고" }
                """, adminToken).andExpect(status().isOk());
        em.flush();
        em.clear();

        assertThat(users.findById(seller.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(adminActions.findByTargetTypeAndTargetIdOrderByIdDesc(AdminActionTarget.USER, seller.getId()))
                .anyMatch(action -> action.getAction() == AdminActionType.SUSPEND_USER);
    }

    @Test
    void 상품_신고로도_판매자를_정지할_수_있다() throws Exception {
        long id = report(reporter, ReportTarget.PRODUCT, product.getId()).getId();

        handle("resolve", id, """
                { "action": "SUSPEND_USER", "reason": "금지 물품을 반복 등록" }
                """, adminToken).andExpect(status().isOk());
        em.flush();
        em.clear();

        assertThat(users.findById(seller.getId()).orElseThrow().isSuspended()).isTrue();
    }

    @Test
    void 관리자_계정은_정지할_수_없다() throws Exception {
        long id = report(reporter, ReportTarget.USER, admin.getId()).getId();

        handle("resolve", id, """
                { "action": "SUSPEND_USER", "reason": "시도" }
                """, adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_SUSPEND_ADMIN"));
    }

    @Test
    void 이미_삭제된_상품이면_조치는_건너뛰고_신고만_닫는다() throws Exception {
        long id = report(reporter, ReportTarget.PRODUCT, product.getId()).getId();
        products.findById(product.getId()).orElseThrow().softDelete();
        em.flush();

        handle("resolve", id, """
                { "action": "DELETE_PRODUCT", "reason": "이미 내려간 상품" }
                """, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        em.flush();
        em.clear();

        // 조치할 것이 없었으므로 상품 조치 기록은 남지 않는다(신고 처리 기록만 남는다).
        assertThat(adminActions.findByTargetTypeAndTargetIdOrderByIdDesc(AdminActionTarget.PRODUCT, product.getId()))
                .isEmpty();
    }

    @Test
    void 반려하면_그_신고만_닫히고_대상은_그대로다() throws Exception {
        long first = report(reporter, ReportTarget.PRODUCT, product.getId()).getId();
        long second = report(otherReporter, ReportTarget.PRODUCT, product.getId()).getId();

        handle("reject", first, """
                { "reason": "문제 없는 상품입니다." }
                """, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        em.flush();
        em.clear();

        assertThat(products.findById(product.getId()).orElseThrow().isDeleted()).isFalse();
        // 사유가 다를 수 있어 같은 대상의 다른 신고는 그대로 둔다.
        assertThat(reports.findById(second).orElseThrow().isPending()).isTrue();
    }

    @Test
    void 이유_없이는_처리할_수_없다() throws Exception {
        long id = report(reporter, ReportTarget.PRODUCT, product.getId()).getId();

        handle("resolve", id, """
                { "action": "NONE", "reason": "   " }
                """, adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
    }

    @Test
    void 이미_처리한_신고는_다시_처리할_수_없다() throws Exception {
        long id = report(reporter, ReportTarget.PRODUCT, product.getId()).getId();
        handle("resolve", id, """
                { "action": "NONE", "reason": "경고만 함" }
                """, adminToken).andExpect(status().isOk());

        handle("resolve", id, """
                { "action": "DELETE_PRODUCT", "reason": "다시 처리" }
                """, adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_HANDLED"));
    }

    @Test
    void 없는_신고는_404_다() throws Exception {
        mvc.perform(get("/api/admin/reports/{id}", 999_999).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }
}
