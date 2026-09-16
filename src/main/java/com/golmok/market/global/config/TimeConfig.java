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
 * 1) JVM 기본 시간대는 반드시 Asia/Seoul 이어야 한다.
 *    엔티티와 응답은 LocalDateTime(시간대 없음)을 쓰므로 시간대가 다르면 모든 시각이 9시간 어긋난다.
 *
 *    시간대는 JVM 시작 옵션(-Duser.timezone=Asia/Seoul)으로만 정한다.
 *    - 운영: Dockerfile 의 JAVA_TOOL_OPTIONS
 *    - 로컬 실행·테스트: build.gradle 의 bootRun / test 태스크
 *
 *    예전에는 여기서 TimeZone.setDefault 로 기동 중에 바꿨다. 그러면 그보다 먼저 초기화된
 *    DB 드라이버·Hibernate 는 원래 시간대를, 이후 코드는 서울을 기준으로 삼아 서로 어긋난다.
 *    실제로 UTC 인 GitHub Actions 러너에서 DB 에 넣은 12:00 이 21:00 으로 읽혔다.
 *    그래서 이제는 바꾸지 않고, 틀렸으면 기동을 멈춘다. 시각이 조용히 틀어진 채 저장되는 것보다 낫다.
 *
 * 2) Clock 을 빈으로 둔다.
 *    토큰 만료처럼 "지금 시각"에 의존하는 로직을 테스트에서 시간을 고정해 검증하기 위함.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @PostConstruct
    void verifyDefaultTimeZone() {
        ZoneId actual = TimeZone.getDefault().toZoneId();
        if (!actual.normalized().equals(ZONE.normalized())) {
            throw new IllegalStateException(
                    "JVM 기본 시간대가 %s 입니다. -Duser.timezone=%s 로 실행해야 합니다.".formatted(actual, ZONE));
        }
    }

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
