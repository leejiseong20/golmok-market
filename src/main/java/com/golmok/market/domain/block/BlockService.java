package com.golmok.market.domain.block;

import com.golmok.market.domain.block.dto.BlockedUserResponse;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.pagination.Cursor;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.pagination.PageSize;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사용자 차단 · 해제 · 차단 목록.
 *
 * 차단하면 네 가지가 달라진다.
 *   1. 홈·검색 목록에서 그 사람 상품이 빠진다(내 화면에서만. 상대는 내 상품을 계속 본다)
 *   2. 채팅방 열기와 메시지 전송이 막힌다(양방향)
 *   3. 채팅 목록에서 그 방이 숨는다(양방향). 방과 대화는 지우지 않는다 — 신고의 근거가 될 수 있다
 *   4. 그 사람이 일으킨 알림이 오지 않는다(양방향)
 *
 * 상품 상세에 주소로 바로 들어오면 상품은 보인다. 채팅만 막는다.
 * 404 로 감추면 "왜 안 보이지"가 되고, 차단은 숨김이 아니라 관계를 끊는 일이기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockService {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;

    @Transactional
    public void block(long targetId, AuthUser viewer) {
        if (targetId == viewer.id()) {
            throw new BusinessException(ErrorCode.CANNOT_BLOCK_SELF);
        }
        // 탈퇴한 회원은 공개 프로필과 같은 기준으로 "없는 사용자"다.
        User target = userRepository.findById(targetId)
                .filter(found -> !found.isWithdrawn())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (blockRepository.existsByBlockerIdAndBlockedId(viewer.id(), targetId)) {
            throw new BusinessException(ErrorCode.ALREADY_BLOCKED);
        }
        try {
            // 확인과 INSERT 사이에 같은 요청이 한 번 더 들어올 수 있다. UNIQUE 가 최종 방어선이다.
            blockRepository.saveAndFlush(Block.of(userRepository.getReferenceById(viewer.id()), target));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_BLOCKED);
        }
    }

    /**
     * 차단하지 않은 사람을 해제해도 에러 없이 넘어간다(멱등).
     * 화면 상태가 서버와 어긋났을 때 에러를 보는 것보다 한 번 더 눌러 맞춰지는 편이 낫다(찜 해제와 같은 기준).
     */
    @Transactional
    public void unblock(long targetId, AuthUser viewer) {
        blockRepository.findByBlockerIdAndBlockedId(viewer.id(), targetId)
                .ifPresent(blockRepository::delete);
    }

    /** 내가 차단한 사람 목록. 최근에 차단한 순. */
    public CursorResponse<BlockedUserResponse> findMyBlocks(AuthUser viewer, String rawCursor, Integer size) {
        Cursor cursor = Cursor.parse(rawCursor);
        PageSize pageSize = PageSize.of(size);
        Pageable limit = PageRequest.of(0, pageSize.fetchSize());

        List<Block> fetched = cursor == null
                ? blockRepository.findFirstPage(viewer.id(), limit)
                : blockRepository.findNextPage(viewer.id(), cursor.valueAsDateTime(), cursor.id(), limit);

        CursorResponse<Block> page = CursorResponse.of(fetched, pageSize,
                block -> Cursor.of(block.getCreatedAt(), block.getId()));
        return page.map(BlockedUserResponse::from);
    }
}
