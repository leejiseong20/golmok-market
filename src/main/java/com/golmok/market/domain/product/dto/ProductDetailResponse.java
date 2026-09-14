package com.golmok.market.domain.product.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductImage;
import com.golmok.market.domain.product.ProductStatus;
import com.golmok.market.domain.product.TradeType;
import com.golmok.market.domain.user.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductDetailResponse(
        Long id, String title, String description, int price, boolean isNegotiable,
        ProductStatus status, TradeType tradeType, Long categoryId, String categoryName, String regionName,
        List<ImageInfo> images, SellerInfo seller, int viewCount, int favoriteCount, int chatCount,
        boolean isLiked, boolean isMine,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt
) {

    public record ImageInfo(Long id, String imageUrl, int sortOrder) {
    }

    public record SellerInfo(Long id, String nickname, String profileImageUrl, BigDecimal mannerTemp) {
    }

    public static ProductDetailResponse from(Product product, List<ProductImage> images, boolean isLiked, boolean isMine) {
        User seller = product.getSeller();
        return new ProductDetailResponse(product.getId(), product.getTitle(), product.getDescription(), product.getPrice(),
                product.isNegotiable(), product.getStatus(), product.getTradeType(), product.getCategory().getId(),
                product.getCategory().getName(), product.getRegion().getDong(),
                images.stream().map(image -> new ImageInfo(image.getId(), image.getImageUrl(), image.getSortOrder())).toList(),
                new SellerInfo(seller.getId(), seller.getNickname(), seller.getProfileImageUrl(), seller.getMannerTemp()),
                product.getViewCount(), product.getFavoriteCount(), product.getChatCount(), isLiked, isMine, product.getCreatedAt());
    }
}
