package com.golmok.market.domain.block;

import com.golmok.market.domain.block.dto.BlockedUserResponse;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.security.AuthUser;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 차단. 모두 로그인이 필요하다(SecurityConfig 의 기본 차단 규칙).
 *
 * 경로가 /api/users 아래인 이유: 차단은 "그 사용자에 대한 내 설정"이다.
 * GET /api/users/me/blocks 는 두 단계라 공개 GET 패턴(/api/users/*)에 걸리지 않는다.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @PostMapping("/{id}/block")
    @ResponseStatus(HttpStatus.CREATED)
    public void block(@PathVariable @Positive(message = "사용자 ID는 양수여야 합니다.") long id,
                      @AuthenticationPrincipal AuthUser viewer) {
        blockService.block(id, viewer);
    }

    @DeleteMapping("/{id}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable @Positive(message = "사용자 ID는 양수여야 합니다.") long id,
                        @AuthenticationPrincipal AuthUser viewer) {
        blockService.unblock(id, viewer);
    }

    @GetMapping("/me/blocks")
    public CursorResponse<BlockedUserResponse> findMyBlocks(@AuthenticationPrincipal AuthUser viewer,
                                                            @RequestParam(required = false) String cursor,
                                                            @RequestParam(required = false) Integer size) {
        return blockService.findMyBlocks(viewer, cursor, size);
    }
}
