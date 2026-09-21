package com.golmok.market.domain.user.dto;

import java.math.BigDecimal;

/**
 * 공개 프로필.
 *
 * @param blockedByMe 보는 사람이 이 회원을 차단했는가. 화면이 "차단하기"와 "차단 해제" 중 무엇을 보일지 정한다.
 *                    비로그인이면 항상 false. 상대가 나를 차단했는지는 알려주지 않는다(차단당한 사실은 드러나지 않아야 한다).
 */
public record UserProfileResponse(Long id, String nickname, String profileImageUrl, BigDecimal mannerTemp,
                                  long productCount, long reviewCount, boolean blockedByMe) {
}
