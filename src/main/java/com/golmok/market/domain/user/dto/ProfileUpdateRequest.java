package com.golmok.market.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 프로필 수정. 두 값을 항상 함께 보낸다.
 *
 * 보낸 필드만 바꾸는 방식으로 두면 "사진 삭제"로 보낸 null 과 "사진은 그대로"를 구분할 수 없다.
 * 수정 화면은 늘 두 값을 들고 있으므로 매번 둘 다 보내고, profileImageUrl 이 null 이면 사진을 지운다.
 *
 * 닉네임 규칙은 가입(SignupRequest)과 같다. 공백 검사는 앞뒤 공백을 뺀 값으로 한다.
 */
public record ProfileUpdateRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 30, message = "닉네임은 2~30자여야 합니다.")
        String nickname,

        @Size(max = 500, message = "사진 경로가 너무 깁니다.")
        String profileImageUrl
) {

    public ProfileUpdateRequest {
        // 검증보다 먼저 공백을 걷어낸다. " 가 " 처럼 보이는 두 글자 닉네임이 실제로는 한 글자인 것을 막는다.
        nickname = nickname == null ? null : nickname.strip();
        profileImageUrl = profileImageUrl == null || profileImageUrl.isBlank() ? null : profileImageUrl;
    }
}
