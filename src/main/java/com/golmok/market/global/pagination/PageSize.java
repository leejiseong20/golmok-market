package com.golmok.market.global.pagination;

/**
 * 목록 요청의 size 를 보정한다. 기본 20, 최대 50.
 *
 * 범위를 벗어나도 에러를 내지 않고 맞춘다. 무한 스크롤에서 400 을 받으면
 * 사용자는 할 수 있는 게 없고, 서버 입장에서 지켜야 할 것은 "한 번에 너무 많이
 * 조회되지 않는 것" 뿐이기 때문이다.
 *
 * {@link #fetchSize()} 가 한 건 더 많은 이유는 {@link CursorResponse} 참고.
 */
public record PageSize(int value) {

    public static final int DEFAULT = 20;
    public static final int MAX = 50;

    public PageSize {
        if (value < 1 || value > MAX) {
            throw new IllegalArgumentException("size 는 1~" + MAX + " 사이여야 합니다: " + value);
        }
    }

    /** 요청 값 보정. 없거나 1 미만이면 기본값, 최대값을 넘으면 최대값. */
    public static PageSize of(Integer requested) {
        if (requested == null || requested < 1) {
            return new PageSize(DEFAULT);
        }
        return new PageSize(Math.min(requested, MAX));
    }

    /** 실제 조회할 건수. 다음 페이지 존재 여부를 알기 위해 1건을 더 가져온다. */
    public int fetchSize() {
        return value + 1;
    }
}
