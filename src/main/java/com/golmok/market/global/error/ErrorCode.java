package com.golmok.market.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * API 에러 코드 목록. 응답의 code 필드에는 enum 이름이 그대로 나간다.
 *
 * 공통 코드만 먼저 둔다. PRODUCT_NOT_FOUND 같은 도메인 코드는
 * 해당 도메인을 구현할 때 이 enum 에 추가한다.
 * 한 곳에 모아두면 프론트와 "어떤 코드가 존재하는가"를 파일 하나로 공유할 수 있다.
 */
@Getter
public enum ErrorCode {

    // 400
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_STATE(HttpStatus.BAD_REQUEST, "현재 상태에서는 처리할 수 없는 요청입니다."),

    // 401 / 403
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),

    // 404 / 405 / 409 / 415
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "이미 존재하는 리소스입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type 입니다."),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public String getCode() {
        return name();
    }

    /**
     * Spring MVC 가 던지는 예외(404, 405 등)를 HTTP 상태로 코드에 대응시킨다.
     * 딱 맞는 코드가 없으면 4xx 는 INVALID_INPUT, 5xx 는 INTERNAL_ERROR 로 본다.
     */
    public static ErrorCode fromStatus(HttpStatusCode status) {
        for (ErrorCode code : values()) {
            if (code.status.value() == status.value()) {
                return code;
            }
        }
        return status.is4xxClientError() ? INVALID_INPUT : INTERNAL_ERROR;
    }
}
