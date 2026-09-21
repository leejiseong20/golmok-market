package com.golmok.market.domain.push;

import java.time.Duration;

/**
 * 푸시 서비스(Google FCM · Apple · Mozilla …)로 보내는 창구. 테스트에서 가짜로 바꿔 끼우려고 인터페이스로 둔다.
 */
public interface PushGateway {

    enum Result {
        /** 푸시 서비스가 받았다(201). 기기에 닿았다는 뜻은 아니다 — 꺼져 있으면 TTL 동안 기다린다. */
        SENT,
        /** 구독이 없어졌다(404·410). 사용자가 권한을 끄거나 앱을 지웠다. 행을 지운다. */
        GONE,
        /** 그 밖의 실패. 일시적일 수 있어 구독은 남긴다. */
        FAILED
    }

    enum Urgency {
        /** 채팅처럼 바로 봐야 하는 것. 절전 중인 기기도 깨운다. */
        HIGH("high"),
        NORMAL("normal");

        final String header;

        Urgency(String header) {
            this.header = header;
        }
    }

    Result send(String endpoint, byte[] encryptedBody, String authorization, Duration ttl, Urgency urgency);
}
