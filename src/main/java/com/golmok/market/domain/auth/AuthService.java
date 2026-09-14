package com.golmok.market.domain.auth;

import com.golmok.market.domain.auth.dto.LoginRequest;
import com.golmok.market.domain.auth.dto.LoginResponse;
import com.golmok.market.domain.auth.dto.SignupRequest;
import com.golmok.market.domain.auth.dto.SignupResponse;
import com.golmok.market.domain.auth.dto.TokenResponse;
import com.golmok.market.domain.user.RefreshToken;
import com.golmok.market.domain.user.RefreshTokenRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRegionRepository;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.security.AuthUser;
import com.golmok.market.global.security.JwtProperties;
import com.golmok.market.global.security.JwtTokenProvider;
import com.golmok.market.global.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 가입 · 로그인 · 토큰 재발급 · 로그아웃.
 *
 * 토큰 정책
 * - access token : JWT, 30분. 서버에 저장하지 않는다(요청마다 DB 조회 없음).
 * - refresh token: 무작위 문자열, 14일. DB 에는 SHA-256 해시만 저장한다.
 * - 재발급 시 refresh token 도 새로 발급하고 기존 것은 폐기한다(rotation).
 * - 여러 기기 로그인을 허용한다. 로그인할 때마다 refresh token 이 한 행씩 생긴다.
 *
 * 로그아웃해도 이미 발급된 access token 은 만료(최대 30분)까지 유효하다.
 * 이를 막으려면 요청마다 블랙리스트를 조회해야 하는데, 그러면 JWT 를 쓰는 이유(무상태)가 사라진다.
 * access token 만료를 짧게 둔 것이 이 트레이드오프에 대한 대응이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final int DEVICE_INFO_MAX_LENGTH = 200;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRegionRepository userRegionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    // ---------- 가입 ----------

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = request.normalizedEmail();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .phone(request.phone())
                .build();

        try {
            // 위의 exists 확인과 INSERT 사이에 같은 이메일로 먼저 가입될 수 있다(check-then-act 경쟁).
            // 최종 방어선은 DB 의 UNIQUE 제약이다. flush 를 여기서 해야 예외를 이 메서드 안에서 잡을 수 있다.
            // (flush 하지 않으면 커밋 시점, 즉 메서드가 끝난 뒤에 터져 409 로 바꿀 기회가 없다.)
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 어느 제약(이메일/닉네임)인지는 DB 마다 메시지 형식이 달라 구분하지 않는다. 드문 경쟁 상황이다.
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "이미 사용 중인 이메일 또는 닉네임입니다.");
        }
        return SignupResponse.from(user);
    }

    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email.trim().toLowerCase(Locale.ROOT));
    }

    public boolean isNicknameAvailable(String nickname) {
        return !userRepository.existsByNickname(nickname);
    }

    // ---------- 로그인 ----------

    @Transactional
    public LoginResponse login(LoginRequest request, String userAgent) {
        User user = userRepository.findByEmail(request.normalizedEmail())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
                // 이메일이 없는 경우와 비밀번호가 틀린 경우를 같은 응답으로 준다.
                // 다르게 주면 로그인 API 로 가입 여부를 대량 조회할 수 있다.
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        // 비밀번호 확인 뒤에 상태를 본다. 비밀번호를 모르는 사람에게 "정지된 계정"인지 알려줄 이유가 없다.
        requireActive(user);

        user.recordLogin();
        TokenResponse tokens = issueTokens(user, userAgent);
        var primaryRegion = userRegionRepository.findPrimaryWithRegion(user.getId()).orElse(null);
        return LoginResponse.of(tokens, user, primaryRegion);
    }

    // ---------- 재발급 ----------

    /**
     * refresh token rotation.
     *
     * noRollbackFor: 만료·정지 계정의 토큰을 삭제한 뒤 예외를 던지는데,
     * 기본 설정이면 예외 때문에 삭제까지 롤백돼 쓸모없는 토큰이 DB 에 남는다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse reissue(String rawRefreshToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenForUpdate(TokenHasher.hash(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = refreshToken.getUser();
        if (!user.isActive()) {
            refreshTokenRepository.delete(refreshToken);
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }

        String newRawToken = TokenHasher.generate();
        refreshToken.rotate(TokenHasher.hash(newRawToken), refreshTokenExpiresAt());
        return new TokenResponse(jwtTokenProvider.createAccessToken(user.getId(), user.getRole()), newRawToken);
    }

    // ---------- 로그아웃 ----------

    /**
     * 본인 토큰일 때만 삭제한다. 없는 토큰이거나 남의 토큰이어도 에러 없이 끝낸다(멱등).
     * 남의 토큰이라고 알려주면 "이 값이 유효한 토큰인지"를 확인하는 수단이 된다.
     */
    @Transactional
    public void logout(AuthUser authUser, String rawRefreshToken) {
        refreshTokenRepository.findByToken(TokenHasher.hash(rawRefreshToken))
                .filter(token -> token.getUser().getId().equals(authUser.id()))
                .ifPresent(refreshTokenRepository::delete);
    }

    // ---------- 내부 ----------

    private TokenResponse issueTokens(User user, String userAgent) {
        String rawRefreshToken = TokenHasher.generate();
        refreshTokenRepository.save(RefreshToken.issue(
                user, TokenHasher.hash(rawRefreshToken), summarize(userAgent), refreshTokenExpiresAt()));
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        return new TokenResponse(accessToken, rawRefreshToken);
    }

    private LocalDateTime refreshTokenExpiresAt() {
        return LocalDateTime.now(clock).plus(jwtProperties.refreshTokenValidity());
    }

    private void requireActive(User user) {
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
    }

    /** device_info 컬럼은 200자. User-Agent 는 이보다 길 수 있다. */
    private String summarize(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.length() > DEVICE_INFO_MAX_LENGTH
                ? userAgent.substring(0, DEVICE_INFO_MAX_LENGTH)
                : userAgent;
    }
}
