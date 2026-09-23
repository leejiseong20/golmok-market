package com.golmok.market.domain.clienterror;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 로그에 남기기 전 정리. 한 보고는 반드시 한 줄이어야 한다(가짜 로그 줄 끼워 넣기 방지). */
class ClientErrorLoggerTest {

    @Test
    void 줄바꿈을_넣어_가짜_로그_줄을_만들_수_없다() {
        String forged = "boom\n2026-09-23T10:00:00 INFO AuthService : 로그인 성공 admin@golmok.test";
        assertThat(ClientErrorLogger.clean(forged, 300))
                .doesNotContain("\n")
                .isEqualTo("boom 2026-09-23T10:00:00 INFO AuthService : 로그인 성공 admin@golmok.test");
    }

    @Test
    void 캐리지리턴_탭_유니코드_줄_구분자도_지운다() {
        String value = "a\rb\tc" + (char) 0x2028 + "d" + (char) 0x2029 + "e" + (char) 0x85 + "f" + (char) 0x1b + "[31mg";
        String cleaned = ClientErrorLogger.clean(value, 300);
        assertThat(cleaned).isEqualTo("a b c d e f [31mg");
        assertThat(cleaned.chars().noneMatch(Character::isISOControl)).isTrue();
    }

    @Test
    void 여러_줄_컴포넌트_스택은_한_줄로_잇는다() {
        assertThat(ClientErrorLogger.clean("\n    at AdminDashboard\n    at AdminApp\n", 2000))
                .isEqualTo("at AdminDashboard at AdminApp");
    }

    @Test
    void 길면_자르고_비었으면_대시() {
        assertThat(ClientErrorLogger.clean("가".repeat(10), 3)).isEqualTo("가가가…");
        assertThat(ClientErrorLogger.clean(null, 10)).isEqualTo("-");
        assertThat(ClientErrorLogger.clean(" \n\t ", 10)).isEqualTo("-");
    }
}
