package com.golmok.market.domain.notification;

import com.golmok.market.domain.block.BlockRepository;
import com.golmok.market.domain.notification.dto.NotificationResponse;
import com.golmok.market.domain.notification.dto.UnreadCountResponse;
import com.golmok.market.domain.notification.event.NotificationCreatedEvent;
import com.golmok.market.domain.notification.event.NotificationRequestedEvent;
import com.golmok.market.domain.product.FavoriteRepository;
import com.golmok.market.domain.product.event.ProductPriceDroppedEvent;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.pagination.Cursor;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.pagination.PageSize;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 알림 저장과 알림함 조회·읽음.
 *
 * 저장(create·notifyPriceDrop)은 원래 동작이 커밋된 뒤 NotificationEventListener 가 부른다.
 * REQUIRES_NEW 인 이유: 커밋 후 단계에는 원래 트랜잭션이 이미 끝나 있어 새 트랜잭션이 필요하고,
 * 알림 저장이 실패해도 이미 끝난 찜·거래를 되돌리지 않기 위해서다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(NotificationRequestedEvent request) {
        save(request);
    }

    /** 찜한 사람 수만큼 한 건씩 저장한다. 찜이 아주 많은 상품은 느려질 수 있다(알려진 한계). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyPriceDrop(ProductPriceDroppedEvent event) {
        String content = "%s · %s → %s".formatted(event.title(), price(event.oldPrice()), price(event.newPrice()));
        for (Long userId : favoriteRepository.findUserIdsByProductId(event.productId())) {
            save(new NotificationRequestedEvent(userId, NotificationType.PRICE_DROP, "찜한 상품의 가격이 내려갔어요",
                    content, NotificationRequestedEvent.productUrl(event.productId()), event.sellerId()));
        }
    }

    /** 화면의 가격 표기와 맞춘다. 0원은 나눔이다. */
    private static String price(int value) {
        return value == 0 ? "나눔" : "%,d원".formatted(value);
    }

    private void save(NotificationRequestedEvent request) {
        // 차단 관계면 보내지 않는다(어느 쪽이 차단했든). 차단은 "이 사람에게서 아무것도 받지 않겠다"는 뜻이다.
        if (request.actorId() != null && blockRepository.existsBetween(request.recipientId(), request.actorId())) {
            return;
        }
        // 탈퇴한 회원에게는 쌓지 않는다(탈퇴 때 알림함을 비웠고 다시 볼 사람이 없다). 없는 회원도 같이 거른다.
        if (userRepository.findById(request.recipientId()).filter(user -> !user.isWithdrawn()).isEmpty()) {
            return;
        }
        if (request.type().collapsesUnread() && notificationRepository.existsByUserIdAndTypeAndTargetUrlAndReadFalse(
                request.recipientId(), request.type(), request.targetUrl())) {
            return;
        }
        Notification notification = notificationRepository.save(Notification.of(
                userRepository.getReferenceById(request.recipientId()), request.type(),
                request.title(), request.content(), request.targetUrl()));
        // 이 알림 트랜잭션이 커밋된 뒤에 실시간으로 밀어준다(NotificationRealtimeRelay).
        eventPublisher.publishEvent(new NotificationCreatedEvent(request.recipientId(),
                NotificationResponse.from(notification)));
    }

    public CursorResponse<NotificationResponse> findMine(AuthUser viewer, String rawCursor, Integer size) {
        Cursor cursor = Cursor.parse(rawCursor);
        PageSize pageSize = PageSize.of(size);
        Pageable limit = PageRequest.of(0, pageSize.fetchSize());

        List<Notification> fetched = cursor == null
                ? notificationRepository.findFirstPage(viewer.id(), limit)
                : notificationRepository.findNextPage(viewer.id(), cursor.valueAsDateTime(), cursor.id(), limit);

        return CursorResponse.of(fetched, pageSize, notification -> Cursor.of(notification.getCreatedAt(), notification.getId()))
                .map(NotificationResponse::from);
    }

    public UnreadCountResponse countUnread(AuthUser viewer) {
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadFalse(viewer.id()));
    }

    /** 이미 읽은 알림을 다시 읽어도 성공이다. 남의 알림·없는 알림은 404. */
    @Transactional
    public void markAsRead(long id, AuthUser viewer) {
        notificationRepository.findByIdAndUserId(id, viewer.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND))
                .markAsRead();
    }

    @Transactional
    public void markAllAsRead(AuthUser viewer) {
        notificationRepository.markAllAsRead(viewer.id());
    }
}
