package com.golmok.market.domain.push.dto;

/**
 * 브라우저가 구독할 때 쓰는 VAPID 공개키.
 *
 * @param enabled 서버에 키가 없으면 false. 화면은 "알림 받기"를 숨긴다.
 */
public record PushPublicKeyResponse(boolean enabled, String publicKey) {
}
