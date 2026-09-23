package com.golmok.market.domain.clienterror.dto;

import com.golmok.market.domain.clienterror.ClientErrorKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 화면 오류 보고.
 *
 * 사람을 가릴 수 있는 값은 받지 않는다(회원 id·토큰·쿼리). 쿼리에는 검색어가 들어 있을 수 있어,
 * 주소는 경로만 받는다 — ? 나 # 가 있으면 거절한다. 화면이 보낼 때 이미 잘라 보낸다.
 *
 * @param boundary       잡은 오류 경계 이름(예: 본문·창·관리자 본문)
 * @param message        오류 메시지. 화면이 300자로 잘라 보낸다
 * @param path           오류가 난 화면의 경로(쿼리 없음)
 * @param componentStack 어느 컴포넌트에서 났는지. 없을 수 있다
 */
public record ClientErrorRequest(
        @NotBlank(message = "경계 이름이 필요합니다.")
        @Size(max = 30, message = "경계 이름은 30자 이하여야 합니다.")
        String boundary,

        @NotNull(message = "오류 종류가 필요합니다.")
        ClientErrorKind kind,

        @NotBlank(message = "오류 메시지가 필요합니다.")
        @Size(max = 300, message = "오류 메시지는 300자 이하여야 합니다.")
        String message,

        @NotBlank(message = "화면 경로가 필요합니다.")
        @Size(max = 200, message = "화면 경로는 200자 이하여야 합니다.")
        @Pattern(regexp = "^/[^?#]*$", message = "화면 경로는 쿼리 없이 / 로 시작해야 합니다.")
        String path,

        @Size(max = 2000, message = "컴포넌트 스택은 2000자 이하여야 합니다.")
        String componentStack) {
}
