package com.golmok.market.domain.user;

import com.golmok.market.domain.user.dto.MyProfileResponse;
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

    /**
     * 토큰은 유효하지만 사용자가 없는 경우(탈퇴 후 물리 삭제 등)도 404 로 처리한다.
     * access token 은 서명만 검증하고 DB 를 보지 않으므로 이 상황이 생길 수 있다.
     */
    public MyProfileResponse findMe(AuthUser viewer) {
        return userRepository.findById(viewer.id())
                .map(MyProfileResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
