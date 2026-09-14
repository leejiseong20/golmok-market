package com.golmok.market.domain.chat;

import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 상품 1건에 대한 판매자-구매자 1:1 채팅방.
 * (product_id, buyer_id) UNIQUE 로 중복 생성을 DB에서 막는다.
 */
@Entity
@Getter
@Table(name = "chat_rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

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
    @Column(name = "last_message", length = 200)
    private String lastMessage;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "buyer_left", nullable = false)
    private boolean buyerLeft;

    @Column(name = "seller_left", nullable = false)
    private boolean sellerLeft;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    private ChatRoom(Product product, User buyer) {
        this.product = product;
        this.buyer = buyer;
        this.seller = product.getSeller();
    }

    public static ChatRoom open(Product product, User buyer) {
        if (product.isOwnedBy(buyer.getId())) {
            throw new IllegalArgumentException("자신의 상품에는 채팅을 걸 수 없습니다.");
        }
        return new ChatRoom(product, buyer);
    }

    public void updateLastMessage(String content, LocalDateTime sentAt) {
        this.lastMessage = content.length() > 200 ? content.substring(0, 200) : content;
        this.lastMessageAt = sentAt;
    }

    public void leave(Long userId) {
        if (this.buyer.getId().equals(userId)) {
            this.buyerLeft = true;
        } else if (this.seller.getId().equals(userId)) {
            this.sellerLeft = true;
        } else {
            throw new IllegalArgumentException("이 채팅방의 참여자가 아닙니다.");
        }
    }

    public boolean isParticipant(Long userId) {
        return this.buyer.getId().equals(userId) || this.seller.getId().equals(userId);
    }

    /** 메시지를 받을 상대방 id. 알림 발송 대상. */
    public Long getOpponentId(Long userId) {
        return this.buyer.getId().equals(userId) ? this.seller.getId() : this.buyer.getId();
    }
}
