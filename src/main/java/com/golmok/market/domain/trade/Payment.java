package com.golmok.market.domain.trade;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 결제 시도 이력. 하나의 거래에 여러 건이 쌓일 수 있다(실패 후 재시도).
 *
 * orderId 는 우리가 만드는 주문번호이자 멱등성 키다.
 * 토스 웹훅이 같은 결제를 두 번 보내도 UNIQUE 제약에 걸려
 * 중복 처리되지 않는다.
 */
@Entity
@Getter
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trade_id")
    private Trade trade;

    @Column(name = "order_id", nullable = false, length = 64, unique = true)
    private String orderId;

    @Column(name = "payment_key", length = 200, unique = true)
    private String paymentKey;

    @Column(nullable = false)
    private int amount;

    @Column(length = 30)
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentStatus status;

    @Column(name = "fail_code", length = 50)
    private String failCode;

    @Column(name = "fail_reason", length = 255)
    private String failReason;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "json")
    private String rawResponse;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    private Payment(Trade trade, String orderId, int amount) {
        this.trade = trade;
        this.orderId = orderId;
        this.amount = amount;
        this.status = PaymentStatus.READY;
    }

    public static Payment ready(Trade trade, String orderId) {
        return new Payment(trade, orderId, trade.getAmount());
    }

    /**
     * 토스 승인 응답을 반영한다.
     * 금액 검증은 반드시 여기서 한다 - 클라이언트가 보낸 금액을 믿으면 안 된다.
     */
    public void approve(String paymentKey, String method, int approvedAmount, String rawResponse) {
        if (this.amount != approvedAmount) {
            throw new IllegalStateException("결제 금액이 주문 금액과 일치하지 않습니다.");
        }
        this.paymentKey = paymentKey;
        this.method = method;
        this.status = PaymentStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
        this.rawResponse = rawResponse;
    }

    public void fail(String failCode, String failReason) {
        this.status = PaymentStatus.FAILED;
        this.failCode = failCode;
        this.failReason = failReason;
    }

    public void cancel(String rawResponse) {
        if (this.status != PaymentStatus.APPROVED) {
            throw new IllegalStateException("승인된 결제만 취소할 수 있습니다.");
        }
        this.status = PaymentStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
        this.rawResponse = rawResponse;
    }

    public boolean isApproved() {
        return this.status == PaymentStatus.APPROVED;
    }
}
