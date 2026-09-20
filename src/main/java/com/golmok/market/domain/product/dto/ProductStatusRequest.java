package com.golmok.market.domain.product.dto;

import com.golmok.market.domain.product.ProductStatus;
import jakarta.validation.constraints.NotNull;

/** 상태 변경. 기본 메시지("널이어서는 안됩니다")는 사용자용이 아니라 직접 적는다. */
public record ProductStatusRequest(
        @NotNull(message = "변경할 상태를 선택해 주세요.") ProductStatus status
) { }
