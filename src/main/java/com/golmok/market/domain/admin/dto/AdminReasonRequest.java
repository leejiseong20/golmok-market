package com.golmok.market.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 신고 없이 하는 직접 조치(정지·정지 해제·상품 내리기·되살리기)의 요청.
 *
 * 이유는 필수다. 신고 처리와 같은 이유 — 근거 없이 남기면 감사 로그의 뜻이 없다.
 */
public record AdminReasonRequest(@NotBlank(message = "조치 이유를 적어 주세요.")
                                 @Size(max = 500, message = "조치 이유는 500자 이하로 적어 주세요.")
                                 String reason) {
}
