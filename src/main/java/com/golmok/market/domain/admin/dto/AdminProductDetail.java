package com.golmok.market.domain.admin.dto;

import java.util.List;

/**
 * 상품 상세. 삭제한 상품은 일반 상세 API 로 볼 수 없어 관리자용으로 따로 준다.
 *
 * @param actions 이 상품에 한 관리자 조치. 최근 것부터
 */
public record AdminProductDetail(AdminProductSummary product, String description, List<AdminActionResponse> actions) {
}
