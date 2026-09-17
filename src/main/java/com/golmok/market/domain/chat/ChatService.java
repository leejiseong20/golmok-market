package com.golmok.market.domain.chat;

import com.golmok.market.domain.chat.dto.ChatMessageResponse;
import com.golmok.market.domain.chat.dto.ChatMessageSendRequest;
import com.golmok.market.domain.chat.dto.ChatRoomResponse;
import com.golmok.market.domain.chat.dto.ChatRoomSummaryResponse;
import com.golmok.market.domain.chat.event.ChatMessageSentEvent;
import com.golmok.market.domain.chat.event.ChatMessagesReadEvent;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.product.ProductStatus;
import com.golmok.market.domain.product.ProductThumbnails;
import com.golmok.market.domain.trade.Trade;
import com.golmok.market.domain.trade.TradeRepository;
import com.golmok.market.domain.trade.TradeStatus;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 1:1 채팅. 방 만들기 · 목록 · 메시지 조회/전송 · 읽음 · 나가기.
 *
 * 모든 방 기능은 "나가지 않은 참여자"만 쓸 수 있고, 아니면 404 로 답한다.
 * 저장·권한·읽음 규칙은 여기 한 곳에만 둔다. 실시간 전달은 변경 후 이벤트를 발행하고
 * {@link ChatRealtimeRelay} 가 커밋 뒤에 WebSocket 으로 밀어준다(서비스는 WebSocket 을 모른다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductThumbnails productThumbnails;
    private final ApplicationEventPublisher eventPublisher;
    private final TradeRepository tradeRepository;

    /** 채팅하기 결과. 새 방이면 201, 기존 방이면 200 으로 답하기 위해 생성 여부를 함께 돌려준다. */
    public record OpenResult(ChatRoomResponse room, boolean created) {
    }

    /**
     * 채팅하기. 같은 상품에 이미 방이 있으면 그 방을 돌려준다(여러 번 눌러도 방은 하나).
     *
     * 상품 행을 먼저 잠가 같은 상품의 방 만들기를 한 줄로 세운다. 더블클릭으로 두 요청이 동시에 와도
     * 두 번째 요청은 첫 번째가 만든 방을 찾아 돌려준다. UNIQUE (product_id, buyer_id) 는 최종 방어선이다.
     * 찜처럼 UNIQUE 위반을 잡아 처리하지 않는 이유: 위반이 난 트랜잭션은 롤백 전용이 되어
     * 같은 트랜잭션에서 기존 방을 다시 조회해 돌려줄 수 없다.
     */
    @Transactional
    public OpenResult open(long productId, AuthUser viewer) {
        Product product = productRepository.findByIdForUpdate(productId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.isOwnedBy(viewer.id())) {
            throw new BusinessException(ErrorCode.CANNOT_CHAT_OWN_PRODUCT);
        }

        Optional<ChatRoom> existing = chatRoomRepository.findByProductIdAndBuyerIdForUpdate(productId, viewer.id());
        if (existing.isPresent()) {
            // 거래가 끝난 상품이라도 이전 대화는 다시 볼 수 있어야 한다. 나갔던 방이면 목록에 되살린다.
            ChatRoom room = existing.get();
            room.rejoinAsBuyer();
            return new OpenResult(describe(room, viewer), false);
        }

        if (product.getStatus() == ProductStatus.SOLD) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_CHATTABLE);
        }
        ChatRoom room = chatRoomRepository.save(
                ChatRoom.open(product, userRepository.getReferenceById(viewer.id())));
        productRepository.incrementChatCount(productId);
        return new OpenResult(describe(room, viewer), true);
    }

    /** 내 채팅 목록. 최근 메시지 순. */
    public CursorResponse<ChatRoomSummaryResponse> findMyRooms(AuthUser viewer, String rawCursor, Integer size) {
        Cursor cursor = Cursor.parse(rawCursor);
        PageSize pageSize = PageSize.of(size);
        Pageable limit = PageRequest.of(0, pageSize.fetchSize());

        List<ChatRoom> fetched = cursor == null
                ? chatRoomRepository.findMyRooms(viewer.id(), limit)
                : chatRoomRepository.findMyRoomsAfter(viewer.id(), cursor.valueAsDateTime(), cursor.id(), limit);

        CursorResponse<ChatRoom> page = CursorResponse.of(fetched, pageSize,
                room -> Cursor.of(room.getLastMessageAt(), room.getId()));
        if (page.content().isEmpty()) {
            // 빈 IN 절 쿼리를 보내지 않는다.
            return new CursorResponse<>(List.of(), null, false);
        }

        List<Long> roomIds = page.content().stream().map(ChatRoom::getId).toList();
        Map<Long, Long> unreadCounts = chatMessageRepository.countUnread(viewer.id(), roomIds).stream()
                .collect(Collectors.toMap(RoomUnreadCount::roomId, RoomUnreadCount::count));
        Map<Long, String> thumbnails = productThumbnails.of(
                page.content().stream().map(room -> room.getProduct().getId()).distinct().toList());

        return page.map(room -> ChatRoomSummaryResponse.of(room, viewer.id(),
                thumbnails.get(room.getProduct().getId()), unreadCounts.getOrDefault(room.getId(), 0L)));
    }

    public ChatRoomResponse findRoom(long roomId, AuthUser viewer) {
        ChatRoom room = chatRoomRepository.findWithDetails(roomId)
                .filter(found -> found.isActiveParticipant(viewer.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        return describe(room, viewer);
    }

    /**
     * 메시지 목록. 최신 메시지부터 내려주고, 커서는 마지막(가장 오래된) 메시지 id 다.
     * 조회만으로는 읽음 처리하지 않는다. GET 에 부수효과를 두지 않고, 읽음은 화면이 실제로 보일 때 따로 요청한다.
     */
    public CursorResponse<ChatMessageResponse> findMessages(long roomId, AuthUser viewer, String rawCursor, Integer size) {
        requireActiveRoom(roomId, viewer);
        Cursor cursor = Cursor.parse(rawCursor);
        PageSize pageSize = PageSize.of(size);
        Pageable limit = PageRequest.of(0, pageSize.fetchSize());

        List<ChatMessage> fetched = cursor == null
                ? chatMessageRepository.findLatest(roomId, limit)
                : chatMessageRepository.findBefore(roomId, cursor.valueAsLong(), limit);

        // 정렬 기준이 id 자체라 정렬값과 id 가 같다. 커서 형식을 API 전체에서 하나로 유지하기 위해 그대로 둔다.
        return CursorResponse.of(fetched, pageSize, message -> Cursor.of(message.getId(), message.getId()))
                .map(ChatMessageResponse::from);
    }

    /**
     * 메시지 전송. 방을 잠가 같은 방의 전송을 순서대로 처리한다.
     * 잠그지 않으면 거의 동시에 보낸 두 메시지 중 먼저 보낸 쪽이 나중에 커밋되며
     * 목록의 마지막 메시지가 실제 마지막 메시지와 달라질 수 있다.
     */
    @Transactional
    public ChatMessageResponse send(long roomId, AuthUser viewer, ChatMessageSendRequest request) {
        ChatRoom room = chatRoomRepository.findByIdForUpdate(roomId)
                .filter(found -> found.isActiveParticipant(viewer.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        return post(room, ChatMessage.text(room, userRepository.getReferenceById(viewer.id()), request.content()));
    }

    /**
     * 거래 상태 변경 같은 안내를 채팅방에 남긴다. 호출한 쪽의 트랜잭션에 참여하고, 방 잠금은 호출한 쪽이 잡는다.
     * 일반 메시지와 같은 경로라 목록 미리보기·상대 목록 복귀·실시간 전달이 그대로 적용된다.
     */
    @Transactional
    public ChatMessageResponse postSystemMessage(ChatRoom room, long actorId, String content) {
        return post(room, ChatMessage.system(room, userRepository.getReferenceById(actorId), content));
    }

    private ChatMessageResponse post(ChatRoom room, ChatMessage message) {
        chatMessageRepository.save(message);
        room.recordMessage(message);
        ChatMessageResponse response = ChatMessageResponse.from(message);
        eventPublisher.publishEvent(new ChatMessageSentEvent(participantIds(room), response));
        return response;
    }

    /**
     * 상대가 보낸 메시지를 모두 읽음 처리한다. 읽을 것이 없어도 성공이다(여러 번 호출해도 같다).
     * 실제로 바뀐 메시지가 있을 때만 알린다. 화면이 방에 들어올 때마다 부르므로 빈 이벤트를 쏟아내지 않는다.
     */
    @Transactional
    public void markAsRead(long roomId, AuthUser viewer) {
        ChatRoom room = requireActiveRoom(roomId, viewer);
        int updated = chatMessageRepository.markOpponentMessagesAsRead(roomId, viewer.id());
        if (updated > 0) {
            eventPublisher.publishEvent(new ChatMessagesReadEvent(participantIds(room), roomId, viewer.id()));
        }
    }

    /**
     * 나가기. 내 목록에서만 사라지고 상대는 대화를 계속 본다.
     * 나갈 때 안 읽은 메시지를 읽음 처리한다. 상대가 새 메시지를 보내 방이 다시 나타났을 때
     * 나가기 전의 메시지까지 안 읽은 수에 섞이지 않게 하기 위함이다.
     */
    @Transactional
    public void leave(long roomId, AuthUser viewer) {
        ChatRoom room = chatRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        room.leave(viewer.id());
        chatMessageRepository.markOpponentMessagesAsRead(roomId, viewer.id());
    }

    private ChatRoom requireActiveRoom(long roomId, AuthUser viewer) {
        return chatRoomRepository.findById(roomId)
                .filter(found -> found.isActiveParticipant(viewer.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    /** 연관 엔티티 id 는 프록시를 초기화하지 않고 꺼낼 수 있어 추가 조회가 없다. */
    private static List<Long> participantIds(ChatRoom room) {
        return List.of(room.getBuyer().getId(), room.getSeller().getId());
    }

    /** 채팅방 응답. 이 방의 현재 거래(취소·환불 제외 최신)와 누를 수 있는 거래 버튼을 함께 담는다. */
    public ChatRoomResponse describe(ChatRoom room, AuthUser viewer) {
        Long productId = room.getProduct().getId();
        Trade trade = tradeRepository.findFirstByChatRoomIdAndStatusNotInOrderByIdDesc(
                room.getId(), List.of(TradeStatus.CANCELED, TradeStatus.REFUNDED)).orElse(null);
        return ChatRoomResponse.of(room, viewer.id(), productThumbnails.of(List.of(productId)).get(productId), trade);
    }
}
