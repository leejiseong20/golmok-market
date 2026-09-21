package com.golmok.market.domain.report;

import com.golmok.market.domain.user.User;
import com.golmok.market.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 신고 기록.
 *
 * 쌓기만 하고 자동으로 조치하지 않는다. 신고 수로 상품을 자동으로 숨기면
 * 경쟁 판매자가 몰아서 신고하는 것으로 멀쩡한 상품을 내릴 수 있다. 판단은 사람이 해야 한다.
 * (관리자 화면은 아직 없다. 이 표를 직접 조회해서 본다.)
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

    private Report(User reporter, ReportTarget targetType, long targetId, ReportReason reason, String detail) {
        this.reporter = reporter;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
    }

    public static Report of(User reporter, ReportTarget targetType, long targetId, ReportReason reason, String detail) {
        return new Report(reporter, targetType, targetId, reason, detail);
    }
}
