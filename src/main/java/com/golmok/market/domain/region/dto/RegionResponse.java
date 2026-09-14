package com.golmok.market.domain.region.dto;

import com.golmok.market.domain.region.Region;

public record RegionResponse(Long id, String sido, String sigungu, String dong, String fullName) {

    public static RegionResponse from(Region region) {
        return new RegionResponse(region.getId(), region.getSido(), region.getSigungu(), region.getDong(), region.getFullName());
    }
}
