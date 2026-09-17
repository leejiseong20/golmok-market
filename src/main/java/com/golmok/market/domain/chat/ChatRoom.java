package com.golmok.market.domain.chat;

import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.user.User;
import com.golmok.market.global.entity.BaseCreatedTimeEntity;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 상품 1건에 대한 판매자-구매자 1:1 채팅방.
 * (product_id, buyer_id) UNIQUE 로 중복 생성을 DB에서 막는다.
 *
 * 나가기는 행을 지우지 않고 buyer_left / seller_left 만 켠다.
 * 상대는 대화를 계속 볼 수 있어야 하고, 거래(trades.chat_room_id)가 이 방을 참조할 수 있기 때문이다.
 */
@Entity
@Getter
// 같은 상품에 같은 구매자는 방 1개. 동시 요청으로 방이 두 개 생기는 것을 DB 가 막는다.
@Table(name = "chat_rooms", uniqueConstraints =
        @UniqueConstraint(name = "uk_chat_room", columnNames = {"product_id", "buyer_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseCreatedTimeEntity {

    private static final int LAST_MESSAGE_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id")
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id")
    private User seller;

    /** 채팅 목록 화면에서 메시지 테이블을 뒤지지 않으려고 캐싱해둔다. */
    @Column(name = "last_message", length = LAST_MESSAGE_LENGTH)
    private String lastMessage;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "buyer_left", nullable = false)
    private boolean buyerLeft;

    @Column(name = "seller_left", nullable = false)
    private boolean sellerLeft;

    private ChatRoom(Product product, User buyer) {
        this.product = product;
        this.buyer = buyer;
        this.seller = product.getSeller();
    }

    public static ChatRoom open(Product product, User buyer) {
        if (product.isOwnedBy(buyer.getId())) {
            throw new BusinessException(ErrorCode.CANNOT_CHAT_OWN_PRODUCT);
        }
        return new ChatRoom(product, buyer);
    }

    // ---------- 참여자 ----------

    public boolean isParticipant(Long userId) {
        return this.buyer.getId().equals(userId) || this.seller.getId().equals(userId);
    }

    /** 참여자이면서 나가지 않은 사람. 채팅방의 모든 기능은 이 조건을 통과해야 쓸 수 있다. */
    public boolean isActiveParticipant(Long userId) {
        if (this.buyer.getId().equals(userId)) {
            return !this.buyerLeft;
        }
        return this.seller.getId().equals(userId) && !this.sellerLeft;
    }

    public boolean isBuyer(Long userId) {
        return this.buyer.getId().equals(userId);
    }

    public User getOpponent(Long userId) {
        return isBuyer(userId) ? this.seller : this.buyer;
    }

    /** 메시지를 받을 상대방 id. 알림 발송 대상. */
    public Long getOpponentId(Long userId) {
        return getOpponent(userId).getId();
    }

    public boolean hasOpponentLeft(Long userId) {
        return isBuyer(userId) ? this.sellerLeft : this.buyerLeft;
    }

    // ---------- 나가기 · 다시 들어오기 ----------

    public void leave(Long userId) {
        if (!isActiveParticipant(userId)) {
            // 남의 방이거나 이미 나간 방. 어느 쪽인지 알려주지 않는다.
            throw new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }
        if (isBuyer(userId)) {
            this.buyerLeft = true;
        } else {
            this.sellerLeft = true;
        }
    }

    /**
     * 구매자가 상품 상세에서 "채팅하기"를 다시 누른 경우.
     * 판매자는 자기 상품에 채팅을 걸 수 없으므로 이 경로가 없고, 구매자가 메시지를 보내면 돌아온다.
     */
    public void rejoinAsBuyer() {
        this.buyerLeft = false;
    }

    // ---------- 메시지 ----------

    /**
     * 새 메시지를 목록용 캐시에 반영한다.
     * 나간 상대에게도 새 메시지는 전달돼야 하므로 상대의 방을 다시 보이게 한다(당근마켓과 같은 동작).
     */
    public void recordMessage(ChatMessage message) {
        String content = message.getContent();
        this.lastMessage = truncate(content);
        this.lastMessageAt = message.getCreatedAt();
        if (isBuyer(message.getSender().getId())) {
            this.sellerLeft = false;
        } else {
            this.buyerLeft = false;
        }
    }

    /** 미리보기용으로 자른다. 이모지(서로게이트 쌍) 한가운데서 잘려 깨진 글자가 되지 않게 한다. */
    private static String truncate(String content) {
        if (content.length() <= LAST_MESSAGE_LENGTH) {
            return content;
        }
        int end = Character.isHighSurrogate(content.charAt(LAST_MESSAGE_LENGTH - 1))
                ? LAST_MESSAGE_LENGTH - 1
                : LAST_MESSAGE_LENGTH;
        return content.substring(0, end);
    }
}
