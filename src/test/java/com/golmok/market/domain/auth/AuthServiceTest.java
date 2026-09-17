package com.golmok.market.domain.auth;

import com.golmok.market.domain.auth.dto.LoginRequest;
import com.golmok.market.domain.auth.dto.LoginResponse;
import com.golmok.market.domain.auth.dto.SignupRequest;
import com.golmok.market.domain.auth.dto.SignupResponse;
import com.golmok.market.domain.auth.dto.TokenResponse;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.user.RefreshToken;
import com.golmok.market.domain.user.RefreshTokenRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRegion;
import com.golmok.market.domain.user.UserRegionRepository;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.domain.user.UserRole;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.security.AuthUser;
import com.golmok.market.global.security.TokenHasher;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AuthServiceTest {

    private static final String PASSWORD = "Password123!";
    private static final String USER_AGENT = "JUnit";

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserRegionRepository userRegionRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager em;

    private SignupResponse signup(String email, String nickname) {
        return authService.signup(new SignupRequest(email, PASSWORD, nickname, null));
    }

    private LoginResponse login(String email) {
        return authService.login(new LoginRequest(email, PASSWORD), USER_AGENT);
    }

    private static void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    // ---------- 가입 ----------

    @Test
    void 가입하면_비밀번호는_BCrypt_로_저장되고_이메일은_소문자로_정규화된다() {
        SignupResponse response = signup("  User@Example.COM ", "골목이");

        User user = userRepository.findById(response.id()).orElseThrow();
        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getPassword()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, user.getPassword())).isTrue();
    }

    @Test
    void 이메일이_중복이면_DUPLICATE_EMAIL() {
        signup("user@example.com", "골목이");

        // 대소문자만 다른 이메일도 같은 이메일로 본다
        assertErrorCode(() -> signup("USER@example.com", "다른닉네임"), ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void 닉네임이_중복이면_DUPLICATE_NICKNAME() {
        signup("user@example.com", "골목이");

        assertErrorCode(() -> signup("other@example.com", "골목이"), ErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    void 중복_확인은_가입_여부를_반영한다() {
        signup("user@example.com", "골목이");

        assertThat(authService.isEmailAvailable("User@Example.com")).isFalse();
        assertThat(authService.isEmailAvailable("new@example.com")).isTrue();
        assertThat(authService.isNicknameAvailable("골목이")).isFalse();
        assertThat(authService.isNicknameAvailable("새닉네임")).isTrue();
    }

    // ---------- 로그인 ----------

    @Test
    void 로그인하면_토큰이_발급되고_DB_에는_refresh_token_해시만_저장된다() {
        signup("user@example.com", "골목이");

        LoginResponse response = login("user@example.com");

        assertThat(response.accessToken()).isNotBlank();
        assertThat(refreshTokenRepository.findByToken(response.refreshToken())).isEmpty();
        RefreshToken stored = refreshTokenRepository.findByToken(TokenHasher.hash(response.refreshToken())).orElseThrow();
        assertThat(stored.getDeviceInfo()).isEqualTo(USER_AGENT);
        assertThat(stored.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(13));
    }

    @Test
    void 대표_동네가_없으면_primaryRegion_은_null() {
        signup("user@example.com", "골목이");

        assertThat(login("user@example.com").user().primaryRegion()).isNull();
    }

    @Test
    void 대표_동네가_있으면_동_이름을_내려준다() {
        SignupResponse signup = signup("user@example.com", "골목이");
        jdbcTemplate.update("""
                insert into regions (sido, sigungu, dong, lat, lng, created_at)
                values ('서울특별시', '강남구', '역삼동', 37.5006, 127.0366, current_timestamp)
                """);
        Long regionId = jdbcTemplate.queryForObject("select id from regions where dong = '역삼동'", Long.class);
        User user = userRepository.findById(signup.id()).orElseThrow();
        userRegionRepository.save(UserRegion.verify(user, em.find(Region.class, regionId), true));

        LoginResponse.RegionInfo region = login("user@example.com").user().primaryRegion();

        assertThat(region.id()).isEqualTo(regionId);
        assertThat(region.name()).isEqualTo("역삼동");
    }

    @Test
    void 비밀번호가_틀린_경우와_없는_이메일은_같은_LOGIN_FAILED() {
        signup("user@example.com", "골목이");

        assertErrorCode(() -> authService.login(new LoginRequest("user@example.com", "Wrong123!"), USER_AGENT),
                ErrorCode.LOGIN_FAILED);
        assertErrorCode(() -> login("nobody@example.com"), ErrorCode.LOGIN_FAILED);
    }

    @Test
    void 탈퇴하면_원래_이메일로는_로그인할_수_없다() {
        // 탈퇴 때 이메일을 익명화하므로 원래 이메일로는 계정을 찾을 수 없다. 가입 여부를 드러내지 않게 일반 로그인 실패와 같다.
        SignupResponse signup = signup("user@example.com", "골목이");
        userRepository.findById(signup.id()).orElseThrow().withdraw(LocalDateTime.now());
        em.flush();

        assertErrorCode(() -> login("user@example.com"), ErrorCode.LOGIN_FAILED);
    }

    @Test
    void 여러_기기에서_로그인하면_refresh_token_이_각각_유지된다() {
        signup("user@example.com", "골목이");

        LoginResponse phone = login("user@example.com");
        LoginResponse laptop = login("user@example.com");

        assertThat(refreshTokenRepository.findByToken(TokenHasher.hash(phone.refreshToken()))).isPresent();
        assertThat(refreshTokenRepository.findByToken(TokenHasher.hash(laptop.refreshToken()))).isPresent();
    }

    // ---------- 재발급 ----------

    @Test
    void 재발급하면_새_토큰을_주고_이전_refresh_token_은_더_이상_쓸_수_없다() {
        signup("user@example.com", "골목이");
        String oldRefreshToken = login("user@example.com").refreshToken();

        TokenResponse reissued = authService.reissue(oldRefreshToken);

        assertThat(reissued.refreshToken()).isNotEqualTo(oldRefreshToken);
        assertThat(authService.reissue(reissued.refreshToken()).accessToken()).isNotBlank();
        assertErrorCode(() -> authService.reissue(oldRefreshToken), ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void 없는_refresh_token_은_INVALID_REFRESH_TOKEN() {
        assertErrorCode(() -> authService.reissue("never-issued-token"), ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void 만료된_refresh_token_은_거부하고_DB_에서_삭제한다() {
        signup("user@example.com", "골목이");
        String raw = login("user@example.com").refreshToken();
        String hash = TokenHasher.hash(raw);
        refreshTokenRepository.findByToken(hash).orElseThrow().rotate(hash, LocalDateTime.now().minusMinutes(1));
        em.flush();

        assertErrorCode(() -> authService.reissue(raw), ErrorCode.INVALID_REFRESH_TOKEN);
        assertThat(refreshTokenRepository.findByToken(hash)).isEmpty();
    }

    @Test
    void 탈퇴한_계정의_refresh_token_은_거부하고_삭제한다() {
        SignupResponse signup = signup("user@example.com", "골목이");
        String raw = login("user@example.com").refreshToken();
        userRepository.findById(signup.id()).orElseThrow().withdraw(LocalDateTime.now());
        em.flush();

        assertErrorCode(() -> authService.reissue(raw), ErrorCode.USER_NOT_ACTIVE);
        assertThat(refreshTokenRepository.findByToken(TokenHasher.hash(raw))).isEmpty();
    }

    // ---------- 로그아웃 ----------

    @Test
    void 로그아웃하면_해당_refresh_token_만_삭제된다() {
        SignupResponse signup = signup("user@example.com", "골목이");
        LoginResponse phone = login("user@example.com");
        LoginResponse laptop = login("user@example.com");

        authService.logout(new AuthUser(signup.id(), UserRole.USER), phone.refreshToken());

        assertErrorCode(() -> authService.reissue(phone.refreshToken()), ErrorCode.INVALID_REFRESH_TOKEN);
        assertThat(authService.reissue(laptop.refreshToken()).accessToken()).isNotBlank();
    }

    @Test
    void 남의_refresh_token_으로는_로그아웃되지_않는다() {
        signup("victim@example.com", "피해자");
        SignupResponse attacker = signup("attacker@example.com", "공격자");
        String victimToken = login("victim@example.com").refreshToken();

        authService.logout(new AuthUser(attacker.id(), UserRole.USER), victimToken);

        assertThat(refreshTokenRepository.findByToken(TokenHasher.hash(victimToken))).isPresent();
    }
}
