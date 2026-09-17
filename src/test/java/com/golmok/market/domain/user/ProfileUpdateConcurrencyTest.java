package com.golmok.market.domain.user;

import com.golmok.market.domain.user.dto.ProfileUpdateRequest;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * 두 사람이 거의 동시에 같은 닉네임으로 바꾸는 경우. 작업 스레드가 볼 수 있게 준비 데이터를 커밋하고,
 * 전용 H2 DB 를 컨텍스트 종료 시 폐기한다.
 *
 * 우연한 타이밍에 기대지 않도록, 두 요청이 모두 "중복 없음"을 확인한 뒤에야 저장으로 넘어가게 맞춘다.
 * 그러면 사전 확인만으로는 막을 수 없고, UNIQUE 위반을 409 로 바꾸는 처리가 있어야 통과한다.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:profile-concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProfileUpdateConcurrencyTest {

    @Autowired UserService userService;
    @MockitoSpyBean UserRepository userRepository;

    @Test
    void 같은_닉네임으로_동시에_바꾸면_하나만_성공하고_나머지는_409다() throws Exception {
        User first = userRepository.save(User.builder().email("first@example.com").password("hash").nickname("첫째").build());
        User second = userRepository.save(User.builder().email("second@example.com").password("hash").nickname("둘째").build());

        // 두 요청이 모두 중복 확인을 마친 지점에서 만나게 한다. 둘 다 아직 커밋 전이라 실제로도 "같은이름"은 없으므로
        // false 가 실제 조회 결과와 같다(저장소가 인터페이스 프록시라 진짜 메서드를 대신 부를 수 없다).
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        doAnswer(invocation -> {
            bothChecked.await(10, TimeUnit.SECONDS);
            return false;
        }).when(userRepository).existsByNickname(anyString());

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(rename(first, start));
            var b = executor.submit(rename(second, start));
            start.countDown();

            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", ErrorCode.DUPLICATE_NICKNAME.name());
        }
        assertThat(userRepository.findAll().stream().filter(user -> user.getNickname().equals("같은이름")).count())
                .isEqualTo(1);
    }

    private Callable<String> rename(User user, CountDownLatch start) {
        return () -> {
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                userService.updateProfile(new AuthUser(user.getId(), user.getRole()), new ProfileUpdateRequest("같은이름", null));
                return "OK";
            } catch (BusinessException e) {
                return e.getErrorCode().name();
            }
        };
    }
}
