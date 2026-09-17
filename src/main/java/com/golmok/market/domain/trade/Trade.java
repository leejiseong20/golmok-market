package com.golmok.market.domain.trade;

import com.golmok.market.domain.chat.ChatRoom;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductStatus;
import com.golmok.market.domain.user.User;
import com.golmok.market.global.entity.BaseTimeEntity;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 거래 = 영수증. 상품의 status 는 화면 표시용 팻말이고,
 * 돈의 흐름은 전부 여기서 관리한다.
 *
 * active_product_id 는 DB 생성컬럼이다.
 * status 가 CANCELED/REFUNDED 면 NULL 이 되어 UNIQUE 제약에서 빠지고,
 * 그 덕분에 "진행 중 거래는 상품당 1건" 이 DB 레벨에서 보장된다.
 */
@Entity
@Getter
// active_product_id 의 UNIQUE 가 "진행 중 거래는 상품당 1건"을 보장한다.
// 취소·환불이면 생성컬럼이 NULL 이 되어 제약에서 빠진다(NULL 은 중복으로 보지 않는다).
@Table(name = "trades", uniqueConstraints =
        @UniqueConstraint(name = "uk_trades_active_product", columnNames = "active_product_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trade extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id")
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id")
    private User buyer;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TradeStatus status;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** DB가 계산하는 생성컬럼이므로 JPA는 읽기만 한다. */
    @Column(name = "active_product_id", insertable = false, updatable = false)
    private Long activeProductId;

    private Trade(Product product, ChatRoom chatRoom, User buyer) {
        this.product = product;
        this.chatRoom = chatRoom;
        this.seller = product.getSeller();
        this.buyer = buyer;
        this.amount = product.getPrice();
        this.status = TradeStatus.REQUESTED;
    }

    public static Trade request(Product product, ChatRoom chatRoom, User buyer) {
        if (product.isOwnedBy(buyer.getId())) {
            throw new BusinessException(ErrorCode.CANNOT_BUY_OWN_PRODUCT);
        }
        product.reserve();
        return new Trade(product, chatRoom, buyer);
    }

    // ---------- 상태 전이 ----------

    private void transitionTo(TradeStatus next) {
        if (!this.status.canTransitionTo(next)) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "거래 상태를 %s 에서 %s 로 변경할 수 없습니다.".formatted(this.status, next));
        }
        this.status = next;
    }

    public void markPaid() {
        transitionTo(TradeStatus.PAID);
    }

    public void markShipping() {
        transitionTo(TradeStatus.SHIPPING);
    }

    /** 구매자의 구매확정(결제 거래). 직거래 완료는 {@link #completeInPerson()}. */
    public void confirm() {
        if (!this.status.isBuyerConfirmable()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "결제가 끝난 거래만 구매확정할 수 있습니다.");
        }
        transitionTo(TradeStatus.CONFIRMED);
        this.completedAt = LocalDateTime.now();
        // 판매자가 먼저 판매완료로 표시했어도 구매자의 수령 확인은 별개다.
        if (this.product.getStatus() != ProductStatus.SOLD) {
            this.product.markSold();
        }
    }

    /**
     * 직거래 완료. 판매자가 채팅방에서 누른다(권한 검사는 서비스).
     * 결제가 없어 서버가 대금을 맡아두지 않으므로, 판매자가 완료를 표시해도 구매자가 손해 볼 것이 없다.
     */
    public void completeInPerson() {
        if (this.status != TradeStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "예약중인 거래만 거래완료할 수 있습니다.");
        }
        transitionTo(TradeStatus.CONFIRMED);
        this.completedAt = LocalDateTime.now();
        this.product.markSold();
    }

    public void cancel(String reason) {
        transitionTo(TradeStatus.CANCELED);
        this.cancelReason = reason;
        this.product.reopen();
    }

    public void refund(String reason) {
        transitionTo(TradeStatus.REFUNDED);
        this.cancelReason = reason;
        this.product.reopen();
    }

    public boolean isParticipant(Long userId) {
        return this.seller.getId().equals(userId) || this.buyer.getId().equals(userId);
    }
}
