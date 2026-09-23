package com.golmok.market.domain.user;

/**
 * 회원이 API 를 쓸 수 있는지(상태·권한)가 바뀌었다. 정지·정지 해제·탈퇴에서 발행한다.
 *
 * 인증 필터는 요청마다 회원 상태를 메모리 캐시로 확인한다(UserAccessCache). 이 이벤트로 그 회원의 캐시를 비워,
 * 정지·탈퇴가 이미 발급된 access token 에도 곧바로 적용되게 한다.
 */
public record UserAccessChangedEvent(long userId) {
}
