package com.golmok.market.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ReissueRequest(

        @NotBlank(message = "refreshToken 은 필수입니다.")
        String refreshToken
) {
}
