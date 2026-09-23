package com.golmok.market.domain.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 회원 상세. 정지 여부를 판단할 재료를 한 화면에 모은다.
 *
 * @param activeProductCount 지금 보이는 판매 상품 수
 * @param reportsOnUser      이 회원 자체에 들어온 신고 수
 * @param reportsOnProducts  이 회원의 상품들에 들어온 신고 수(삭제한 상품 포함)
 * @param actions            이 회원에게 한 관리자 조치. 최근 것부터
 */
public record AdminUserDetail(AdminUserSummary user, String profileImageUrl, BigDecimal mannerTemp,
                              long activeProductCount, long reportsOnUser, long reportsOnProducts,
                              List<AdminActionResponse> actions) {
}
