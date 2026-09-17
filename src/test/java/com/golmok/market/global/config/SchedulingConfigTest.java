package com.golmok.market.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 예약 작업이 켜졌을 때 정리 배치가 설정한 시각으로 등록되는지.
 * 다른 테스트는 예약 작업을 끄므로 이 클래스만 켠다. 실제로 새벽 4시를 기다리지 않고 등록 여부만 본다.
 */
@SpringBootTest(properties = {"app.scheduling.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:scheduling-config;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SchedulingConfigTest {

    @Autowired ScheduledTaskHolder scheduledTasks;

    @Test
    void 정리_배치가_매일_새벽_4시_작업으로_등록된다() {
        assertThat(scheduledTasks.getScheduledTasks())
                .map(scheduled -> scheduled.getTask())
                .filteredOn(CronTask.class::isInstance)
                .map(task -> ((CronTask) task).getExpression())
                .contains("0 0 4 * * *");
    }
}
