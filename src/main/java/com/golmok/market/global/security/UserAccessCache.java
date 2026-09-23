package com.golmok.market.global.security;

import com.golmok.market.domain.user.UserAccessChangedEvent;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.domain.user.UserRole;
import com.golmok.market.domain.user.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;

/**
 * 요청마다 확인하는 회원의 현재 상태·권한. 인증 필터와 채팅 연결 인증이 쓴다.
 *
 * access token 만 보면 정지·탈퇴·관리자 해제가 토큰 만료(최대 30분)까지 반영되지 않는다 — 정지한 사기꾼이 30분 동안
 * 채팅을 더 보낼 수 있다. 그래서 요청마다 DB 기준 상태를 보되, 매번 조회하지 않도록 회원당 60초 기억한다.
 *
 * - **바뀌면 바로 비운다.** 정지·해제·탈퇴는 {@link UserAccessChangedEvent} 를 발행한다. 발행 즉시 한 번,
 *   커밋 뒤에 한 번 더 비운다. 즉시만 하면 커밋 전 사이에 다른 요청이 옛 상태를 다시 담을 수 있고,
 *   커밋 뒤만 하면 같은 트랜잭션 안의 다음 확인(테스트 등)이 옛 상태를 본다.
 * - **읽는 도중 비워졌으면 담지 않는다.** 조회를 시작한 뒤 누군가 비웠다면 그 조회 결과는 이미 낡았을 수 있다.
 *   비운 횟수(invalidations)를 조회 전후로 비교한다. 누가 비웠는지는 따지지 않아 가끔 한 번 더 조회할 뿐이다.
 * - 60초는 DB 를 직접 고친 경우(SQL 로 관리자 지정 등)를 위한 안전망이다.
 * - 메모리 캐시라 서버가 한 대일 때만 즉시 반영된다. 서버를 늘리면 비우는 신호를 서버끼리 나눠야 한다.
 */
@Component
public class UserAccessCache {

    /** 캐시가 이 시간 지나면 다시 조회한다. */
    static final Duration TTL = Duration.ofSeconds(60);
    /** 기억하는 회원 수 상한. 넘으면 가장 오래전에 담은 것부터 밀어낸다(LoginAttemptGuard 와 같은 방식). */
    static final int MAX_ENTRIES = 10_000;

    /** 쓸 수 있는 계정인지와 지금 권한. */
    public record UserAccess(UserRole role, boolean active) {
    }

    private final Clock clock;
    private final LongFunction<Optional<UserAccess>> loader;
    private final Map<Long, Entry> entries = new LinkedHashMap<>(256, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Entry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
    private long invalidations;

    @Autowired
    public UserAccessCache(UserRepository userRepository, Clock clock) {
        this(id -> userRepository.findAccess(id)
                .map(row -> new UserAccess(row.getRole(), row.getStatus() == UserStatus.ACTIVE)), clock);
    }

    UserAccessCache(LongFunction<Optional<UserAccess>> loader, Clock clock) {
        this.loader = loader;
        this.clock = clock;
    }

    /** 지금 상태. 캐시가 없거나 오래됐으면 조회한다(조회는 잠금 밖에서 — 다른 회원의 확인을 막지 않는다). */
    public Optional<UserAccess> get(long userId) {
        Instant now = clock.instant();
        long seen;
        synchronized (this) {
            Entry cached = entries.get(userId);
            if (cached != null && now.isBefore(cached.loadedAt().plus(TTL))) {
                return Optional.of(cached.access());
            }
            seen = invalidations;
        }
        Optional<UserAccess> loaded = loader.apply(userId);
        // 없는 회원은 기억하지 않는다. 서명이 맞는 토큰인데 회원이 없는 일은 사실상 없고(회원 행은 지우지 않는다),
        // 기억하면 그 번호로 막 가입한 회원이 60초 동안 막힐 수 있다.
        if (loaded.isPresent()) {
            synchronized (this) {
                if (invalidations == seen) {
                    entries.put(userId, new Entry(loaded.get(), now));
                }
            }
        }
        return loaded;
    }

    public synchronized void invalidate(long userId) {
        entries.remove(userId);
        invalidations++;
    }

    /** 발행 즉시 비운다(같은 트랜잭션 안의 다음 확인도 새 상태를 보게). */
    @EventListener
    public void onChangedNow(UserAccessChangedEvent event) {
        invalidate(event.userId());
    }

    /** 커밋 뒤 한 번 더 비운다. 커밋 전 사이에 다른 요청이 옛 상태를 담았을 수 있다. 롤백이면 할 일이 없다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChangedAfterCommit(UserAccessChangedEvent event) {
        invalidate(event.userId());
    }

    synchronized int size() {
        return entries.size();
    }

    /**
     * 모두 비운다. 테스트끼리 기록을 물려받지 않게 하려는 용도다(빈이 하나라 공유되고, 롤백한 테스트의 회원 번호가
     * 다음 테스트에서 다시 쓰일 수 있다). 운영 코드에서는 부르지 않는다.
     */
    synchronized void clear() {
        entries.clear();
        invalidations++;
    }

    private record Entry(UserAccess access, Instant loadedAt) {
    }
}
