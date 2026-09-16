package com.golmok.market.domain.product.dto;

import com.golmok.market.domain.product.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record ProductStatusRequest(@NotNull ProductStatus status) { }
