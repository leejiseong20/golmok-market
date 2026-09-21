package com.golmok.market.domain.product;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 상품 검색어를 MySQL FULLTEXT 불린 모드 식으로 바꾼다.
 *
 * 인덱스는 ngram(2글자 단위)이다. 불린 모드에서 ngram 은 검색어를 글자 쌍의 "구문"으로 찾으므로
 * "식탁" 은 "원목식탁" 의 가운데서도 찾힌다. 즉 2글자 이상이면 LIKE '%식탁%' 과 같은 결과다.
 *
 * 띄어 쓴 단어는 모두 들어 있어야 한다(+원목 +식탁). LIKE 로는 "원목 식탁" 이 그 띄어쓰기 그대로
 * 붙어 있어야 찾혔다. 제목이 "식탁(원목)" 이어도 찾히는 편이 사용자에게 낫다(2026-09-21 결정).
 *
 * 순수 함수라 DB 없이 테스트한다. 실제 MySQL 동작은 로컬 MySQL 로 따로 확인했다(CLAUDE.md).
 */
public final class ProductSearchQuery {

    /** MySQL 기본 ngram_token_size. 이보다 짧은 단어는 색인에 없어 FULLTEXT 로 찾을 수 없다. */
    static final int NGRAM_SIZE = 2;

    /**
     * 불린 모드 연산자. 사용자가 입력한 그대로 넘기면 검색어로 쿼리 의미를 바꿀 수 있다
     * ("-식탁" 은 "식탁이 없는 것", "식*" 은 접두 검색). 공백으로 바꿔 단어를 나누는 구분자로 쓴다.
     */
    private static final Pattern OPERATORS = Pattern.compile("[+\\-<>()~*\"@]");

    private ProductSearchQuery() {
    }

    /**
     * FULLTEXT 로 찾을 수 있으면 불린 모드 식을, 없으면 비어 있는 값을 준다.
     * 비어 있으면 호출한 쪽이 LIKE 로 찾는다(1글자 단어가 섞였거나 연산자만 입력한 경우).
     */
    public static Optional<String> booleanQuery(String keyword) {
        if (keyword == null) {
            return Optional.empty();
        }
        List<String> terms = Arrays.stream(OPERATORS.matcher(keyword).replaceAll(" ").trim().split("\\s+"))
                .filter(term -> !term.isBlank())
                .toList();
        // 한 단어라도 1글자면 전체를 LIKE 로 찾는다. 섞어서 찾으면 결과의 의미가 단어마다 달라진다.
        if (terms.isEmpty() || terms.stream().anyMatch(term -> term.codePointCount(0, term.length()) < NGRAM_SIZE)) {
            return Optional.empty();
        }
        // 단어마다 따옴표로 감싸 구문으로 찾는다. 따옴표 없이 두면 ngram 이 쪼갠 글자 쌍 중 하나만 맞아도 찾힐 수 있다.
        return Optional.of(terms.stream().map(term -> "+\"" + term + "\"").collect(Collectors.joining(" ")));
    }
}
