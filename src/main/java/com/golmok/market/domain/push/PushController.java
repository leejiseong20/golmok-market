package com.golmok.market.domain.push;

import com.golmok.market.domain.push.dto.PushPublicKeyResponse;
import com.golmok.market.domain.push.dto.PushSubscribeRequest;
import com.golmok.market.domain.push.dto.PushUnsubscribeRequest;
import com.golmok.market.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 웹 푸시. 모두 로그인이 필요하다(SecurityConfig 의 기본 차단 규칙). */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;

    @GetMapping("/public-key")
    public PushPublicKeyResponse publicKey() {
        return pushService.publicKey();
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@Valid @RequestBody PushSubscribeRequest request, @AuthenticationPrincipal AuthUser viewer) {
        pushService.subscribe(viewer, request);
    }

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody PushUnsubscribeRequest request, @AuthenticationPrincipal AuthUser viewer) {
        pushService.unsubscribe(viewer, request.endpoint());
    }
}
