package com.golmok.market.domain.user;

import com.golmok.market.domain.chat.ChatRoomRepository;
import com.golmok.market.domain.notification.NotificationRepository;
import com.golmok.market.domain.product.FavoriteRepository;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.trade.TradeRepository;
import com.golmok.market.domain.trade.TradeStatus;
import com.golmok.market.domain.block.BlockRepository;
import com.golmok.market.domain.push.PushSubscriptionRepository;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 회원 탈퇴. 모든 처리를 한 트랜잭션으로 묶는다. 중간에 실패하면 전부 되돌려
 * "상품은 지워졌는데 계정은 남음" 같은 반쪽 상태를 만들지 않는다.
 *
 * <pre>
 * 막는 경우   비밀번호 불일치 · 진행 중 거래(예약·결제·배송)가 있음
 * 지우는 것   판매 상품(soft delete) · 내가 한 찜(찜 수도 줄임) · 알림 · 인증 동네 · refresh token
 * 표시만     모든 채팅방에서 나감(대화는 남는다)
 * 남기는 것   거래·후기·채팅 메시지(상대의 기록이다). 회원 행은 익명화해서 남긴다(User.withdraw)
 * </pre>
 *
 * 잠금 순서: 판매 상품 → 회원. 예약·구매확정·후기 작성이 상품을 먼저 잠그는 것과 같은 방향이라 서로 교착되지 않는다.
 * 판매 상품을 잠근 뒤 진행 중 거래를 확인하므로, 확인과 상품 삭제 사이에 내 상품에 새 예약이 끼어들지 못한다.
 * 내가 "구매자"인 방에서 판매자가 동시에 예약하는 경우까지는 막지 않는다(드문 경쟁). 그렇게 생긴 예약은
 * 판매자가 취소할 수 있고, 이후 탈퇴한 상대와의 예약·대화는 거부된다.
 *
 * 이미 발급된 access token 은 만료(최대 30분)까지 유효하다. 로그아웃과 같은 정책이다(JWT 를 요청마다 DB 로 확인하지 않는다).
 */
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final FavoriteRepository favoriteRepository;
    private final TradeRepository tradeRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final NotificationRepository notificationRepository;
    private final BlockRepository blockRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserRegionRepository userRegionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public void withdraw(AuthUser viewer, String password) {
        productRepository.findAllOnSaleBySellerForUpdate(viewer.id());
        User user = userRepository.findByIdForUpdate(viewer.id())
                // 이미 탈퇴했으면(남은 access token 으로 다시 요청) 없는 사용자로 본다.
                .filter(found -> !found.isWithdrawn())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        if (tradeRepository.existsByParticipantAndStatusIn(user.getId(), TradeStatus.activeStatuses())) {
            throw new BusinessException(ErrorCode.TRADE_IN_PROGRESS);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        productRepository.softDeleteAllBySeller(user.getId(), now);
        // 찜 행을 지우기 전에 찜 수부터 줄인다(지운 뒤에는 어느 상품을 찜했는지 알 수 없다).
        productRepository.decrementFavoriteCountsLikedBy(user.getId());
        favoriteRepository.deleteAllByUserId(user.getId());
        chatRoomRepository.leaveAllAsBuyer(user.getId());
        chatRoomRepository.leaveAllAsSeller(user.getId());
        notificationRepository.deleteAllByUserId(user.getId());
        // 차단 관계는 남겨도 쓸 곳이 없다. 익명화된 행만 가리키고, 그 사람과 다시 마주칠 일도 없다.
        blockRepository.deleteAllRelatedTo(user.getId());
        // 탈퇴한 사람의 기기로 알림이 계속 가면 안 된다.
        pushSubscriptionRepository.deleteAllByUserId(user.getId());
        userRegionRepository.deleteAllByUserId(user.getId());
        refreshTokenRepository.deleteAllByUserId(user.getId());
        user.withdraw(now);
    }
}
