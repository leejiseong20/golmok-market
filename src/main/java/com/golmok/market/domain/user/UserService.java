package com.golmok.market.domain.user;

import com.golmok.market.domain.image.ImageUrlValidator;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.trade.ReviewRepository;
import com.golmok.market.domain.user.dto.MyProfileResponse;
import com.golmok.market.domain.user.dto.ProfileUpdateRequest;
import com.golmok.market.domain.user.dto.UserProfileResponse;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserRegionService userRegionService;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final ImageUrlValidator imageUrlValidator;

    /** 탈퇴한 회원의 공개 프로필은 없는 사용자로 본다(익명화된 행만 남아 있다). */
    public UserProfileResponse findProfile(long id) {
        User user = userRepository.findById(id)
                .filter(found -> !found.isWithdrawn())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new UserProfileResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getMannerTemp(),
                productRepository.countBySellerIdAndDeletedAtIsNull(id), reviewRepository.countByRevieweeId(id));
    }

    /**
     * 토큰은 유효하지만 사용자가 없는 경우(탈퇴 후 물리 삭제 등)도 404 로 처리한다.
     * access token 은 서명만 검증하고 DB 를 보지 않으므로 이 상황이 생길 수 있다.
     * 탈퇴 직후 남은 access token(최대 30분)으로 요청해도 같은 404 다.
     */
    public MyProfileResponse findMe(AuthUser viewer) {
        return userRepository.findById(viewer.id())
                .filter(user -> !user.isWithdrawn())
                .map(user -> MyProfileResponse.from(user, userRegionService.findMyRegions(user.getId())))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 닉네임·프로필 사진 수정. 응답은 갱신된 내 정보라 화면이 다시 조회하지 않아도 된다.
     *
     * 닉네임 중복은 먼저 확인하지만, 두 사람이 거의 동시에 같은 닉네임으로 바꾸면 둘 다 확인을 통과할 수 있다.
     * 최종 방어선은 uk_users_nickname UNIQUE 이고, 위반을 여기서 잡으려고 flush 한다(커밋 때 터지면 409 로 바꿀 기회가 없다).
     * 바뀌는 UNIQUE 컬럼이 닉네임 하나뿐이라 위반은 곧 닉네임 중복이다(가입과 달리 이메일과 구분할 필요가 없다).
     *
     * 이전 사진 파일은 지우지 않는다. 상품 사진과 같은 정책이다(롤백 가능성, 고아 파일 정리는 후속 배치).
     */
    @Transactional
    public MyProfileResponse updateProfile(AuthUser viewer, ProfileUpdateRequest request) {
        User user = userRepository.findById(viewer.id())
                // 탈퇴 후 남은 토큰으로 익명화된 닉네임·사진을 되돌리지 못하게 한다.
                .filter(found -> !found.isWithdrawn())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (request.profileImageUrl() != null) {
            imageUrlValidator.validate(List.of(request.profileImageUrl()));
        }
        boolean nicknameChanged = !request.nickname().equals(user.getNickname());
        if (nicknameChanged && userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        if (!nicknameChanged && Objects.equals(request.profileImageUrl(), user.getProfileImageUrl())) {
            return MyProfileResponse.from(user, userRegionService.findMyRegions(user.getId()));
        }
        user.updateProfile(request.nickname(), request.profileImageUrl());
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        return MyProfileResponse.from(user, userRegionService.findMyRegions(user.getId()));
    }
}
