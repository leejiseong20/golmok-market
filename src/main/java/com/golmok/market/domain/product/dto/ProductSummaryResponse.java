package com.golmok.market.domain.product.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductStatus;

import java.time.LocalDateTime;

public record ProductSummaryResponse(
        Long id, String title, int price, Long categoryId, String categoryName,
        String regionName, String sellerNickname, String thumbnailUrl, ProductStatus status,
        int favoriteCount, int chatCount, boolean isLiked,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime bumpedAt
) {

    public static ProductSummaryResponse from(Product product, String thumbnailUrl, boolean isLiked) {
        return new ProductSummaryResponse(product.getId(), product.getTitle(), product.getPrice(),
                product.getCategory().getId(), product.getCategory().getName(), product.getRegion().getDong(),
                product.getSeller().getNickname(), thumbnailUrl, product.getStatus(), product.getFavoriteCount(),
                product.getChatCount(), isLiked, product.getCreatedAt(), product.getBumpedAt());
    }
}
