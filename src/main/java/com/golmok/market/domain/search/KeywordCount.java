package com.golmok.market.domain.search;

/** 검색어별 집계 결과. JPQL 생성자 표현식이 COUNT 를 Long 으로 넘기므로 래퍼 타입이다. */
public record KeywordCount(String keyword, Long count) {
}
