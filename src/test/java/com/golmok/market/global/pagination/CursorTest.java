package com.golmok.market.global.pagination;

import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorTest {

    @Test
    void 날짜_커서는_명세_형식으로_인코딩된다() {
        Cursor cursor = Cursor.of(LocalDateTime.of(2026, 9, 14, 18, 20, 0), 47L);

        // LocalDateTime.toString() 은 초가 0 이면 "18:20" 으로 초를 생략한다. 그러면 안 된다.
        assertThat(cursor.encode()).isEqualTo("2026-09-14T18:20:00_47");
    }

    @Test
    void 날짜_커서는_인코딩_후_파싱하면_원래_값이_된다() {
        LocalDateTime bumpedAt = LocalDateTime.of(2026, 9, 14, 18, 20, 31);

        Cursor parsed = Cursor.parse(Cursor.of(bumpedAt, 47L).encode());

        assertThat(parsed.valueAsDateTime()).isEqualTo(bumpedAt);
        assertThat(parsed.id()).isEqualTo(47L);
    }

    @Test
    void 숫자_커서는_인코딩_후_파싱하면_원래_값이_된다() {
        Cursor parsed = Cursor.parse(Cursor.of(80000, 47L).encode());

        assertThat(parsed.valueAsLong()).isEqualTo(80000L);
        assertThat(parsed.id()).isEqualTo(47L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void cursor_가_없으면_첫_페이지로_보고_null_을_반환한다(String raw) {
        assertThat(Cursor.parse(raw)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-09-14T18:20:00",     // 구분자 없음
            "_47",                     // 정렬값 없음
            "2026-09-14T18:20:00_",    // id 없음
            "2026-09-14T18:20:00_abc", // id 가 숫자 아님
            "2026-09-14T18:20:00_0",   // id 는 양수
            "2026-09-14T18:20:00_-3"
    })
    void 형식이_틀린_cursor_는_INVALID_INPUT(String raw) {
        assertThatThrownBy(() -> Cursor.parse(raw))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 정렬값_타입이_기대와_다르면_INVALID_INPUT() {
        // 최신순 커서를 가격순 API 에 보낸 경우
        Cursor cursor = Cursor.parse("2026-09-14T18:20:00_47");

        assertThatThrownBy(cursor::valueAsLong)
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Cursor.parse("80000_47").valueAsDateTime())
                .isInstanceOf(BusinessException.class);
    }
}
