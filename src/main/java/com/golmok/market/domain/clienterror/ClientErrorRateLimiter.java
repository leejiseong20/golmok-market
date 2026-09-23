package com.golmok.market.domain.clienterror;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 화면 오류 보고의 요청 수 제한.
 *
 * 로그인 없이 받는 주소라, 막지 않으면 보고를 쏟아부어 서버 로그(디스크)를 채울 수 있다.
 * 같은 IP 1분에 10건, 전체 1분에 300건까지 받는다. 화면은 한 페이지에서 최대 5건만 보내므로 정상 사용으로는 닿지 않는다.
 * 전체 한도를 두는 이유: IP 를 바꿔 가며 보내면 IP 한도만으로는 막히지 않는다(그때는 진짜 보고도 함께 버려지지만,
 * 로그가 넘치는 것보다 낫다).
 *
 * 구간은 1분 고정 창이다. 경계에 몰아 보내면 잠깐 두 배까지 들어올 수 있지만 이 용도에는 충분하다.
 * 저장소는 메모리다(LoginAttemptGuard 와 같은 선택 — 서버가 한 대다). 보고는 드물어 메서드 전체를 잠가도 된다.
 */
@Component
public class ClientErrorRateLimiter {

    static final int PER_IP_LIMIT = 10;
    static final int TOTAL_LIMIT = 300;
    static final Duration WINDOW = Duration.ofMinutes(1);
    /** IP 기록 수 상한. 넘으면 가장 오래된 것부터 밀어낸다(새 기록을 거부하면 제한 자체를 끌 수 있다). */
    static final int MAX_ENTRIES = 10_000;

    private final Clock clock;
    private final Map<String, Window> perIp = new LinkedHashMap<>(256, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Window> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
    private Window total;

    public ClientErrorRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** 받아도 되면 true 를 돌려주고 한 건으로 센다. IP 한도에 걸린 요청은 전체 한도를 쓰지 않는다. */
    public synchronized boolean tryAcquire(String clientIp) {
        Instant now = clock.instant();
        Window mine = perIp.compute(clientIp == null ? "" : clientIp,
                (ignored, found) -> found == null || found.expired(now) ? new Window(now) : found);
        if (total == null || total.expired(now)) {
            total = new Window(now);
        }
        if (mine.count >= PER_IP_LIMIT || total.count >= TOTAL_LIMIT) {
            return false;
        }
        mine.count++;
        total.count++;
        return true;
    }

    /** 테스트가 서로의 기록을 물려받지 않게 비운다(빈이 하나라 공유된다). 운영 코드에서는 부르지 않는다. */
    synchronized void reset() {
        perIp.clear();
        total = null;
    }

    synchronized int size() {
        return perIp.size();
    }

    private static final class Window {
        private final Instant start;
        private int count;

        private Window(Instant start) {
            this.start = start;
        }

        private boolean expired(Instant now) {
            return !now.isBefore(start.plus(WINDOW));
        }
    }
}
