package com.golmok.market.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/** 회원 탈퇴. 로그인된 기기를 잠깐 쓰는 것만으로 계정이 사라지지 않게 비밀번호를 다시 받는다. */
public record WithdrawRequest(@NotBlank(message = "비밀번호를 입력해 주세요.") String password) {
}
