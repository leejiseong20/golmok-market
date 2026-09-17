package com.golmok.market.domain.search;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 인기 검색어 집계용 검색어 정규화.
 *
 * "에어팟 " 과 "에어팟", "iPad" 와 "ipad" 가 따로 세어지면 순위가 쪼개진다.
 * 앞뒤 공백 제거 · 연속 공백 하나로 · 영문 소문자로 맞춘다. 한글에는 대소문자가 없어 영향이 없다.
 * 상품 검색 자체(LIKE)에는 쓰지 않는다. 검색 결과는 사용자가 입력한 그대로 찾는다.
 */
public final class SearchKeywords {

    public static final int MAX_LENGTH = 50;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private SearchKeywords() {
    }

    /** @return 정규화한 검색어. 비었거나 공백뿐이면 null(기록하지 않는다) */
    public static String normalize(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = WHITESPACE.matcher(keyword.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() > MAX_LENGTH ? normalized.substring(0, MAX_LENGTH) : normalized;
    }
}
