package com.golmok.market.global.pagination;

import java.util.List;
import java.util.function.Function;

/**
 * 커서 페이징 응답.
 *
 * <pre>
 * { "content": [ ... ], "nextCursor": "2026-09-14T18:20:00_47", "hasNext": true }
 * </pre>
 *
 * 다음 페이지 여부는 COUNT 쿼리 없이 판단한다.
 * size 보다 1건 더 조회해서({@link PageSize#fetchSize()}) 실제로 1건이 더 오면 다음 페이지가 있는 것이다.
 * 전체 개수는 무한 스크롤에 필요 없고, 큰 테이블에서 COUNT 는 목록 조회만큼 비싸다.
 *
 * 사용 예:
 * <pre>
 * List&lt;Product&gt; fetched = repository.findPage(cursor, pageSize.fetchSize());
 * return CursorResponse.of(fetched, pageSize, p -&gt; Cursor.of(p.getBumpedAt(), p.getId()))
 *                      .map(ProductSummaryResponse::from);
 * </pre>
 * 커서는 엔티티에서 뽑고 DTO 변환은 그 다음에 하도록 두 단계로 나눴다.
 * DTO 에 정렬 기준 필드가 없어도 커서를 만들 수 있다.
 */
public record CursorResponse<T>(List<T> content, String nextCursor, boolean hasNext) {

    public CursorResponse {
        content = List.copyOf(content);
    }

    /**
     * @param fetched  size + 1 건으로 조회한 결과
     * @param pageSize 요청 크기
     * @param cursorOf 항목에서 커서를 만드는 방법. 목록의 정렬 기준과 반드시 같아야 한다.
     */
    public static <T> CursorResponse<T> of(List<T> fetched, PageSize pageSize, Function<T, Cursor> cursorOf) {
        int size = pageSize.value();
        if (fetched.size() <= size) {
            return new CursorResponse<>(fetched, null, false);
        }
        List<T> content = fetched.subList(0, size);
        String nextCursor = cursorOf.apply(content.get(size - 1)).encode();
        return new CursorResponse<>(content, nextCursor, true);
    }

    /** 커서와 hasNext 는 유지한 채 content 만 변환한다. 엔티티 → DTO 변환용. */
    public <R> CursorResponse<R> map(Function<T, R> mapper) {
        return new CursorResponse<>(content.stream().map(mapper).toList(), nextCursor, hasNext);
    }
}
