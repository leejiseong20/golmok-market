package com.golmok.market.domain.product;

import com.golmok.market.domain.product.dto.ProductDetailResponse;
import com.golmok.market.domain.product.dto.ProductSummaryResponse;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.security.AuthUser;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @InitBinder
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }

    @GetMapping
    public CursorResponse<ProductSummaryResponse> findPage(
            @RequestParam @Positive(message = "동네 ID는 양수여야 합니다.") long regionId,
            @RequestParam(required = false) @Positive(message = "카테고리 ID는 양수여야 합니다.") Long categoryId,
            @RequestParam(required = false) @Size(max = 50, message = "검색어는 50자 이하여야 합니다.") String keyword,
            @RequestParam(defaultValue = "LATEST") ProductSort sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal AuthUser viewer) {
        return productService.findPage(regionId, categoryId, keyword, sort, cursor, size, viewer);
    }

    @GetMapping("/{id}")
    public ProductDetailResponse findDetail(@PathVariable @Positive(message = "상품 ID는 양수여야 합니다.") long id,
                                           @AuthenticationPrincipal AuthUser viewer) {
        return productService.findDetail(id, viewer);
    }
}
