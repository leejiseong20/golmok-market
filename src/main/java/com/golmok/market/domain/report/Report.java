package com.golmok.market.domain.report;

import com.golmok.market.domain.user.User;
import com.golmok.market.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 신고 기록.
 *
 * 쌓기만 하고 자동으로 조치하지 않는다. 신고 수로 상품을 자동으로 숨기면
 * 경쟁 판매자가 몰아서 신고하는 것으로 멀쩡한 상품을 내릴 수 있다. 판단은 사람이 해야 한다.
 * 관리자가 처리하면 상태·처리자·근거를 여기에 남긴다(2026-09-23). 조치 자체는 admin_actions 에도 남는다.
 */
@Entity
@Getter
// 같은 사람이 같은 대상을 여러 번 신고하지 못하게 DB 가 막는다. 엔티티에도 선언해야 H2 테스트에도 제약이 선다.
@Table(name = "reports", uniqueConstraints =
        @UniqueConstraint(name = "uk_report", columnNames = {"reporter_id", "target_type", "target_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTarget targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    /** 신고자가 적은 설명. 사유가 OTHER 면 필수다(서비스에서 확인한다). */
    @Column(length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    /** 처리한 관리자. 누가 판단했는지 남아야 나중에 설명할 수 있다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private User handledBy;

    /** 관리자가 적은 판단 근거. */
    @Column(name = "admin_memo", length = 500)
    private String adminMemo;

    private Report(User reporter, ReportTarget targetType, long targetId, ReportReason reason, String detail) {
        this.reporter = reporter;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
        this.status = ReportStatus.PENDING;
    }

    public static Report of(User reporter, ReportTarget targetType, long targetId, ReportReason reason, String detail) {
        return new Report(reporter, targetType, targetId, reason, detail);
    }

    /**
     * 관리자가 처리했다. 이미 처리된 신고는 다시 바꾸지 않는다 —
     * 같은 대상의 신고를 한꺼번에 닫을 때 먼저 닫힌 건을 덮어쓰지 않기 위해서다.
     */
    public void handle(ReportStatus status, User admin, String memo, LocalDateTime now) {
        if (this.status != ReportStatus.PENDING) {
            return;
        }
        this.status = status;
        this.handledBy = admin;
        this.adminMemo = memo;
        this.handledAt = now;
    }

    public boolean isPending() {
        return this.status == ReportStatus.PENDING;
    }
}
