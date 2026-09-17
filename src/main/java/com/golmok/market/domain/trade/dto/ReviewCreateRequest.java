package com.golmok.market.domain.trade.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewCreateRequest(
        @NotNull(message = "평점을 선택해 주세요.")
        @Min(value = 1, message = "평점은 1~5 사이여야 합니다.")
        @Max(value = 5, message = "평점은 1~5 사이여야 합니다.") Integer score,
        @Size(max = 500, message = "후기는 500자 이하여야 합니다.") String content
) {
}
