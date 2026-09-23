package com.golmok.market.domain.admin.dto;

import com.golmok.market.domain.admin.AdminAction;
import com.golmok.market.domain.admin.AdminActionType;

import java.time.LocalDateTime;

/** 이 대상에 있었던 관리자 조치 한 건. */
public record AdminActionResponse(Long id, AdminActionType action, String adminNickname,
                                  String reason, LocalDateTime createdAt) {

    public static AdminActionResponse from(AdminAction action) {
        return new AdminActionResponse(action.getId(), action.getAction(),
                action.getAdmin().getNickname(), action.getReason(), action.getCreatedAt());
    }
}
