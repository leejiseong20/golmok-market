package com.golmok.market.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 신고 처리 요청.
 *
 * 이유는 필수다. 정지·삭제는 되돌리기 어렵고 남의 계정·물건에 손대는 일이라, 근거 없이 남기면 감사 로그의 뜻이 없다.
 *
 * @param action 함께 할 조치. 반려(reject)에서는 쓰지 않는다. 비우면 NONE(신고는 인정하되 조치는 하지 않음)
 * @param reason 관리자가 적는 근거
 */
public record ReportHandleRequest(Action action,
                                  @NotBlank(message = "처리 이유를 적어 주세요.")
                                  @Size(max = 500, message = "처리 이유는 500자 이하로 적어 주세요.")
                                  String reason) {

    public enum Action {
        /** 신고는 인정하되 대상에는 손대지 않는다(경고만 하고 지켜보는 경우). */
        NONE,
        SUSPEND_USER,
        DELETE_PRODUCT
    }

    public ReportHandleRequest {
        if (action == null) {
            action = Action.NONE;
        }
    }
}
