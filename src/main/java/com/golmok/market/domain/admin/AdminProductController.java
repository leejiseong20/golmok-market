package com.golmok.market.domain.admin;

import com.golmok.market.domain.admin.dto.AdminProductDetail;
import com.golmok.market.domain.admin.dto.AdminProductSummary;
import com.golmok.market.domain.admin.dto.AdminReasonRequest;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 관리(관리자 전용). 삭제한 상품도 보인다. 권한 검사와 404 규칙은 AdminReportController 와 같다.
 */
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@Validated
public class AdminProductController {

    private final AdminProductService adminProductService;

    /** 상품 목록. q 는 제목 부분 일치, deleted 는 true(삭제만)·false(보이는 것만)·생략(전부). */
    @GetMapping
    public CursorResponse<AdminProductSummary> list(@RequestParam(required = false) String q,
                                                    @RequestParam(required = false)
                                                    @Positive(message = "판매자 번호가 올바르지 않습니다.") Long sellerId,
                                                    @RequestParam(required = false) Boolean deleted,
                                                    @RequestParam(required = false) String cursor,
                                                    @RequestParam(required = false) Integer size) {
        return adminProductService.list(q, sellerId, deleted, cursor, size);
    }

    @GetMapping("/{id}")
    public AdminProductDetail detail(@PathVariable @Positive(message = "상품 번호가 올바르지 않습니다.") Long id) {
        return adminProductService.detail(id);
    }

    @PostMapping("/{id}/delete")
    public AdminProductDetail delete(@AuthenticationPrincipal AuthUser viewer,
                                     @PathVariable @Positive(message = "상품 번호가 올바르지 않습니다.") Long id,
                                     @Valid @RequestBody AdminReasonRequest request) {
        return adminProductService.delete(viewer, id, request.reason());
    }

    /** 관리자가 내린 상품만 되살린다. 판매자가 직접 지웠거나 판매자가 탈퇴했으면 409. */
    @PostMapping("/{id}/restore")
    public AdminProductDetail restore(@AuthenticationPrincipal AuthUser viewer,
                                      @PathVariable @Positive(message = "상품 번호가 올바르지 않습니다.") Long id,
                                      @Valid @RequestBody AdminReasonRequest request) {
        return adminProductService.restore(viewer, id, request.reason());
    }
}
