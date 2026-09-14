package com.golmok.market.global.error;

import lombok.Getter;

/**
 * 서비스/도메인에서 의도적으로 던지는 예외.
 *
 * 예외 클래스를 상황마다 만들지 않고 ErrorCode 하나로 구분한다.
 * ProductNotFoundException 같은 클래스가 수십 개로 늘어나는 것을 막고,
 * 핸들러도 이 예외 하나만 처리하면 된다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 기본 메시지 대신 상황에 맞는 메시지를 내려야 할 때. 예: "끌어올리기는 24시간에 한 번만 가능합니다." */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
