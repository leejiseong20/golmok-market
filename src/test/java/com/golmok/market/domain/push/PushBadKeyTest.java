package com.golmok.market.domain.push;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 짝이 틀린 VAPID 키로 떠도 앱은 뜨고 푸시만 꺼진다. 서버가 한 대라 키 하나 잘못 넣었다고 서비스 전체가 내려가면 안 된다.
 * (공개키는 RFC 의 서버 키, 개인키는 RFC 의 브라우저 키 — 둘 다 올바른 키지만 서로 짝이 아니다.)
 */
@SpringBootTest(properties = {
        "app.push.public-key=" + PushTestKeys.VAPID_PUBLIC,
        "app.push.private-key=" + PushTestKeys.BROWSER_PRIVATE})
class PushBadKeyTest {

    @Autowired PushService pushService;

    @Test
    void 짝이_틀린_키로도_앱은_뜨고_푸시만_꺼진다() {
        assertThat(pushService.enabled()).isFalse();
        assertThat(pushService.publicKey().enabled()).isFalse();
    }
}
