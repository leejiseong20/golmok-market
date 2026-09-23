package com.golmok.market.global.security;

import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * 테스트 메서드마다 인증 캐시(UserAccessCache)를 비운다. `META-INF/spring.factories` 로 모든 스프링 테스트에 붙는다.
 *
 * 캐시는 빈 하나라 테스트끼리 공유된다. 롤백한 테스트의 회원 번호가 다음 테스트에서 다시 쓰이면(H2),
 * 앞 테스트의 "일반 회원" 기록 때문에 새로 만든 관리자가 관리자 API 에서 막히는 식으로 서로 영향을 줬다.
 * 운영에서는 번호를 다시 쓰지 않아 생기지 않는 문제라, 캐시 쪽이 아니라 테스트 쪽에서 끊는다.
 */
public class UserAccessCacheResetListener implements TestExecutionListener {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        ApplicationContext context = testContext.getApplicationContext();
        context.getBeanProvider(UserAccessCache.class).ifAvailable(UserAccessCache::clear);
    }
}
