package com.golmok.market.domain.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 웹 푸시 설정. 운영은 deploy/.env 의 VAPID_* 로 넣는다(커밋 금지).
 *
 * 키가 비어 있으면 푸시만 꺼지고 나머지는 그대로 동작한다. 로컬 개발·테스트는 키 없이 돈다.
 *
 * @param publicKey  VAPID 공개키. P-256 비압축 65바이트의 base64url
 * @param privateKey VAPID 개인키. 32바이트 스칼라의 base64url
 * @param subject    푸시 서비스가 문제가 있을 때 연락할 곳(https:// 또는 mailto:). 개인 이메일 대신 서비스 주소를 쓴다.
 */
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(String publicKey, String privateKey, String subject) {

    public boolean configured() {
        return publicKey != null && !publicKey.isBlank() && privateKey != null && !privateKey.isBlank();
    }
}
