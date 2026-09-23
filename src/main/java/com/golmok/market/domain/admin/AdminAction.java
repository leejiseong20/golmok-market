package com.golmok.market.domain.admin;

import com.golmok.market.domain.user.User;
import com.golmok.market.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자 조치 기록(감사 로그).
 *
 * 사용자 정지·상품 삭제는 되돌리기 어렵고 남의 물건과 계정에 손대는 일이다.
 * "누가 언제 무엇을 왜 했는지"가 남아야 나중에 설명할 수 있어서, 조치와 같은 트랜잭션으로 남긴다.
 * 근거(reason)는 비울 수 없다 — 기록만 남고 이유를 모르면 감사 로그의 뜻이 없다.
 *
 * 대상에는 외래키를 걸지 않는다. 한 컬럼이 users·products·reports 를 동시에 가리킬 수 없어서다(reports 와 같은 선택).
 */
@Entity
@Getter
@Table(name = "admin_actions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAction extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id")
    private User admin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdminActionType action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private AdminActionTarget targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** 신고를 처리하며 한 조치면 그 신고 id. 관리자가 직접 조치했으면 비어 있다. */
    @Column(name = "report_id")
    private Long reportId;

    @Column(nullable = false, length = 500)
    private String reason;

    private AdminAction(User admin, AdminActionType action, AdminActionTarget targetType, long targetId,
                        Long reportId, String reason) {
        this.admin = admin;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reportId = reportId;
        this.reason = reason;
    }

    public static AdminAction of(User admin, AdminActionType action, AdminActionTarget targetType, long targetId,
                                 Long reportId, String reason) {
        return new AdminAction(admin, action, targetType, targetId, reportId, reason);
    }
}
