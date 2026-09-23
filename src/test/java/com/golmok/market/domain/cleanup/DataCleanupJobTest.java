package com.golmok.market.domain.cleanup;

import com.golmok.market.domain.image.ImageProperties;
import com.golmok.market.domain.image.OrphanImageCleaner;
import com.golmok.market.domain.notification.Notification;
import com.golmok.market.domain.notification.NotificationRepository;
import com.golmok.market.domain.notification.NotificationType;
import com.golmok.market.domain.product.ProductImageRepository;
import com.golmok.market.domain.search.SearchLog;
import com.golmok.market.domain.search.SearchLogRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 오래된 검색 로그·알림 정리. 시계를 고정하고, 배치 크기를 작게 잡아 여러 번 나눠 지우는 것까지 본다. */
@SpringBootTest
@Transactional
class DataCleanupJobTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-17T19:00:00Z"), ZONE);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Autowired SearchLogRepository searchLogRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager em;
    @Autowired ProductImageRepository productImageRepository;

    private User user;

    @BeforeEach
    void 준비() {
        user = userRepository.save(User.builder().email("cleanup@example.com").password("hash").nickname("정리").build());
    }

    private DataCleanupJob job(int batchSize) {
        CleanupProperties properties = new CleanupProperties("0 0 4 * * *",
                Duration.ofDays(7), Duration.ofDays(30), Duration.ofDays(90), batchSize, Duration.ofDays(3), 500);
        // 사진 정리는 OrphanImageCleanerTest 에서 본다. 여기서는 DB 정리만 보므로 없는 폴더를 가리켜 아무것도 지우지 않게 한다.
        OrphanImageCleaner imageCleaner = new OrphanImageCleaner(
                new ImageProperties("build/tmp/uploads-없음", 10, 640), productImageRepository, CLOCK);
        return new DataCleanupJob(searchLogRepository, notificationRepository,
                imageCleaner, properties, transactionManager, CLOCK);
    }

    /** 생성 시각은 저장 때 자동으로 채워지므로 저장 뒤 원하는 시각으로 바꾼다. */
    private long searchLog(String keyword, LocalDateTime createdAt) {
        SearchLog saved = searchLogRepository.saveAndFlush(SearchLog.of(null, null, keyword));
        em.createQuery("update SearchLog s set s.createdAt = :at where s.id = :id")
                .setParameter("at", createdAt).setParameter("id", saved.getId()).executeUpdate();
        return saved.getId();
    }

    private long notification(boolean read, LocalDateTime createdAt) {
        Notification notification = Notification.of(user, NotificationType.TRADE, "알림", null, "/chat-rooms/1");
        if (read) {
            notification.markAsRead();
        }
        Notification saved = notificationRepository.saveAndFlush(notification);
        em.createQuery("update Notification n set n.createdAt = :at where n.id = :id")
                .setParameter("at", createdAt).setParameter("id", saved.getId()).executeUpdate();
        return saved.getId();
    }

    @Test
    void 검색_로그는_7일보다_오래된_것만_지운다() {
        long old = searchLog("오래됨", NOW.minusDays(7).minusSeconds(1));
        long boundary = searchLog("딱7일", NOW.minusDays(7));
        long recent = searchLog("최근", NOW.minusHours(1));

        DataCleanupJob.CleanupResult result = job(1000).run();
        em.clear();

        assertThat(result.searchLogs()).isEqualTo(1);
        assertThat(searchLogRepository.existsById(old)).isFalse();
        assertThat(searchLogRepository.existsById(boundary)).isTrue();
        assertThat(searchLogRepository.existsById(recent)).isTrue();
    }

    @Test
    void 알림은_읽었으면_30일_안_읽었으면_90일이_지나야_지운다() {
        long readOld = notification(true, NOW.minusDays(30).minusSeconds(1));
        long readRecent = notification(true, NOW.minusDays(29));
        long unreadBetween = notification(false, NOW.minusDays(60));
        long unreadOld = notification(false, NOW.minusDays(90).minusSeconds(1));

        DataCleanupJob.CleanupResult result = job(1000).run();
        em.clear();

        assertThat(result.readNotifications()).isEqualTo(1);
        assertThat(result.unreadNotifications()).isEqualTo(1);
        assertThat(notificationRepository.existsById(readOld)).isFalse();
        assertThat(notificationRepository.existsById(readRecent)).isTrue();
        // 60일 된 안 읽은 알림은 읽은 알림 기준(30일)을 넘었지만 안 읽었으므로 남는다.
        assertThat(notificationRepository.existsById(unreadBetween)).isTrue();
        assertThat(notificationRepository.existsById(unreadOld)).isFalse();
    }

    @Test
    void 배치_크기보다_많으면_여러_번_나눠_끝까지_지운다() {
        for (int i = 0; i < 5; i++) {
            searchLog("오래됨" + i, NOW.minusDays(10));
        }
        long recent = searchLog("최근", NOW);

        DataCleanupJob.CleanupResult result = job(2).run();
        em.clear();

        assertThat(result.searchLogs()).isEqualTo(5);
        assertThat(searchLogRepository.count()).isEqualTo(1);
        assertThat(searchLogRepository.existsById(recent)).isTrue();
    }

    @Test
    void 지울_것이_없으면_아무것도_지우지_않는다() {
        searchLog("최근", NOW);
        notification(false, NOW);

        assertThat(job(2).run()).isEqualTo(new DataCleanupJob.CleanupResult(0, 0, 0, 0));
    }

    @Test
    void 잘못된_설정은_기동할_때_막는다() {
        assertThatThrownBy(() -> new CleanupProperties("매일 새벽", Duration.ofDays(7), Duration.ofDays(30), Duration.ofDays(90), 1000, Duration.ofDays(3), 500))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CleanupProperties("0 0 4 * * *", Duration.ZERO, Duration.ofDays(30), Duration.ofDays(90), 1000, Duration.ofDays(3), 500))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CleanupProperties("0 0 4 * * *", Duration.ofDays(7), Duration.ofDays(30), Duration.ofDays(90), 0, Duration.ofDays(3), 500))
                .isInstanceOf(IllegalStateException.class);
        // 사진 정리 설정도 같은 기준으로 막는다.
        assertThatThrownBy(() -> new CleanupProperties("0 0 4 * * *", Duration.ofDays(7), Duration.ofDays(30), Duration.ofDays(90), 1000, Duration.ZERO, 500))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CleanupProperties("0 0 4 * * *", Duration.ofDays(7), Duration.ofDays(30), Duration.ofDays(90), 1000, Duration.ofDays(3), 0))
                .isInstanceOf(IllegalStateException.class);
    }
}
