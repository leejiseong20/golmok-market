package com.golmok.market.domain.product.dto;

import com.golmok.market.domain.product.TradeType;
import jakarta.validation.constraints.*;
import java.util.List;

public record ProductWriteRequest(
        @NotBlank @Size(min = 2, max = 100) String title,
        @NotBlank @Size(min = 10) String description,
        @NotNull @Min(0) Integer price,
        @NotNull @Positive Long categoryId,
        @NotNull @Positive Long regionId,
        @NotNull Boolean isNegotiable,
        @NotNull TradeType tradeType,
        @NotNull @Size(min = 1, max = 10) List<@NotBlank @Size(max = 500) String> imageUrls
) {
    public ProductWriteRequest {
        title = title == null ? null : title.trim();
        description = description == null ? null : description.trim();
    }
}
