package com.golmok.market.domain.notification.event;

import com.golmok.market.domain.notification.dto.NotificationResponse;

/** 알림이 저장됐다. 알림 트랜잭션이 커밋되면 받는 사람의 개인 큐로 밀어준다. */
public record NotificationCreatedEvent(long recipientId, NotificationResponse notification) {
}
