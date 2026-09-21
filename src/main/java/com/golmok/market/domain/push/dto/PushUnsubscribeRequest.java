package com.golmok.market.domain.push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 구독 주소는 기기 식별자라 URL(쿼리)에 싣지 않고 본문으로 받는다. 접속 로그에 남지 않게. */
public record PushUnsubscribeRequest(
        @NotBlank(message = "구독 주소가 없습니다.")
        @Size(max = 500, message = "구독 주소가 너무 깁니다.")
        String endpoint
) {
}
