package com.golmok.market.domain.admin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTextTest {

    @Test
    void 이메일은_앞_두_글자와_도메인만_남긴다() {
        assertThat(AdminText.maskEmail("demo4@golmok.test")).isEqualTo("de***@golmok.test");
    }

    @Test
    void 앞부분이_두_글자_이하면_한_글자만_남긴다() {
        assertThat(AdminText.maskEmail("ab@b.com")).isEqualTo("a***@b.com");
        assertThat(AdminText.maskEmail("a@b")).isEqualTo("a***@b");
    }

    @Test
    void 이메일_모양이_아니면_전부_가린다() {
        assertThat(AdminText.maskEmail("no-at-sign")).isEqualTo("***");
        assertThat(AdminText.maskEmail("@nolocal.com")).isEqualTo("***");
        assertThat(AdminText.maskEmail(null)).isNull();
    }

    @Test
    void 검색어의_퍼센트_밑줄_이스케이프_문자를_글자_그대로_찾게_바꾼다() {
        assertThat(AdminText.likeKeyword(" 50%_할인! ")).isEqualTo("50!%!_할인!!");
        assertThat(AdminText.likeKeyword("  ")).isNull();
        assertThat(AdminText.likeKeyword(null)).isNull();
    }
}
