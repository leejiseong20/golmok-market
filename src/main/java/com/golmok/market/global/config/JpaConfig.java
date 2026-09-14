package com.golmok.market.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseTimeEntity 의 @CreatedDate / @LastModifiedDate 를 동작시키려면
 * 이 설정이 반드시 있어야 한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
