package com.golmok.market.domain.auth;

import com.golmok.market.domain.auth.dto.AvailabilityResponse;
import com.golmok.market.domain.auth.dto.LoginRequest;
import com.golmok.market.domain.auth.dto.LoginResponse;
import com.golmok.market.domain.auth.dto.LogoutRequest;
import com.golmok.market.domain.auth.dto.ReissueRequest;
import com.golmok.market.domain.auth.dto.SignupRequest;
import com.golmok.market.domain.auth.dto.SignupResponse;
import com.golmok.market.domain.auth.dto.TokenResponse;
import com.golmok.market.global.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
                               @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                               HttpServletRequest servletRequest) {
        return authService.login(request, userAgent, clientIp(servletRequest));
    }

    /**
     * 실패 횟수를 셀 때 쓰는 클라이언트 주소(LoginAttemptGuard).
     *
     * 운영에서는 Caddy 를 거치므로 소켓 주소는 항상 Caddy 다. Caddy 는 X-Forwarded-For 뒤에 실제 접속 IP 를 덧붙이므로
     * **맨 뒤 값**이 우리 앞단이 직접 본 주소다. 앞쪽 값은 클라이언트가 마음대로 적어 보낼 수 있어 믿지 않는다.
     * 헤더가 없으면(로컬 개발·직접 접속) 소켓 주소를 쓴다.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] hops = forwarded.split(",");
        return hops[hops.length - 1].trim();
    }

    @PostMapping("/reissue")
    public TokenResponse reissue(@Valid @RequestBody ReissueRequest request) {
        return authService.reissue(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthUser authUser,
                                       @Valid @RequestBody LogoutRequest request) {
        authService.logout(authUser, request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check-email")
    public AvailabilityResponse checkEmail(@RequestParam @NotBlank(message = "이메일을 입력해 주세요.") String email) {
        return new AvailabilityResponse(authService.isEmailAvailable(email));
    }

    @GetMapping("/check-nickname")
    public AvailabilityResponse checkNickname(@RequestParam @NotBlank(message = "닉네임을 입력해 주세요.") String nickname) {
        return new AvailabilityResponse(authService.isNicknameAvailable(nickname));
    }
}
