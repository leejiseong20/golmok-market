package com.golmok.market.domain.auth;

import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 로그인 실패 횟수 제한. 시계를 직접 움직여 기다리지 않고 검증한다.
 */
class LoginAttemptGuardTest {

    private static final String EMAIL = "user@example.com";
    private static final String IP = "203.0.113.5";

    /** 테스트가 시각을 마음대로 옮길 수 있는 시계. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-09-23T10:00:00Z");

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void forward(Duration amount) { now = now.plus(amount); }
    }

    private final MovableClock clock = new MovableClock();
    private final LoginAttemptGuard guard = new LoginAttemptGuard(clock);

    private void fail(int times) {
        for (int i = 0; i < times; i++) {
            guard.recordFailure(EMAIL, IP);
        }
    }

    private void assertBlocked(String email, String ip) {
        assertThatThrownBy(() -> guard.check(email, ip))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
    }

    @Test
    void 한도_직전까지는_통과하고_한도를_채우면_막는다() {
        fail(LoginAttemptGuard.EMAIL_LIMIT - 1);
        assertThatCode(() -> guard.check(EMAIL, IP)).doesNotThrowAnyException();

        guard.recordFailure(EMAIL, IP);
        assertBlocked(EMAIL, IP);
    }

    @Test
    void 대소문자와_공백이_달라도_같은_계정으로_센다() {
        fail(LoginAttemptGuard.EMAIL_LIMIT);
        assertBlocked("  USER@example.com ", "198.51.100.9");
    }

    @Test
    void 로그인에_성공하면_그_이메일의_기록만_지운다() {
        fail(LoginAttemptGuard.EMAIL_LIMIT);
        guard.recordSuccess(EMAIL);
        // 이메일 기록은 지워졌다. 다른 사람이 같은 IP 를 쓰고 있을 수 있으므로 IP 기록은 남긴다.
        assertThatCode(() -> guard.check(EMAIL, IP)).doesNotThrowAnyException();
        assertThatCode(() -> guard.check("other@example.com", IP)).doesNotThrowAnyException();
    }

    @Test
    void 시간이_지나면_풀린다() {
        fail(LoginAttemptGuard.EMAIL_LIMIT);
        assertBlocked(EMAIL, IP);

        clock.forward(LoginAttemptGuard.WINDOW.plusSeconds(1));
        assertThatCode(() -> guard.check(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void 창_안에서_실패가_이어지면_잠금이_연장된다() {
        fail(LoginAttemptGuard.EMAIL_LIMIT);
        clock.forward(LoginAttemptGuard.WINDOW.minusMinutes(1));
        guard.recordFailure(EMAIL, IP);

        clock.forward(Duration.ofMinutes(2)); // 첫 실패로부터는 창을 넘겼지만 마지막 실패로부터는 아니다
        assertBlocked(EMAIL, IP);
    }

    @Test
    void 이메일이_모두_달라도_같은_IP_면_한도에서_막는다() {
        for (int i = 0; i < LoginAttemptGuard.IP_LIMIT; i++) {
            guard.recordFailure("user" + i + "@example.com", IP);
        }
        assertBlocked("new@example.com", IP);
        // IP 가 다르면 영향을 받지 않는다.
        assertThatCode(() -> guard.check("new@example.com", "198.51.100.9")).doesNotThrowAnyException();
    }

    @Test
    void 주소를_계속_바꿔_기록을_쌓아도_상한을_넘지_않고_보호가_꺼지지_않는다() {
        // 공격자가 매번 다른 이메일·IP 로 시도해 기록을 불리는 경우.
        for (int i = 0; i < LoginAttemptGuard.MAX_ENTRIES; i++) {
            guard.recordFailure("flood" + i + "@example.com", "10.0." + (i / 256) + "." + (i % 256));
        }
        assertThat(guard.size()).isLessThanOrEqualTo(LoginAttemptGuard.MAX_ENTRIES);

        // 오래된 기록이 밀려날 뿐, 새 실패는 계속 센다. 쓰레기 기록으로 보호를 꺼 버릴 수 없다.
        fail(LoginAttemptGuard.EMAIL_LIMIT);
        assertBlocked(EMAIL, IP);
    }
}
