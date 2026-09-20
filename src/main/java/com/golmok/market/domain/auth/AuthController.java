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
                               @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return authService.login(request, userAgent);
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
