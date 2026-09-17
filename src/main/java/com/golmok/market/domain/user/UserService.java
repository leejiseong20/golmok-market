package com.golmok.market.domain.user;

import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.trade.ReviewRepository;
import com.golmok.market.domain.user.dto.MyProfileResponse;
import com.golmok.market.domain.user.dto.UserProfileResponse;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserRegionService userRegionService;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;

    public UserProfileResponse findProfile(long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new UserProfileResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getMannerTemp(),
                productRepository.countBySellerIdAndDeletedAtIsNull(id), reviewRepository.countByRevieweeId(id));
    }

    /**
     * 토큰은 유효하지만 사용자가 없는 경우(탈퇴 후 물리 삭제 등)도 404 로 처리한다.
     * access token 은 서명만 검증하고 DB 를 보지 않으므로 이 상황이 생길 수 있다.
     */
    public MyProfileResponse findMe(AuthUser viewer) {
        return userRepository.findById(viewer.id())
                .map(user -> MyProfileResponse.from(user, userRegionService.findMyRegions(user.getId())))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
