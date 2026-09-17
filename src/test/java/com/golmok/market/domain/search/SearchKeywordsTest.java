package com.golmok.market.domain.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchKeywordsTest {

    @Test
    void 앞뒤_공백과_연속_공백을_정리하고_영문은_소문자로_맞춘다() {
        assertThat(SearchKeywords.normalize("  에어팟 ")).isEqualTo("에어팟");
        assertThat(SearchKeywords.normalize("원목   식탁\t의자")).isEqualTo("원목 식탁 의자");
        assertThat(SearchKeywords.normalize("iPad Pro")).isEqualTo("ipad pro");
    }

    @Test
    void 비었거나_공백뿐이면_기록하지_않는다() {
        assertThat(SearchKeywords.normalize(null)).isNull();
        assertThat(SearchKeywords.normalize("")).isNull();
        assertThat(SearchKeywords.normalize("   \t ")).isNull();
    }

    @Test
    void 저장_컬럼_길이를_넘지_않는다() {
        assertThat(SearchKeywords.normalize("가".repeat(60))).hasSize(SearchKeywords.MAX_LENGTH);
    }
}
