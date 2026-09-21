package com.golmok.market.domain.report;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.notification.NotificationRepository;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired RegionRepository regions;
    @Autowired ReportRepository reports;
    @Autowired NotificationRepository notifications;
    @Autowired JwtTokenProvider tokens;

    User reporter, seller;
    Product product;
    String token;

    @BeforeEach
    void 준비() {
        reporter = users.save(User.builder().email("reporter@test.com").password("hash").nickname("신고한사람").build());
        seller = users.save(User.builder().email("seller@test.com").password("hash").nickname("신고당한사람").build());
        token = tokens.createAccessToken(reporter.getId(), reporter.getRole());
        Category category = categories.save(Category.create(null, "신고카테고리", null, 1));
        Region region = regions.save(Region.create("서울", "강남", "역삼동", 37.5, 127.0));
        product = products.save(Product.builder().seller(seller).category(category).region(region)
                .title("신고할 상품").description("신고 테스트용 상품입니다.").price(10000).build());
    }

    private Map<String, Object> body(String targetType, long targetId, String reason) {
        return new HashMap<>(Map.of("targetType", targetType, "targetId", targetId, "reason", reason));
    }

    private ResultActions send(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/reports").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    @Test
    void 상품을_신고하면_기록이_남는다() throws Exception {
        send(body("PRODUCT", product.getId(), "FRAUD"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetType").value("PRODUCT"))
                .andExpect(jsonPath("$.targetId").value(product.getId()))
                .andExpect(jsonPath("$.reason").value("FRAUD"));

        assertThat(reports.existsByReporterIdAndTargetTypeAndTargetId(
                reporter.getId(), ReportTarget.PRODUCT, product.getId())).isTrue();
    }

    @Test
    void 사용자를_신고할_수_있다() throws Exception {
        send(body("USER", seller.getId(), "ABUSE")).andExpect(status().isCreated());
        assertThat(reports.existsByReporterIdAndTargetTypeAndTargetId(
                reporter.getId(), ReportTarget.USER, seller.getId())).isTrue();
    }

    /** 신고당한 사람에게 알림이 가면 누가 신고했는지 짐작할 수 있고 보복으로 이어진다. */
    @Test
    void 신고해도_상대에게_알리지_않는다() throws Exception {
        send(body("PRODUCT", product.getId(), "SPAM")).andExpect(status().isCreated());
        assertThat(notifications.findAll()).noneMatch(each -> each.getUser().getId().equals(seller.getId()));
    }

    @Test
    void 같은_대상을_두_번_신고할_수_없다() throws Exception {
        send(body("PRODUCT", product.getId(), "SPAM")).andExpect(status().isCreated());
        send(body("PRODUCT", product.getId(), "FRAUD"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));
    }

    /** 대상 종류가 다르면 별개다. 같은 사람의 상품과 사람 자체를 각각 신고할 수 있다. */
    @Test
    void 대상_종류가_다르면_따로_신고된다() throws Exception {
        send(body("PRODUCT", product.getId(), "SPAM")).andExpect(status().isCreated());
        send(body("USER", seller.getId(), "SPAM")).andExpect(status().isCreated());
    }

    @Test
    void 자기_자신과_자기_상품은_신고할_수_없다() throws Exception {
        String sellerToken = tokens.createAccessToken(seller.getId(), seller.getRole());
        mvc.perform(post("/api/reports").header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body("PRODUCT", product.getId(), "SPAM"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_REPORT_SELF"));

        send(body("USER", reporter.getId(), "SPAM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_REPORT_SELF"));
    }

    @Test
    void 없는_대상은_신고할_수_없다() throws Exception {
        send(body("PRODUCT", 999999L, "SPAM")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        send(body("USER", 999999L, "SPAM")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    /** 탈퇴한 회원은 공개 프로필과 같은 기준으로 "없는 사용자"다. */
    @Test
    void 탈퇴한_회원은_신고할_수_없다() throws Exception {
        seller.withdraw(LocalDateTime.now());
        users.saveAndFlush(seller);
        send(body("USER", seller.getId(), "SPAM")).andExpect(status().isNotFound());
    }

    @Test
    void 삭제된_상품은_신고할_수_없다() throws Exception {
        product.softDelete();
        products.saveAndFlush(product);
        send(body("PRODUCT", product.getId(), "SPAM")).andExpect(status().isNotFound());
    }

    @Test
    void 기타_사유는_설명을_적어야_한다() throws Exception {
        Map<String, Object> onlyBlank = body("PRODUCT", product.getId(), "OTHER");
        onlyBlank.put("detail", "   ");   // 공백만 적은 것은 안 적은 것과 같다
        send(onlyBlank).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_DETAIL_REQUIRED"));

        Map<String, Object> withDetail = body("PRODUCT", product.getId(), "OTHER");
        withDetail.put("detail", "  다른 사람 사진을 도용했어요  ");
        send(withDetail).andExpect(status().isCreated());
        assertThat(reports.findAll().getFirst().getDetail()).isEqualTo("다른 사람 사진을 도용했어요");
    }

    @Test
    void 필수값이_빠지면_사람이_읽을_문구를_준다() throws Exception {
        Map<String, Object> noReason = body("PRODUCT", product.getId(), "SPAM");
        noReason.remove("reason");
        send(noReason).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"))
                .andExpect(jsonPath("$.errors[0].reason").value("신고 사유를 선택해 주세요."));
    }

    @Test
    void 로그인하지_않으면_신고할_수_없다() throws Exception {
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body("PRODUCT", product.getId(), "SPAM"))))
                .andExpect(status().isUnauthorized());
    }
}
