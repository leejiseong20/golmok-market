package com.golmok.market.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 텍스트 메시지 전송. 공백만 있는 메시지는 보내지 않는다. */
public record ChatMessageSendRequest(
        @NotBlank(message = "메시지를 입력해 주세요.")
        @Size(max = 1000, message = "메시지는 1000자 이하로 입력해 주세요.")
        String content
) {
}
