package com.golmok.market.domain.user.dto;

import com.golmok.market.domain.user.UserRegion;

/**
 * 내가 인증한 동네.
 *
 * id 는 user_regions 의 id 가 아니라 **동네(region) id** 다.
 * 삭제·대표 지정 API 의 경로가 /api/users/me/regions/{regionId} 이므로 화면이 그대로 쓸 수 있어야 한다.
 */
public record MyRegionResponse(Long id, String name, boolean isPrimary, int verifyCount) {

    public static MyRegionResponse from(UserRegion userRegion) {
        return new MyRegionResponse(userRegion.getRegion().getId(), userRegion.getRegion().getDong(),
                userRegion.isPrimary(), userRegion.getVerifyCount());
    }
}
