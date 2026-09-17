package com.golmok.market.domain.notification;

import com.golmok.market.domain.notification.dto.NotificationResponse;
import com.golmok.market.domain.notification.dto.UnreadCountResponse;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.security.AuthUser;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 알림함. 모두 인증이 필요하다(SecurityConfig 의 anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public CursorResponse<NotificationResponse> findMine(@AuthenticationPrincipal AuthUser viewer,
                                                         @RequestParam(required = false) String cursor,
                                                         @RequestParam(required = false) Integer size) {
        return notificationService.findMine(viewer, cursor, size);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse countUnread(@AuthenticationPrincipal AuthUser viewer) {
        return notificationService.countUnread(viewer);
    }

    // "read-all" 이 {id} 패턴에 먼저 걸리지 않도록 경로를 따로 둔다(숫자가 아니라 400 이 날 수 있다).
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal AuthUser viewer) {
        notificationService.markAllAsRead(viewer);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable @Positive(message = "알림 ID는 양수여야 합니다.") long id,
                                           @AuthenticationPrincipal AuthUser viewer) {
        notificationService.markAsRead(id, viewer);
        return ResponseEntity.noContent().build();
    }
}
