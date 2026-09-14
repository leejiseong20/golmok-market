package com.golmok.market.global.error;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 모든 실패 응답의 공통 본문.
 *
 * <pre>
 * { "code": "INVALID_INPUT", "message": "...", "timestamp": "2026-09-14T19:52:31",
 *   "errors": [ { "field": "email", "reason": "이메일 형식이 아닙니다." } ] }
 * </pre>
 *
 * errors 는 입력 검증 실패일 때만 나가고, 그 외에는 필드 자체가 빠진다.
 * 프론트가 어느 입력칸에 에러를 표시할지 알아야 하기 때문이다.
 *
 * 컨트롤러 밖(Spring Security 필터의 401/403 처리 등)에서도
 * 같은 형식으로 응답할 수 있도록 핸들러와 분리된 값 객체로 둔다.
 */
public record ErrorResponse(
        String code,
        String message,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime timestamp,

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<FieldError> errors
) {

    public record FieldError(String field, String reason) {
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message, LocalDateTime.now(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), LocalDateTime.now(), List.copyOf(errors));
    }
}
