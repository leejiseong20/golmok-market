package com.golmok.market.domain.user.dto;

import com.golmok.market.domain.user.User;

import java.math.BigDecimal;

/**
 * 마이페이지 상단 정보.
 *
 * 명세의 GET /api/users/me 에는 email 과 인증 동네(regions)도 있지만 지금은 내려주지 않는다.
 * 동네 인증 API 가 아직 없어 regions 는 항상 빈 배열이고, 빈 값을 내려주면
 * 프론트가 "동네가 없다"와 "기능이 없다"를 구분할 수 없다. 동네 인증과 함께 추가한다.
 */
public record MyProfileResponse(Long id, String nickname, String profileImageUrl, BigDecimal mannerTemp) {

    public static MyProfileResponse from(User user) {
        return new MyProfileResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getMannerTemp());
    }
}
