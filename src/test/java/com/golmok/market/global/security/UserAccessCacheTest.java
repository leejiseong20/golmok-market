package com.golmok.market.global.security;

import com.golmok.market.domain.user.UserRole;
import com.golmok.market.global.security.UserAccessCache.UserAccess;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccessCacheTest {

    /** 테스트가 시각을 마음대로 옮길 수 있는 시계. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-09-23T10:00:00Z");

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void forward(Duration amount) { now = now.plus(amount); }
    }

    private static final UserAccess ACTIVE = new UserAccess(UserRole.USER, true);
    private static final UserAccess SUSPENDED = new UserAccess(UserRole.USER, false);

    private final MovableClock clock = new MovableClock();
    /** "DB". 조회 횟수를 센다. */
    private final Map<Long, UserAccess> db = new HashMap<>();
    private final AtomicInteger loads = new AtomicInteger();
    private final UserAccessCache cache = new UserAccessCache(id -> {
        loads.incrementAndGet();
        return Optional.ofNullable(db.get(id));
    }, clock);

    @Test
    void 기억하는_동안은_다시_조회하지_않고_60초가_지나면_다시_조회한다() {
        db.put(1L, ACTIVE);
        cache.get(1);
        cache.get(1);
        assertThat(loads.get()).isEqualTo(1);

        // DB 를 직접 고쳐도(이벤트 없이) 60초 안에는 옛 값이다 — 그래서 상태를 바꾸는 곳은 반드시 이벤트를 낸다.
        db.put(1L, SUSPENDED);
        clock.forward(Duration.ofSeconds(59));
        assertThat(cache.get(1)).contains(ACTIVE);

        clock.forward(Duration.ofSeconds(1));
        assertThat(cache.get(1)).contains(SUSPENDED);
        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    void 비우면_곧바로_새_상태를_본다() {
        db.put(1L, ACTIVE);
        cache.get(1);
        db.put(1L, SUSPENDED);

        cache.invalidate(1);

        assertThat(cache.get(1)).contains(SUSPENDED);
    }

    @Test
    void 없는_회원은_기억하지_않는다() {
        assertThat(cache.get(99)).isEmpty();
        // 그 번호로 막 가입한 회원은 바로 쓸 수 있어야 한다.
        db.put(99L, ACTIVE);
        assertThat(cache.get(99)).contains(ACTIVE);
        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    void 조회하는_도중_비워졌으면_읽은_옛_값을_담지_않는다() {
        // 요청 A 가 "정상"을 읽는 사이에 정지가 커밋되고 캐시가 비워지는 경우.
        // A 가 읽은 값을 그대로 담으면 정지가 60초 동안 적용되지 않는다.
        AtomicReference<UserAccessCache> self = new AtomicReference<>();
        UserAccessCache racing = new UserAccessCache(id -> {
            loads.incrementAndGet();
            UserAccess read = db.get(id);          // A 가 읽은 값: 정상
            if (loads.get() == 1) {
                db.put(id, SUSPENDED);             // 그 사이 정지가 커밋되고
                self.get().invalidate(id);         // 캐시가 비워진다
            }
            return Optional.ofNullable(read);
        }, clock);
        self.set(racing);
        db.put(1L, ACTIVE);

        assertThat(racing.get(1)).contains(ACTIVE); // A 는 자기가 읽은 값으로 이번 요청만 처리한다
        assertThat(racing.get(1)).contains(SUSPENDED);
        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    void 상한을_넘으면_가장_오래된_것부터_밀어낸다() {
        for (long id = 1; id <= UserAccessCache.MAX_ENTRIES + 10; id++) {
            db.put(id, ACTIVE);
            cache.get(id);
        }
        assertThat(cache.size()).isEqualTo(UserAccessCache.MAX_ENTRIES);
        // 가장 먼저 담은 1번은 밀려나 다시 조회한다.
        int before = loads.get();
        cache.get(1);
        assertThat(loads.get()).isEqualTo(before + 1);
    }
}
