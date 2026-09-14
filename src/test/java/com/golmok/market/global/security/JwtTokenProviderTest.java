package com.golmok.market.global.security;

import com.golmok.market.domain.user.UserRole;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 스프링 없이 순수 단위 테스트. Clock 을 고정해 만료를 실제로 기다리지 않고 검증한다.
 */
class JwtTokenProviderTest {

    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");

    private static String secret(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static final String SECRET = secret("test-secret-key-for-jwt-provider-unit-test!!");

    private static JwtTokenProvider providerAt(Instant instant, String secret) {
        JwtProperties properties = new JwtProperties(secret, Duration.ofMinutes(30), Duration.ofDays(14));
        return new JwtTokenProvider(properties, Clock.fixed(instant, ZoneId.of("Asia/Seoul")));
    }

    @Test
    void 발급한_토큰을_검증하면_사용자_id_와_권한이_나온다() {
        JwtTokenProvider provider = providerAt(NOW, SECRET);

        AuthUser authUser = provider.parseAccessToken(provider.createAccessToken(7L, UserRole.ADMIN));

        assertThat(authUser.id()).isEqualTo(7L);
        assertThat(authUser.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void 유효기간_안에서는_검증에_성공한다() {
        String token = providerAt(NOW, SECRET).createAccessToken(1L, UserRole.USER);

        AuthUser authUser = providerAt(NOW.plus(Duration.ofMinutes(29)), SECRET).parseAccessToken(token);

        assertThat(authUser.id()).isEqualTo(1L);
    }

    @Test
    void 유효기간이_지나면_EXPIRED_TOKEN() {
        String token = providerAt(NOW, SECRET).createAccessToken(1L, UserRole.USER);

        assertThatThrownBy(() -> providerAt(NOW.plus(Duration.ofMinutes(31)), SECRET).parseAccessToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EXPIRED_TOKEN);
    }

    @Test
    void 다른_키로_서명된_토큰은_INVALID_TOKEN() {
        String forged = providerAt(NOW, secret("attacker-owned-secret-key-with-enough-bits!!")).createAccessToken(1L, UserRole.ADMIN);

        assertThatThrownBy(() -> providerAt(NOW, SECRET).parseAccessToken(forged))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 페이로드를_변조하면_INVALID_TOKEN() {
        JwtTokenProvider provider = providerAt(NOW, SECRET);
        String[] parts = provider.createAccessToken(1L, UserRole.USER).split("\\.");
        // role 을 ADMIN 으로 바꾼 페이로드에 원래 서명을 붙인다
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"role\":\"ADMIN\",\"iat\":1789380000,\"exp\":1789381800}".getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThatThrownBy(() -> provider.parseAccessToken(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 서명이_없는_alg_none_토큰은_INVALID_TOKEN() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\",\"role\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> providerAt(NOW, SECRET).parseAccessToken(header + "." + payload + "."))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 형식이_아예_틀린_문자열은_INVALID_TOKEN() {
        assertThatThrownBy(() -> providerAt(NOW, SECRET).parseAccessToken("not-a-jwt"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 키가_256비트_미만이면_생성_단계에서_실패한다() {
        assertThatThrownBy(() -> providerAt(NOW, secret("too-short-key")))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    void refresh_token_해시는_결정적이고_원문과_다르다() {
        String raw = TokenHasher.generate();

        assertThat(raw).hasSize(43);
        assertThat(TokenHasher.hash(raw)).hasSize(64).isEqualTo(TokenHasher.hash(raw)).isNotEqualTo(raw);
        assertThat(TokenHasher.generate()).isNotEqualTo(raw);
    }
}
