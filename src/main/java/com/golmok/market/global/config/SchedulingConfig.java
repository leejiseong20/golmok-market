package com.golmok.market.global.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 예약 작업(매일 정리 배치) 설정.
 *
 * 예약 작업 전용 스케줄러를 따로 둔다. 따로 두지 않으면 WebSocket 설정이 만든 메시지 브로커 스케줄러(하트비트용)를
 * 찾아 쓰게 되어, 오래 걸리는 정리 작업이 채팅 하트비트와 스레드를 나눠 쓴다.
 * 빈으로 등록하지 않는 이유: TaskScheduler 빈이 두 개가 되면 WebSocket 설정의 스케줄러 주입이 모호해진다.
 *
 * 테스트에서는 app.scheduling.enabled=false 로 끈다. 테스트 도중 정리가 돌면 결과가 흔들린다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig implements SchedulingConfigurer, DisposableBean {

    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

    public SchedulingConfig() {
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduled-");
        scheduler.initialize();
    }

    /** 빈이 아니라 컨테이너가 닫아 주지 않으므로 직접 종료한다. 남겨 두면 스레드가 애플리케이션 종료를 막는다. */
    @Override
    public void destroy() {
        scheduler.shutdown();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(scheduler);
    }
}
