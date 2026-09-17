package com.golmok.market.domain.trade.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.golmok.market.domain.product.ProductStatus;
import com.golmok.market.domain.trade.Trade;
import com.golmok.market.domain.trade.TradeStatus;

import java.time.LocalDateTime;

/**
 * 구매내역 한 건.
 *
 * canConfirm 을 서버가 계산해 내려준다. "지금 구매확정을 누를 수 있는가"는
 * TradeStatus.isBuyerConfirmable() 이 정하는 것이라, 프론트가 상태 목록을 따로 들고 있으면
 * 규칙이 두 곳으로 갈라진다.
 */
public record PurchaseResponse(
        Long tradeId,
        TradeStatus status,
        int amount,
        boolean canConfirm,
        boolean canReview,
        ProductSummary product,
        SellerSummary seller,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime completedAt
) {

    /** 삭제된 상품도 구매내역에는 남으므로 deleted 를 함께 내려 화면에서 구분할 수 있게 한다. */
    public record ProductSummary(Long id, String title, String thumbnailUrl, ProductStatus status, boolean deleted) {
    }

    public record SellerSummary(Long id, String nickname) {
    }

    public static PurchaseResponse from(Trade trade, String thumbnailUrl, boolean canReview) {
        var product = trade.getProduct();
        return new PurchaseResponse(
                trade.getId(),
                trade.getStatus(),
                trade.getAmount(),
                trade.getStatus().isBuyerConfirmable(),
                canReview,
                new ProductSummary(product.getId(), product.getTitle(), thumbnailUrl, product.getStatus(), product.isDeleted()),
                new SellerSummary(trade.getSeller().getId(), trade.getSeller().getNickname()),
                trade.getCreatedAt(),
                trade.getCompletedAt());
    }
}
