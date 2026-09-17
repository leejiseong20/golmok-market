package com.golmok.market.domain.cleanup;

import com.golmok.market.domain.notification.NotificationRepository;
import com.golmok.market.domain.search.SearchLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * 계속 쌓이는 데이터(검색 로그·알림)를 매일 정리한다.
 *
 * 한 번에 지우지 않고 batchSize 건씩 각각의 트랜잭션으로 지운다. 수십만 건을 한 트랜잭션에서 지우면
 * 트랜잭션이 길어지고 그동안 같은 테이블의 쓰기(알림 저장 등)가 오래 기다린다.
 * 지울 id 를 먼저 읽고 그 id 로 지운다. MySQL 전용 DELETE ... LIMIT 을 쓰지 않아 H2 테스트에서도 같은 코드가 검증된다.
 *
 * 서버 한 대 기준이다. 여러 대면 같은 시각에 모두 돌지만, 이미 지운 행은 다시 지워지지 않아 결과는 틀어지지 않는다.
 */
@Slf4j
@Component
// 설정값 등록은 스케줄링 설정(SchedulingConfig)이 아니라 여기서 한다. 스케줄링을 끈 테스트에서도 이 작업을 직접 실행할 수 있어야 한다.
@EnableConfigurationProperties(CleanupProperties.class)
public class DataCleanupJob {

    private final SearchLogRepository searchLogRepository;
    private final NotificationRepository notificationRepository;
    private final CleanupProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public DataCleanupJob(SearchLogRepository searchLogRepository, NotificationRepository notificationRepository,
                          CleanupProperties properties, PlatformTransactionManager transactionManager, Clock clock) {
        this.searchLogRepository = searchLogRepository;
        this.notificationRepository = notificationRepository;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** 정리 결과. 운영 로그로 남겨 얼마나 지워졌는지 추적한다. */
    public record CleanupResult(long searchLogs, long readNotifications, long unreadNotifications) {
    }

    @Scheduled(cron = "${app.cleanup.cron}", zone = "Asia/Seoul")
    public void runScheduled() {
        try {
            CleanupResult result = run();
            log.info("오래된 데이터 정리 완료: 검색 로그 {}건, 읽은 알림 {}건, 안 읽은 알림 {}건",
                    result.searchLogs(), result.readNotifications(), result.unreadNotifications());
        } catch (RuntimeException e) {
            // 실패해도 다음 날 다시 돈다. 스케줄러 스레드가 예외로 죽지 않게 기록만 한다.
            log.error("오래된 데이터 정리 실패", e);
        }
    }

    public CleanupResult run() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime searchLogBefore = now.minus(properties.searchLogRetention());
        LocalDateTime readBefore = now.minus(properties.readNotificationRetention());
        LocalDateTime unreadBefore = now.minus(properties.unreadNotificationRetention());

        long searchLogs = deleteInBatches(
                page -> searchLogRepository.findIdsCreatedBefore(searchLogBefore, page),
                searchLogRepository::deleteByIdIn);
        long readNotifications = deleteInBatches(
                page -> notificationRepository.findIdsCreatedBefore(true, readBefore, page),
                notificationRepository::deleteByIdIn);
        long unreadNotifications = deleteInBatches(
                page -> notificationRepository.findIdsCreatedBefore(false, unreadBefore, page),
                notificationRepository::deleteByIdIn);
        return new CleanupResult(searchLogs, readNotifications, unreadNotifications);
    }

    /**
     * 지울 id 를 batchSize 만큼 읽어 지우기를 반복한다. 한 묶음이 한 트랜잭션이다.
     * 가져온 수가 batchSize 보다 적으면 남은 것이 없으므로 끝낸다. 지운 행이 없으면(다른 서버가 먼저 지움 등) 멈춘다.
     */
    private long deleteInBatches(Function<Pageable, List<Long>> findIds, Function<List<Long>, Integer> deleteIds) {
        Pageable page = PageRequest.of(0, properties.batchSize());
        long total = 0;
        while (true) {
            int[] batch = transaction.execute(status -> {
                List<Long> ids = findIds.apply(page);
                return new int[]{ids.size(), ids.isEmpty() ? 0 : deleteIds.apply(ids)};
            });
            total += batch[1];
            if (batch[0] < properties.batchSize() || batch[1] == 0) {
                return total;
            }
        }
    }
}
