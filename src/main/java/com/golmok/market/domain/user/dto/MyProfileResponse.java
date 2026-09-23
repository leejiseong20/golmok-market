package com.golmok.market.domain.user.dto;

import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRole;

import java.math.BigDecimal;
import java.util.List;

/**
 * 마이페이지 상단 정보.
 *
 * 명세의 email 은 아직 내려주지 않는다. 화면에서 쓰지 않는 값을 굳이 응답에 담지 않는다.
 * regions 는 동네 인증 기능이 생기면서 함께 내려준다.
 *
 * @param admin 관리자인가. 설정 화면의 "관리자" 진입만 가리려는 용도라 **권한 이름 대신 true/false 만** 준다.
 *              화면 숨김은 보조 수단이고, 실제 차단은 서버가 `/api/admin/**` 에서 한다.
 */
public record MyProfileResponse(
        Long id, String nickname, String profileImageUrl, BigDecimal mannerTemp,
        List<MyRegionResponse> regions, boolean admin) {

    public static MyProfileResponse from(User user, List<MyRegionResponse> regions) {
        return new MyProfileResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(),
                user.getMannerTemp(), regions, user.getRole() == UserRole.ADMIN);
    }
}
