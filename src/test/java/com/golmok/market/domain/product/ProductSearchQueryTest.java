package com.golmok.market.domain.product;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 검색어 → 불린 모드 식 변환. 실제 MySQL 에서의 검색 결과는 CLAUDE.md 의 검증 기록을 볼 것
 * (H2 에는 MATCH AGAINST 가 없어 여기서는 식이 올바르게 만들어지는지만 본다).
 */
class ProductSearchQueryTest {

    @Test
    void 한_단어는_구문으로_감싼다() {
        assertThat(ProductSearchQuery.booleanQuery("식탁")).hasValue("+\"식탁\"");
    }

    /** 띄어 쓴 단어는 모두 들어 있어야 한다. 순서와 띄어쓰기는 상관없다. */
    @Test
    void 여러_단어는_모두_포함해야_한다() {
        assertThat(ProductSearchQuery.booleanQuery("  원목   식탁 ")).hasValue("+\"원목\" +\"식탁\"");
    }

    /** 사용자가 쓴 연산자를 그대로 넘기면 "-식탁"(제외)·"식*"(접두)처럼 쿼리 의미가 바뀐다. */
    @Test
    void 불린_연산자는_지워서_단어_구분자로_쓴다() {
        assertThat(ProductSearchQuery.booleanQuery("-식탁")).hasValue("+\"식탁\"");
        assertThat(ProductSearchQuery.booleanQuery("아이폰+케이스")).hasValue("+\"아이폰\" +\"케이스\"");
        assertThat(ProductSearchQuery.booleanQuery("\"원목\" (식탁)")).hasValue("+\"원목\" +\"식탁\"");
        assertThat(ProductSearchQuery.booleanQuery("식탁*")).hasValue("+\"식탁\"");
    }

    /** ngram 색인 단위가 2글자라 1글자는 FULLTEXT 로 찾을 수 없다. 호출한 쪽이 LIKE 로 찾는다. */
    @Test
    void 한_글자_단어가_섞이면_FULLTEXT_를_쓰지_않는다() {
        assertThat(ProductSearchQuery.booleanQuery("책")).isEmpty();
        assertThat(ProductSearchQuery.booleanQuery("원목 책")).isEmpty();
    }

    /** 이모지처럼 두 코드 유닛인 글자도 한 글자로 센다. length() 로 세면 2글자로 잘못 본다. */
    @Test
    void 글자_수는_코드포인트로_센다() {
        assertThat(ProductSearchQuery.booleanQuery("🍎")).isEmpty();
    }

    @Test
    void 연산자만_있거나_비어_있으면_FULLTEXT_를_쓰지_않는다() {
        assertThat(ProductSearchQuery.booleanQuery("+-*")).isEmpty();
        assertThat(ProductSearchQuery.booleanQuery("   ")).isEmpty();
        assertThat(ProductSearchQuery.booleanQuery(null)).isEmpty();
    }

    @Test
    void 영문과_숫자도_그대로_찾는다() {
        assertThat(ProductSearchQuery.booleanQuery("iPad 10")).hasValue("+\"iPad\" +\"10\"");
    }
}
