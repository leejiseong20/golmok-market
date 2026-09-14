package com.golmok.market.global.pagination;

import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 커서 = "마지막으로 본 항목의 정렬값 + id". 문자열 형식은 {정렬값}_{id}.
 *
 * <pre>
 * 최신순      2026-09-14T18:20:00_47   (bumped_at, id)
 * 낮은가격순  80000_47                 (price, id)
 * </pre>
 *
 * 왜 id 를 같이 넣는가: 정렬값만으로는 순서가 확정되지 않는다.
 * bumped_at 은 초 단위라 같은 초에 끌어올린 상품이 여럿일 수 있고, 가격은 더 자주 겹친다.
 * 이때 "정렬값이 같으면 id 로 가른다"는 규칙이 없으면 경계에 걸린 상품이 누락되거나 중복된다.
 * 조회 조건은 항상 다음 형태가 된다.
 *
 * <pre>
 * WHERE (bumped_at &lt; :value) OR (bumped_at = :value AND id &lt; :id)
 * </pre>
 *
 * 정렬 기준이 목록마다 다르므로 정렬값은 문자열로 들고 있다가
 * 사용하는 쪽에서 {@link #valueAsDateTime()} / {@link #valueAsLong()} 으로 꺼낸다.
 */
public record Cursor(String value, long id) {

    private static final String DELIMITER = "_";
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public Cursor {
        if (value == null || value.isBlank()) {
            throw invalid();
        }
        if (id <= 0) {
            throw invalid();
        }
    }

    // ---------- 생성 ----------

    public static Cursor of(LocalDateTime value, long id) {
        return new Cursor(DATE_TIME_FORMAT.format(value), id);
    }

    public static Cursor of(long value, long id) {
        return new Cursor(Long.toString(value), id);
    }

    /**
     * 요청 파라미터를 파싱한다.
     *
     * @return 첫 페이지 요청(cursor 생략)이면 null
     * @throws BusinessException 형식이 틀리면 INVALID_INPUT (400)
     */
    public static Cursor parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // 정렬값 쪽에 구분자가 섞여도 id 는 항상 마지막 조각이므로 뒤에서부터 자른다.
        int delimiterIndex = raw.lastIndexOf(DELIMITER);
        if (delimiterIndex <= 0 || delimiterIndex == raw.length() - 1) {
            throw invalid();
        }
        String value = raw.substring(0, delimiterIndex);
        String idPart = raw.substring(delimiterIndex + 1);
        try {
            return new Cursor(value, Long.parseLong(idPart));
        } catch (NumberFormatException e) {
            throw invalid();
        }
    }

    // ---------- 사용 ----------

    /** 응답의 nextCursor 로 내려갈 문자열 */
    public String encode() {
        return value + DELIMITER + id;
    }

    public LocalDateTime valueAsDateTime() {
        try {
            return LocalDateTime.parse(value, DATE_TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw invalid();
        }
    }

    public long valueAsLong() {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_INPUT, "cursor 형식이 올바르지 않습니다.");
    }
}
