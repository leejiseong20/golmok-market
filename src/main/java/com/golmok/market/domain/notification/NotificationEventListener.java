package com.golmok.market.domain.notification;

import com.golmok.market.domain.notification.event.NotificationRequestedEvent;
import com.golmok.market.domain.product.event.ProductPriceDroppedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 원래 동작이 커밋된 뒤에 알림을 만든다(@TransactionalEventListener 기본값 AFTER_COMMIT).
 *
 * - 원래 동작이 롤백되면 이벤트가 버려져 알림도 생기지 않는다.
 * - 알림 저장이 실패해도 이미 커밋된 요청이 실패 응답으로 바뀌면 안 된다. 실패 응답을 받은 사용자가
 *   찜·거래완료를 다시 누르면 오히려 상태가 꼬인다. Spring 도 커밋 후 리스너의 예외는 기록만 하고 삼키지만
 *   (afterCompletion 단계), 프레임워크 내부 동작에 기대지 않고 어떤 알림이 빠졌는지 남기려고 직접 잡는다.
 *   NotificationFlowTest 는 이 try/catch 를 지워도 통과한다(프레임워크가 삼키므로). 결과를 지키는 테스트다.
 * - 대가: 커밋 직후 서버가 죽으면 그 알림은 빠진다. 알림은 놓쳐도 원래 데이터가 맞으면 되는 부가 정보라 받아들인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener
    public void onRequested(NotificationRequestedEvent event) {
        try {
            notificationService.create(event);
        } catch (RuntimeException e) {
            log.warn("알림 저장 실패 recipientId={}, type={}", event.recipientId(), event.type(), e);
        }
    }

    @TransactionalEventListener
    public void onPriceDropped(ProductPriceDroppedEvent event) {
        try {
            notificationService.notifyPriceDrop(event);
        } catch (RuntimeException e) {
            log.warn("가격 인하 알림 저장 실패 productId={}", event.productId(), e);
        }
    }
}
