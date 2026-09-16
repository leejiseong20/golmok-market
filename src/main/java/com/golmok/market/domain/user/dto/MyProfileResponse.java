package com.golmok.market.domain.user.dto;

import com.golmok.market.domain.user.User;

import java.math.BigDecimal;
import java.util.List;

/**
 * 마이페이지 상단 정보.
 *
 * 명세의 email 은 아직 내려주지 않는다. 화면에서 쓰지 않는 값을 굳이 응답에 담지 않는다.
 * regions 는 동네 인증 기능이 생기면서 함께 내려준다.
 */
public record MyProfileResponse(
        Long id, String nickname, String profileImageUrl, BigDecimal mannerTemp, List<MyRegionResponse> regions) {

    public static MyProfileResponse from(User user, List<MyRegionResponse> regions) {
        return new MyProfileResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(),
                user.getMannerTemp(), regions);
    }
}
