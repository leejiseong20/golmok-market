package com.golmok.market.global.security;

import com.golmok.market.domain.user.UserRole;

/**
 * 인증된 요청의 사용자 정보. 컨트롤러에서 {@code @AuthenticationPrincipal AuthUser} 로 받는다.
 *
 * User 엔티티를 그대로 principal 로 두지 않는 이유:
 * access token 만으로 인증하므로 요청마다 DB 를 조회하지 않는다.
 * 토큰에 든 최소 정보(id, role)만 들고 다니고, 필요한 서비스에서만 User 를 조회한다.
 */
public record AuthUser(Long id, UserRole role) {
}
