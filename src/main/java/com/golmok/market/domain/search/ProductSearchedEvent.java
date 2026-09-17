package com.golmok.market.domain.search;

/**
 * 상품 목록을 검색어로 조회했다(첫 페이지). 검색 응답과 분리해 로그로 남기기 위한 이벤트.
 *
 * @param userId 비로그인이면 null
 * @param keyword 정규화된 검색어
 */
public record ProductSearchedEvent(Long userId, long regionId, String keyword) {
}
