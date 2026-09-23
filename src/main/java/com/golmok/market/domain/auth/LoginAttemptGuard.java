package com.golmok.market.domain.auth;

import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 로그인 실패 횟수 제한(비밀번호 대입 공격 방어).
 *
 * 데모 계정 비밀번호가 공개돼 있고 운영 서버가 인터넷에 열려 있어, 막는 장치가 없으면 같은 계정에 계속 시도할 수 있다.
 *
 * 이메일과 IP 를 따로 센다. 한 계정을 노리는 공격(같은 이메일·여러 비밀번호)과
 * 여러 계정을 훑는 공격(같은 IP·여러 이메일)은 모양이 달라 한 가지 기준으로는 한쪽을 놓친다.
 * IP 한도를 이메일보다 넉넉하게 두는 이유: 회사·학교처럼 여러 사람이 한 IP 를 쓰는 곳을 막지 않기 위해서다.
 *
 * 잠긴 동안에는 비밀번호가 맞아도 막는다. 맞는 비밀번호를 통과시키면 잠금이 대입 공격을 늦추지 못한다.
 *
 * 저장소는 메모리다. 서버가 한 대이고(브로커·캐시도 메모리다) 의존성을 늘리지 않으려는 선택이다.
 * 서버를 늘리면 서버마다 따로 세므로 한도가 서버 수만큼 늘어난다 — 그때는 Redis 같은 공용 저장소로 옮겨야 한다.
 * 재시작하면 기록이 사라지지만, 공격자가 재시작 시점을 고를 수 없어 실질적인 우회가 되지 않는다.
 */
@Component
public class LoginAttemptGuard {

    /** 같은 이메일로 이 횟수만큼 실패하면 잠근다. */
    static final int EMAIL_LIMIT = 5;
    /** 같은 IP 기준. 한 곳에서 여러 계정을 훑는 경우를 막는다. */
    static final int IP_LIMIT = 20;
    /** 실패를 세는 기간이자 잠기는 기간. 마지막 실패로부터 이만큼 지나면 풀린다. */
    static final Duration WINDOW = Duration.ofMinutes(10);
    /**
     * 기록 수 상한. 공격자가 매번 다른 이메일·IP 로 시도하면 맵이 계속 커져 그 자체가 공격이 된다.
     * 넘으면 **가장 오래 전에 기록된 것부터** 밀어낸다. 새 기록을 거부하는 방식은 안 된다 —
     * 쓰레기 기록으로 상한을 채워 보호 자체를 꺼 버릴 수 있다(테스트가 이 구멍을 잡았다).
     */
    static final int MAX_ENTRIES = 20_000;

    private final Clock clock;
    /**
     * 넣은 순서를 지키는 맵이라 상한을 넘으면 가장 오래된 기록이 빠진다.
     * 로그인 시도는 초당 수백 건이 아니어서, 잠금 하나로 묶어도 성능 문제가 없다.
     */
    private final Map<String, Attempts> attempts = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Attempts> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    public LoginAttemptGuard(Clock clock) {
        this.clock = clock;
    }

    /** 로그인 처리 전에 부른다. 잠겨 있으면 429 로 막는다. */
    public void check(String email, String clientIp) {
        Instant now = clock.instant();
        if (locked(emailKey(email), EMAIL_LIMIT, now) || locked(ipKey(clientIp), IP_LIMIT, now)) {
            throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }
    }

    /** 비밀번호가 틀렸을 때 부른다. */
    public void recordFailure(String email, String clientIp) {
        Instant now = clock.instant();
        record(emailKey(email), now);
        record(ipKey(clientIp), now);
    }

    /** 로그인에 성공하면 그 이메일의 기록을 지운다. IP 기록은 남긴다 — 한 번 성공했다고 그 IP 의 다른 시도까지 풀어 줄 이유가 없다. */
    public void recordSuccess(String email) {
        attempts.remove(emailKey(email));
    }

    /**
     * 기록을 모두 비운다. 테스트가 서로의 실패 횟수를 물려받지 않게 하려는 용도다
     * (빈이 하나라 테스트 클래스들이 같은 기록을 공유한다). 운영 코드에서는 부르지 않는다.
     */
    void reset() {
        attempts.clear();
    }

    /** 지금 세고 있는 대상 수. 상한이 지켜지는지 테스트에서 본다. */
    int size() {
        return attempts.size();
    }

    private boolean locked(String key, int limit, Instant now) {
        Attempts found = attempts.get(key);
        if (found == null || expired(found, now)) {
            return false;
        }
        return found.count.get() >= limit;
    }

    private void record(String key, Instant now) {
        attempts.compute(key, (ignored, found) -> {
            if (found == null || expired(found, now)) {
                return new Attempts(now);
            }
            found.count.incrementAndGet();
            found.last = now;
            return found;
        });
    }

    private boolean expired(Attempts found, Instant now) {
        return found.last.plus(WINDOW).isBefore(now);
    }

    private static String emailKey(String email) {
        return "email:" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String clientIp) {
        return "ip:" + (clientIp == null ? "" : clientIp);
    }

    private static final class Attempts {
        private final AtomicInteger count = new AtomicInteger(1);
        private volatile Instant last;

        private Attempts(Instant now) {
            this.last = now;
        }
    }
}
