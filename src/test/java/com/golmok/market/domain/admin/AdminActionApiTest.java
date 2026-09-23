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
import com.golmok.market.domain.trade.Trade;
import com.golmok.market.domain.trade.TradeRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.domain.user.UserRole;
import com.golmok.market.global.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 조치 기록과 현황판.
 *
 * 조치 기록: 최신순, 대상 이름, 종류 거르기, 신고 처리 조치와 직접 조치의 구분(reportId).
 * 현황판: 데이터가 늘면 숫자가 그만큼 는다(테스트 DB 에 다른 행이 있을 수 있어 늘어난 양만 본다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminActionApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired RegionRepository regions;
    @Autowired ReportRepository reports;
    @Autowired TradeRepository trades;
    @Autowired JwtTokenProvider tokens;
    @Autowired EntityManager em;

    User admin, seller, buyer;
    Product product;
    String adminToken, userToken;

    @BeforeEach
    void 준비() {
        admin = users.save(User.builder().email("admin@test.com").password("hash").nickname("운영자").build());
        em.createQuery("update User u set u.role = :role where u.id = :id")
                .setParameter("role", UserRole.ADMIN).setParameter("id", admin.getId()).executeUpdate();
        em.clear();
        admin = users.findById(admin.getId()).orElseThrow();

        seller = users.save(User.builder().email("seller@test.com").password("hash").nickname("판매자").build());
        buyer = users.save(User.builder().email("buyer@test.com").password("hash").nickname("구매자").build());
        adminToken = tokens.createAccessToken(admin.getId(), UserRole.ADMIN);
        userToken = tokens.createAccessToken(seller.getId(), seller.getRole());

        Category category = categories.save(Category.create(null, "기록카테고리", null, 1));
        Region region = regions.save(Region.create("서울", "강남", "역삼동", 37.5, 127.0));
        product = products.save(Product.builder().seller(seller).category(category).region(region)
                .title("기록 확인용 상품").description("조치 기록 확인용 상품입니다.").price(5000).build());
    }

    private void adminPost(String path, String body) throws Exception {
        mvc.perform(post(path)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void 관리자가_아니면_404_다() throws Exception {
        mvc.perform(get("/api/admin/actions").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/summary").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 조치_기록은_최신순이고_대상_이름과_신고_여부를_함께_준다() throws Exception {
        long reportId = reports.saveAndFlush(Report.of(buyer, ReportTarget.PRODUCT, product.getId(),
                ReportReason.FRAUD, null)).getId();
        adminPost("/api/admin/reports/" + reportId + "/resolve", "{ \"action\": \"DELETE_PRODUCT\", \"reason\": \"사기\" }");
        adminPost("/api/admin/users/" + seller.getId() + "/suspend", "{ \"reason\": \"직접 정지\" }");

        mvc.perform(get("/api/admin/actions").param("size", "3").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                // 최신순: 직접 정지 → 신고 닫기 → 상품 내리기
                .andExpect(jsonPath("$.content[0].action").value("SUSPEND_USER"))
                .andExpect(jsonPath("$.content[0].targetName").value("판매자"))
                .andExpect(jsonPath("$.content[0].reportId").doesNotExist())
                .andExpect(jsonPath("$.content[0].adminNickname").value("운영자"))
                .andExpect(jsonPath("$.content[1].action").value("RESOLVE_REPORT"))
                .andExpect(jsonPath("$.content[1].targetName").value("신고 #" + reportId))
                .andExpect(jsonPath("$.content[2].action").value("DELETE_PRODUCT"))
                .andExpect(jsonPath("$.content[2].targetName").value("기록 확인용 상품"))
                .andExpect(jsonPath("$.content[2].reportId").value(reportId));
    }

    @Test
    void 조치_종류로_거를_수_있고_커서로_이어진다() throws Exception {
        adminPost("/api/admin/users/" + seller.getId() + "/suspend", "{ \"reason\": \"정지 1\" }");
        adminPost("/api/admin/users/" + seller.getId() + "/unsuspend", "{ \"reason\": \"해제\" }");
        adminPost("/api/admin/users/" + seller.getId() + "/suspend", "{ \"reason\": \"정지 2\" }");

        String first = mvc.perform(get("/api/admin/actions").param("action", "SUSPEND_USER").param("size", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.content[0].reason").value("정지 2"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(first, "$.nextCursor");

        mvc.perform(get("/api/admin/actions").param("action", "SUSPEND_USER").param("size", "1")
                        .param("cursor", cursor).header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.content[0].reason").value("정지 1"));
    }

    @Test
    void 현황판은_처리_전_신고_정지_가입_등록_거래완료를_센다() throws Exception {
        String before = mvc.perform(get("/api/admin/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        reports.saveAndFlush(Report.of(buyer, ReportTarget.USER, seller.getId(), ReportReason.ABUSE, null));
        adminPost("/api/admin/users/" + buyer.getId() + "/suspend", "{ \"reason\": \"정지\" }");
        users.save(User.builder().email("new@test.com").password("hash").nickname("새회원").build());
        Trade trade = trades.save(Trade.request(product, null, buyer));
        trade.completeInPerson();
        em.flush();
        em.clear();

        String after = mvc.perform(get("/api/admin/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(delta(before, after, "pendingReports")).isEqualTo(1);
        assertThat(delta(before, after, "suspendedUsers")).isEqualTo(1);
        assertThat(delta(before, after, "newUsersToday")).isEqualTo(1);
        assertThat(delta(before, after, "completedTradesLast7Days")).isEqualTo(1);
        // 준비 단계에서 만든 상품·회원은 before 에 이미 들어 있다.
        assertThat(((Number) JsonPath.read(after, "$.newProductsToday")).longValue()).isGreaterThanOrEqualTo(1);
    }

    private static long delta(String before, String after, String field) {
        return ((Number) JsonPath.read(after, "$." + field)).longValue()
                - ((Number) JsonPath.read(before, "$." + field)).longValue();
    }
}
