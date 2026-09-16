package com.golmok.market.domain.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** 브라우저 위치 좌표. 가장 가까운 동네로 인증된다. */
public record RegionVerifyRequest(

        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90", message = "위도는 90 이하여야 합니다.")
        Double lat,

        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 합니다.")
        Double lng
) {
}
