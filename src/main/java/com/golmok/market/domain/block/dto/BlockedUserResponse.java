package com.golmok.market.domain.block.dto;

import com.golmok.market.domain.block.Block;
import com.golmok.market.domain.user.User;

import java.time.LocalDateTime;

/** 차단 목록 한 줄. 해제하려면 누구인지 알아볼 수 있어야 하므로 닉네임과 사진을 함께 준다. */
public record BlockedUserResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        LocalDateTime blockedAt
) {
    public static BlockedUserResponse from(Block block) {
        User blocked = block.getBlocked();
        return new BlockedUserResponse(blocked.getId(), blocked.getNickname(),
                blocked.getProfileImageUrl(), block.getCreatedAt());
    }
}
