package com.golmok.market.domain.clienterror;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ClientErrorRateLimiterTest {

    /** 테스트가 시각을 마음대로 옮길 수 있는 시계. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-09-23T10:00:00Z");

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void forward(Duration amount) { now = now.plus(amount); }
    }

    private final MovableClock clock = new MovableClock();
    private final ClientErrorRateLimiter limiter = new ClientErrorRateLimiter(clock);

    private int accepted(String ip, int tries) {
        int count = 0;
        for (int i = 0; i < tries; i++) {
            if (limiter.tryAcquire(ip)) {
                count++;
            }
        }
        return count;
    }

    @Test
    void 같은_IP_는_1분에_10건까지_받고_1분이_지나면_다시_받는다() {
        assertThat(accepted("203.0.113.5", 15)).isEqualTo(ClientErrorRateLimiter.PER_IP_LIMIT);
        // 다른 IP 는 영향을 받지 않는다.
        assertThat(limiter.tryAcquire("198.51.100.7")).isTrue();

        clock.forward(Duration.ofSeconds(59));
        assertThat(limiter.tryAcquire("203.0.113.5")).isFalse();
        clock.forward(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire("203.0.113.5")).isTrue();
    }

    @Test
    void IP_를_바꿔_가며_보내도_전체_한도에서_멈춘다() {
        int total = 0;
        for (int i = 0; i < 100; i++) {
            total += accepted("10.0." + i + ".1", ClientErrorRateLimiter.PER_IP_LIMIT);
        }
        assertThat(total).isEqualTo(ClientErrorRateLimiter.TOTAL_LIMIT);

        clock.forward(ClientErrorRateLimiter.WINDOW);
        assertThat(limiter.tryAcquire("10.0.200.1")).isTrue();
    }

    @Test
    void IP_한도에_걸린_요청은_전체_한도를_쓰지_않는다() {
        // 한 IP 가 한도를 넘겨 계속 보내도 다른 사람의 보고 자리를 빼앗지 않는다.
        accepted("203.0.113.5", 1_000);
        assertThat(accepted("198.51.100.7", ClientErrorRateLimiter.PER_IP_LIMIT))
                .isEqualTo(ClientErrorRateLimiter.PER_IP_LIMIT);
    }

    @Test
    void 기록_수가_상한을_넘으면_오래된_것부터_밀어낸다() {
        for (int i = 0; i < ClientErrorRateLimiter.MAX_ENTRIES + 50; i++) {
            limiter.tryAcquire("ip-" + i);
            // 전체 한도에 막혀도 IP 기록은 남는다. 상한 확인을 위해 창을 넘겨 전체 한도를 비운다.
            if (i % ClientErrorRateLimiter.TOTAL_LIMIT == 0) {
                clock.forward(ClientErrorRateLimiter.WINDOW);
            }
        }
        assertThat(limiter.size()).isEqualTo(ClientErrorRateLimiter.MAX_ENTRIES);
    }
}
