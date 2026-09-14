package com.golmok.market.global.pagination;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class CursorResponseTest {

    /** 정렬값 = price, id 로 커서를 만드는 가짜 항목 */
    record Item(long id, int price) {
    }

    private static List<Item> items(int count) {
        return LongStream.rangeClosed(1, count)
                .mapToObj(id -> new Item(id, (int) id * 1000))
                .toList();
    }

    private static Cursor cursorOf(Item item) {
        return Cursor.of(item.price(), item.id());
    }

    @Test
    void size_보다_한_건_더_조회되면_잘라내고_다음_페이지가_있다() {
        PageSize pageSize = PageSize.of(3);

        CursorResponse<Item> response = CursorResponse.of(items(4), pageSize, CursorResponseTest::cursorOf);

        assertThat(response.content()).extracting(Item::id).containsExactly(1L, 2L, 3L);
        assertThat(response.hasNext()).isTrue();
        // 다음 커서는 "잘라낸 뒤의 마지막 항목"이어야 한다. 4번이 아니라 3번.
        assertThat(response.nextCursor()).isEqualTo("3000_3");
    }

    @Test
    void 정확히_size_만큼_조회되면_마지막_페이지다() {
        CursorResponse<Item> response = CursorResponse.of(items(3), PageSize.of(3), CursorResponseTest::cursorOf);

        assertThat(response.content()).hasSize(3);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void 결과가_없으면_빈_목록이고_다음_페이지가_없다() {
        CursorResponse<Item> response = CursorResponse.of(List.of(), PageSize.of(3), CursorResponseTest::cursorOf);

        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void map_은_커서와_hasNext_를_유지한다() {
        CursorResponse<Item> response = CursorResponse.of(items(4), PageSize.of(3), CursorResponseTest::cursorOf);

        CursorResponse<String> mapped = response.map(item -> "상품" + item.id());

        assertThat(mapped.content()).containsExactly("상품1", "상품2", "상품3");
        assertThat(mapped.nextCursor()).isEqualTo("3000_3");
        assertThat(mapped.hasNext()).isTrue();
    }

    @Test
    void PageSize_는_범위를_벗어나면_에러_대신_보정한다() {
        assertThat(PageSize.of(null).value()).isEqualTo(20);
        assertThat(PageSize.of(0).value()).isEqualTo(20);
        assertThat(PageSize.of(-5).value()).isEqualTo(20);
        assertThat(PageSize.of(30).value()).isEqualTo(30);
        assertThat(PageSize.of(51).value()).isEqualTo(50);
        assertThat(PageSize.of(1000).value()).isEqualTo(50);
        assertThat(PageSize.of(20).fetchSize()).isEqualTo(21);
    }
}
