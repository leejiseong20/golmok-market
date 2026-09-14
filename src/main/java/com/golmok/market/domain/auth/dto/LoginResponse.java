package com.golmok.market.domain.auth.dto;

import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRegion;

public record LoginResponse(String accessToken, String refreshToken, UserInfo user) {

    public record UserInfo(Long id, String nickname, String profileImageUrl, RegionInfo primaryRegion) {
    }

    /** name 은 화면 표시용 동 이름. 예: "역삼동" */
    public record RegionInfo(Long id, String name) {
    }

    /**
     * @param primaryRegion 동네 인증 전이면 null. 응답에서도 primaryRegion: null 로 나간다.
     */
    public static LoginResponse of(TokenResponse tokens, User user, UserRegion primaryRegion) {
        RegionInfo region = primaryRegion == null
                ? null
                : new RegionInfo(primaryRegion.getRegion().getId(), primaryRegion.getRegion().getDong());
        UserInfo userInfo = new UserInfo(user.getId(), user.getNickname(), user.getProfileImageUrl(), region);
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), userInfo);
    }
}
