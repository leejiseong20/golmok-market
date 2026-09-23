package com.golmok.market.domain.admin.dto;

import com.golmok.market.domain.admin.AdminAction;
import com.golmok.market.domain.admin.AdminActionTarget;
import com.golmok.market.domain.admin.AdminActionType;

import java.time.LocalDateTime;

/**
 * 조치 기록 목록 한 줄. 대상 이름을 함께 준다(id 만으로는 무엇에 한 조치인지 알 수 없다).
 *
 * @param reportId 신고를 처리하며 한 조치면 그 신고 id, 직접 한 조치면 null
 */
public record AdminActionLogResponse(Long id, AdminActionType action, String adminNickname,
                                     AdminActionTarget targetType, Long targetId, String targetName,
                                     Long reportId, String reason, LocalDateTime createdAt) {

    public static AdminActionLogResponse of(AdminAction action, String targetName) {
        return new AdminActionLogResponse(action.getId(), action.getAction(), action.getAdmin().getNickname(),
                action.getTargetType(), action.getTargetId(), targetName, action.getReportId(),
                action.getReason(), action.getCreatedAt());
    }
}
