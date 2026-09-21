package com.golmok.market.domain.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 브라우저의 PushSubscription.toJSON() 과 같은 모양이다(endpoint + keys.p256dh · keys.auth). */
public record PushSubscribeRequest(
        @NotBlank(message = "구독 주소가 없습니다.")
        @Size(max = 500, message = "구독 주소가 너무 깁니다.")
        String endpoint,

        @NotNull(message = "구독 키가 없습니다.")
        @Valid
        Keys keys
) {
    public record Keys(
            @NotBlank(message = "구독 키가 없습니다.") @Size(max = 100, message = "구독 키가 너무 깁니다.") String p256dh,
            @NotBlank(message = "구독 키가 없습니다.") @Size(max = 50, message = "구독 키가 너무 깁니다.") String auth
    ) {
    }
}
