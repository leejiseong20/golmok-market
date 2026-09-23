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
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // ---------- 인증 ----------
    // EXPIRED_TOKEN 과 INVALID_TOKEN 을 나눈 이유: 프론트는 만료일 때만 재발급을 시도하고,
    // 위조·형식 오류면 곧바로 로그아웃시켜야 한다. 코드가 같으면 이 판단을 할 수 없다.
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료되었습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    // 비밀번호 대입 공격을 늦춘다. 잠긴 동안에는 맞는 비밀번호도 막는다(LoginAttemptGuard).
    TOO_MANY_LOGIN_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 10분 뒤에 다시 시도해 주세요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    USER_NOT_ACTIVE(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),

    // ---------- 상품 ----------
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    REGION_NOT_VERIFIED(HttpStatus.FORBIDDEN, "인증한 동네에서만 상품을 등록하거나 수정할 수 있습니다."),
    INVALID_IMAGE_URL(HttpStatus.BAD_REQUEST, "사진을 다시 업로드해 주세요. 서버에 저장된 이미지 경로만 사용할 수 있습니다."),

    // ---------- 회원 · 찜 ----------
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 올바르지 않습니다."),
    CANNOT_FAVORITE_OWN_PRODUCT(HttpStatus.BAD_REQUEST, "자신의 상품은 찜할 수 없습니다."),
    ALREADY_FAVORITED(HttpStatus.CONFLICT, "이미 찜한 상품입니다."),

    // ---------- 신고 ----------
    CANNOT_REPORT_SELF(HttpStatus.BAD_REQUEST, "자기 자신은 신고할 수 없습니다."),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 대상입니다."),
    REPORT_DETAIL_REQUIRED(HttpStatus.BAD_REQUEST, "기타를 고르면 어떤 점이 문제인지 적어 주세요."),

    // ---------- 차단 ----------
    CANNOT_BLOCK_SELF(HttpStatus.BAD_REQUEST, "자기 자신은 차단할 수 없습니다."),
    ALREADY_BLOCKED(HttpStatus.CONFLICT, "이미 차단한 사용자입니다."),
    // 기본 문구는 차단당한 쪽이 보는 것이다. 이유를 밝히지 않는다(차단당한 사실이 드러나면 안 된다).
    // 차단한 쪽에는 ChatService 가 "해제하면 된다"는 문구로 바꿔 보낸다.
    BLOCKED_USER(HttpStatus.FORBIDDEN, "지금은 이 사용자와 대화할 수 없어요."),

    // ---------- 웹 푸시 ----------
    PUSH_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "지금은 알림을 받을 수 없어요."),
    INVALID_PUSH_SUBSCRIPTION(HttpStatus.BAD_REQUEST, "알림 구독 정보가 올바르지 않습니다."),

    // ---------- 이미지 ----------
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "jpg, png, webp 이미지만 올릴 수 있습니다."),
    IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지는 장당 5MB 이하여야 합니다."),

    // ---------- 동네 ----------
    REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "동네를 찾을 수 없습니다."),
    USER_REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "인증하지 않은 동네입니다."),
    REGION_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "동네는 최대 2개까지 인증할 수 있습니다."),

    // ---------- 거래 ----------
    REVIEW_NOT_ALLOWED(HttpStatus.CONFLICT, "거래가 완료된 뒤에만 후기를 남길 수 있어요."),
    ALREADY_REVIEWED(HttpStatus.CONFLICT, "이미 후기를 남긴 거래예요."),
    TRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "거래 내역을 찾을 수 없습니다."),
    CANNOT_BUY_OWN_PRODUCT(HttpStatus.BAD_REQUEST, "자신의 상품은 구매할 수 없습니다."),
    // 거래와 상품 상태가 어긋나지 않도록, 진행 중 거래가 있으면 상품 쪽에서 직접 바꾸지 못하게 한다.
    TRADE_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 거래가 있어요. 채팅방에서 예약을 취소하거나 거래를 완료해 주세요."),
    NO_RESERVATION(HttpStatus.BAD_REQUEST, "이 채팅방에 진행 중인 예약이 없어요."),
    SELLER_ONLY(HttpStatus.FORBIDDEN, "판매자만 할 수 있어요."),

    // ---------- 알림 ----------
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),

    // ---------- 채팅 ----------
    // 남의 방·이미 나간 방도 이 코드로 답한다. 403 을 주면 그 id 의 방이 존재한다는 사실이 드러난다.
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    CHAT_OPPONENT_WITHDRAWN(HttpStatus.BAD_REQUEST, "탈퇴한 사용자와는 대화하거나 거래할 수 없어요."),
    CANNOT_CHAT_OWN_PRODUCT(HttpStatus.BAD_REQUEST, "자신의 상품에는 채팅을 걸 수 없습니다."),
    PRODUCT_NOT_CHATTABLE(HttpStatus.BAD_REQUEST, "거래가 완료된 상품에는 새 채팅을 시작할 수 없습니다.");

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
        // 같은 상태를 쓰는 코드가 여럿(401 만 5개)이라 enum 선언 순서에 기대지 않고 명시적으로 고른다.
        return switch (status.value()) {
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> DUPLICATE_RESOURCE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status.is4xxClientError() ? INVALID_INPUT : INTERNAL_ERROR;
        };
    }
}
