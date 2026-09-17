package com.golmok.market.domain.search.dto;

/** 인기 검색어 한 줄. rank 는 1부터. */
public record PopularKeywordResponse(int rank, String keyword, long count) {
}
