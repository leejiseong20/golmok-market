package com.golmok.market.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.golmok.market.domain.notification.Notification;
import com.golmok.market.domain.notification.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String content,
        String targetUrl,
        boolean read,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getContent(), notification.getTargetUrl(), notification.isRead(),
                notification.getCreatedAt());
    }
}
