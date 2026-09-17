package com.golmok.market.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 작업용 실행기. 기본 실행기를 쓰지 않고 용도별로 크기를 정해 둔다.
 *
 * Spring 기본 실행기는 대기열 크기에 제한이 없어, 검색이 몰리면 밀린 작업이 메모리에 끝없이 쌓인다.
 * 검색 로그는 버려도 되는 부가 정보이므로 스레드 2개·대기열 1,000건으로 막고,
 * 넘치면 새 로그를 버리고 기록만 남긴다(검색 응답은 영향받지 않는다).
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String SEARCH_LOG_EXECUTOR = "searchLogExecutor";

    @Bean(name = SEARCH_LOG_EXECUTOR)
    public ThreadPoolTaskExecutor searchLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("search-log-");
        executor.setRejectedExecutionHandler((task, pool) -> log.warn("검색 로그 대기열이 가득 차 로그 1건을 버렸다"));
        // 종료할 때 대기 중인 로그를 잠깐 기다려 준다. 오래 붙잡지는 않는다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }
}
