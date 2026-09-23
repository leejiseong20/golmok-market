package com.golmok.market.domain.admin.dto;

import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductStatus;

import java.time.LocalDateTime;

/**
 * 상품 목록 한 줄(삭제한 상품 포함).
 *
 * @param deletedByAdmin 마지막으로 관리자가 내렸는지. 이것이 true 일 때만 되살릴 수 있다
 *                       (판매자가 직접 지운 상품을 관리자가 되돌리면 판매자의 뜻을 뒤집는다).
 * @param reportCount    이 상품에 들어온 신고 수(처리 여부와 상관없이)
 */
public record AdminProductSummary(Long id, String title, int price, ProductStatus status, String thumbnailUrl,
                                  Long sellerId, String sellerNickname, boolean deleted, boolean deletedByAdmin,
                                  long reportCount, LocalDateTime createdAt) {

    public static AdminProductSummary of(Product product, String thumbnailUrl, boolean deletedByAdmin, long reportCount) {
        return new AdminProductSummary(product.getId(), product.getTitle(), product.getPrice(), product.getStatus(),
                thumbnailUrl, product.getSeller().getId(), product.getSeller().getNickname(), product.isDeleted(),
                product.isDeleted() && deletedByAdmin, reportCount, product.getCreatedAt());
    }
}
