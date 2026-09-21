package com.golmok.market.domain.trade;

import com.golmok.market.domain.chat.ChatRoom;
import com.golmok.market.domain.chat.ChatRoomRepository;
import com.golmok.market.domain.chat.ChatService;
import com.golmok.market.domain.chat.dto.ChatRoomResponse;
import com.golmok.market.domain.notification.NotificationType;
import com.golmok.market.domain.notification.event.NotificationRequestedEvent;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅방에서 진행하는 직거래: 예약 → 거래완료, 또는 예약 취소.
 *
 * <pre>
 * 판매자: 예약      거래 REQUESTED  · 상품 예약중
 * 판매자: 거래완료  거래 CONFIRMED  · 상품 판매완료
 * 양쪽:   예약 취소 거래 CANCELED   · 상품 판매중
 * </pre>
 * 결제가 붙는 택배 거래는 이 경로가 아니라 PAID → SHIPPING → 구매자 구매확정을 쓴다.
 *
 * 잠금 순서는 상품 → 채팅방 → 거래다. 구매확정(상품 → 거래), 채팅하기(상품 → 방)와 같은 방향이라
 * 서로 기다리다 교착되지 않는다. 같은 상품을 두 방에서 동시에 예약하면 상품 잠금에서 한 줄로 서고,
 * 뒤 요청은 상품이 이미 예약중이라 실패한다. DB 의 "진행 중 거래는 상품당 1건" UNIQUE 가 최종 방어선이다.
 *
 * 변경마다 채팅방에 시스템 메시지를 남긴다. 일반 메시지와 같은 실시간 전달을 타므로
 * 상대 화면이 이 메시지를 받고 방 정보를 다시 불러와 거래 상태를 맞춘다.
 */
@Service
@RequiredArgsConstructor
public class ChatTradeService {

    private final ChatRoomRepository chatRoomRepository;
    private final ProductRepository productRepository;
    private final TradeRepository tradeRepository;
    private final ChatService chatService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ChatRoomResponse reserve(long roomId, AuthUser viewer) {
        Locked locked = lock(roomId, viewer);
        requireSeller(locked.room(), viewer);
        if (locked.room().getBuyer().isWithdrawn()) {
            throw new BusinessException(ErrorCode.CHAT_OPPONENT_WITHDRAWN);
        }
        // 상품이 판매중이 아니면(다른 방에 예약됨·판매완료) Product.reserve() 가 INVALID_STATE 로 막는다.
        tradeRepository.save(Trade.request(locked.product(), locked.room(), locked.room().getBuyer()));
        chatService.postSystemMessage(locked.room(), viewer.id(), "판매자가 예약했어요.");
        notifyOpponent(locked, viewer, "판매자가 예약했어요");
        return chatService.describe(locked.room(), viewer);
    }

    @Transactional
    public ChatRoomResponse cancel(long roomId, AuthUser viewer) {
        Locked locked = lock(roomId, viewer);
        Trade trade = reservation(roomId);
        boolean buyer = locked.room().isBuyer(viewer.id());
        trade.cancel(buyer ? "구매자가 예약 취소" : "판매자가 예약 취소");
        chatService.postSystemMessage(locked.room(), viewer.id(), (buyer ? "구매자" : "판매자") + "가 예약을 취소했어요.");
        notifyOpponent(locked, viewer, (buyer ? "구매자" : "판매자") + "가 예약을 취소했어요");
        return chatService.describe(locked.room(), viewer);
    }

    @Transactional
    public ChatRoomResponse complete(long roomId, AuthUser viewer) {
        Locked locked = lock(roomId, viewer);
        requireSeller(locked.room(), viewer);
        reservation(roomId).completeInPerson();
        chatService.postSystemMessage(locked.room(), viewer.id(), "거래가 완료됐어요.");
        notifyOpponent(locked, viewer, "거래가 완료됐어요. 후기를 남겨 주세요");
        return chatService.describe(locked.room(), viewer);
    }

    private record Locked(Product product, ChatRoom room) {
    }

    /**
     * 거래 상대에게 알림. 채팅방 시스템 메시지와 별개로, 방을 보고 있지 않은 상대가 알림함에서 알게 한다.
     * 누르면 이 채팅방으로 간다.
     */
    private void notifyOpponent(Locked locked, AuthUser viewer, String title) {
        eventPublisher.publishEvent(new NotificationRequestedEvent(locked.room().getOpponentId(viewer.id()),
                NotificationType.TRADE, title, locked.product().getTitle(),
                NotificationRequestedEvent.chatRoomUrl(locked.room().getId()), viewer.id()));
    }

    /** 상품 → 방 순서로 잠근다. 나가지 않은 참여자가 아니면 방이 없는 것처럼 404. */
    private Locked lock(long roomId, AuthUser viewer) {
        long productId = chatRoomRepository.findProductIdById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        Product product = productRepository.findByIdForUpdate(productId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        ChatRoom room = chatRoomRepository.findByIdForUpdate(roomId)
                .filter(found -> found.isActiveParticipant(viewer.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        return new Locked(product, room);
    }

    private static void requireSeller(ChatRoom room, AuthUser viewer) {
        if (room.isBuyer(viewer.id())) {
            throw new BusinessException(ErrorCode.SELLER_ONLY);
        }
    }

    private Trade reservation(long roomId) {
        return tradeRepository.findByChatRoomIdAndStatusForUpdate(roomId, TradeStatus.REQUESTED)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_RESERVATION));
    }
}
