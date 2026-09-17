package com.golmok.market.domain.cleanup;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;

/**
 * 오래된 데이터 정리 설정. 코드 수정 없이 보관 기간·실행 시각을 바꿀 수 있게 설정값으로 둔다.
 *
 * @param cron                        실행 시각(한국 시간). 기본 매일 새벽 4시
 * @param searchLogRetention          검색 로그 보관 기간. 인기 검색어는 24시간치만 쓰지만 문제 확인용 여유를 둔다
 * @param readNotificationRetention   읽은 알림 보관 기간
 * @param unreadNotificationRetention 안 읽은 알림 보관 기간. 오래 접속하지 않은 사람도 돌아와서 볼 여유를 둔다
 * @param batchSize                   한 트랜잭션에서 지우는 최대 행 수
 */
@ConfigurationProperties(prefix = "app.cleanup")
public record CleanupProperties(String cron, Duration searchLogRetention, Duration readNotificationRetention,
                                Duration unreadNotificationRetention, int batchSize) {

    public CleanupProperties {
        if (cron == null || !CronExpression.isValidExpression(cron)) {
            throw new IllegalStateException("app.cleanup.cron 이 올바른 cron 식이 아닙니다: " + cron);
        }
        requirePositive("search-log-retention", searchLogRetention);
        requirePositive("read-notification-retention", readNotificationRetention);
        requirePositive("unread-notification-retention", unreadNotificationRetention);
        if (batchSize < 1) {
            throw new IllegalStateException("app.cleanup.batch-size 는 1 이상이어야 합니다.");
        }
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalStateException("app.cleanup." + name + " 은 0보다 커야 합니다.");
        }
    }
}
