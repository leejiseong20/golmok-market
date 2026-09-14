package com.golmok.market.global.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * 시간 기준을 한곳에서 정한다.
 *
 * 1) JVM 기본 시간대를 Asia/Seoul 로 고정한다.
 *    엔티티와 응답은 LocalDateTime(시간대 없음)을 쓰므로, 배포 서버가 UTC 면
 *    토큰 만료 시각·생성 시각이 전부 9시간 어긋난다. 배포 시 -Duser.timezone=Asia/Seoul 도
 *    함께 주는 게 안전하지만, 설정 누락으로 사고 나지 않도록 애플리케이션에서도 보장한다.
 *
 * 2) Clock 을 빈으로 둔다.
 *    토큰 만료처럼 "지금 시각"에 의존하는 로직을 테스트에서 시간을 고정해 검증하기 위함.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @PostConstruct
    void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
    }

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
