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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 상품 관리.
 *
 * 삭제한 상품까지 보는지, 직접 내리기·되살리기와 기록, 그리고 **판매자가 직접 지운 상품은 되살리지 못하는지**를 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminProductApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired RegionRepository regions;
    @Autowired ReportRepository reports;
    @Autowired AdminActionRepository adminActions;
    @Autowired JwtTokenProvider tokens;
    @Autowired EntityManager em;

    User admin, seller;
    Category category;
    Region region;
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
        adminToken = tokens.createAccessToken(admin.getId(), UserRole.ADMIN);
        userToken = tokens.createAccessToken(seller.getId(), seller.getRole());

        category = categories.save(Category.create(null, "상품관리카테고리", null, 1));
        region = regions.save(Region.create("서울", "강남", "역삼동", 37.5, 127.0));
        product = save("관리 확인용 원목 식탁");
    }

    private Product save(String title) {
        return products.save(Product.builder().seller(seller).category(category).region(region)
                .title(title).description("관리자 상품 관리 확인용입니다.").price(10000).build());
    }

    private ResultActions act(String path, long productId, String reason, String token) throws Exception {
        return mvc.perform(post("/api/admin/products/{id}/" + path, productId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reason == null ? "{}" : "{ \"reason\": \"" + reason + "\" }"));
    }

    // ---------- 권한 ----------

    @Test
    void 관리자가_아니면_404_다() throws Exception {
        mvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
        act("delete", product.getId(), "이유", userToken).andExpect(status().isNotFound());
        assertThat(products.findById(product.getId()).orElseThrow().isDeleted()).isFalse();
    }

    // ---------- 목록 ----------

    @Test
    void 목록은_삭제한_상품도_보여주고_삭제_여부로_거른다() throws Exception {
        Product gone = save("판매자가 지운 상품");
        gone.softDelete();
        products.saveAndFlush(gone);
        reports.saveAndFlush(Report.of(admin, ReportTarget.PRODUCT, product.getId(), ReportReason.FRAUD, null));

        mvc.perform(get("/api/admin/products").param("sellerId", String.valueOf(seller.getId()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("판매자가 지운 상품"))
                .andExpect(jsonPath("$.content[0].deleted").value(true))
                .andExpect(jsonPath("$.content[0].deletedByAdmin").value(false))
                .andExpect(jsonPath("$.content[1].reportCount").value(1))
                .andExpect(jsonPath("$.content[1].sellerNickname").value("판매자"));

        mvc.perform(get("/api/admin/products").param("sellerId", String.valueOf(seller.getId()))
                        .param("deleted", "true").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(gone.getId()));
        mvc.perform(get("/api/admin/products").param("q", "원목 식탁")
                        .param("deleted", "false").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.content[0].id").value(product.getId()));
    }

    @Test
    void 상세는_삭제한_상품도_설명과_조치_이력까지_보여준다() throws Exception {
        act("delete", product.getId(), "금지 물품", adminToken).andExpect(status().isOk());

        mvc.perform(get("/api/admin/products/{id}", product.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.deleted").value(true))
                .andExpect(jsonPath("$.description").value("관리자 상품 관리 확인용입니다."))
                .andExpect(jsonPath("$.actions[0].action").value("DELETE_PRODUCT"));
    }

    // ---------- 내리기·되살리기 ----------

    @Test
    void 내리고_되살리면_일반_목록에서_사라졌다가_돌아오고_기록이_남는다() throws Exception {
        act("delete", product.getId(), "사기 의심", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.deleted").value(true))
                .andExpect(jsonPath("$.product.deletedByAdmin").value(true));
        em.flush();
        mvc.perform(get("/api/products/{id}", product.getId())).andExpect(status().isNotFound());

        act("restore", product.getId(), "오해로 확인됨", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.deleted").value(false))
                .andExpect(jsonPath("$.product.deletedByAdmin").value(false))
                .andExpect(jsonPath("$.actions[0].action").value("RESTORE_PRODUCT"))
                .andExpect(jsonPath("$.actions[1].action").value("DELETE_PRODUCT"));
        em.flush();
        mvc.perform(get("/api/products/{id}", product.getId())).andExpect(status().isOk());
    }

    @Test
    void 판매자가_직접_지운_상품은_되살리지_않는다() throws Exception {
        product.softDelete();
        products.saveAndFlush(product);

        act("restore", product.getId(), "되살리기 시도", adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("판매자가 직접 지운 상품은 되살릴 수 없습니다."));
        assertThat(products.findById(product.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void 관리자가_되살린_뒤_판매자가_지웠으면_되살리지_않는다() throws Exception {
        act("delete", product.getId(), "내림", adminToken).andExpect(status().isOk());
        act("restore", product.getId(), "되살림", adminToken).andExpect(status().isOk());
        em.flush();
        em.clear();
        Product mine = products.findById(product.getId()).orElseThrow();
        mine.softDelete();
        products.saveAndFlush(mine);

        // 마지막 관리자 조치가 "되살리기"이므로, 지금 삭제는 판매자의 뜻이다.
        act("restore", product.getId(), "다시 되살리기 시도", adminToken)
                .andExpect(status().isConflict());
    }

    @Test
    void 탈퇴한_판매자의_상품은_되살리지_않는다() throws Exception {
        act("delete", product.getId(), "내림", adminToken).andExpect(status().isOk());
        em.flush();
        em.clear();
        User gone = users.findById(seller.getId()).orElseThrow();
        gone.withdraw(LocalDateTime.now());
        users.saveAndFlush(gone);

        act("restore", product.getId(), "되살리기 시도", adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("탈퇴한 회원의 상품은 되살릴 수 없습니다."));
    }

    @Test
    void 이미_삭제된_상품을_내리거나_보이는_상품을_되살리면_409_다() throws Exception {
        act("restore", product.getId(), "되살리기", adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("삭제된 상품이 아닙니다."));
        act("delete", product.getId(), "내림", adminToken).andExpect(status().isOk());
        act("delete", product.getId(), "또 내림", adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 삭제된 상품입니다."));

        assertThat(adminActions.findByTargetTypeAndTargetIdOrderByIdDesc(AdminActionTarget.PRODUCT, product.getId()))
                .hasSize(1);
    }

    @Test
    void 이유가_없으면_내리지_않는다() throws Exception {
        act("delete", product.getId(), null, adminToken).andExpect(status().isBadRequest());
        assertThat(products.findById(product.getId()).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    void 신고_처리로_내린_상품도_되살릴_수_있다() throws Exception {
        long reportId = reports.saveAndFlush(Report.of(admin, ReportTarget.PRODUCT, product.getId(),
                ReportReason.PROHIBITED, null)).getId();
        mvc.perform(post("/api/admin/reports/{id}/resolve", reportId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"action\": \"DELETE_PRODUCT\", \"reason\": \"금지 물품\" }"))
                .andExpect(status().isOk());

        act("restore", product.getId(), "금지 물품이 아니었음", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.deleted").value(false));
    }
}
