package com.golmok.market.domain.user;

import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionService;
import com.golmok.market.domain.user.dto.MyRegionResponse;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 동네 인증. 좌표에서 가장 가까운 동네를 내 동네로 등록한다.
 *
 * 정책
 * - 1인 최대 2개. 스키마가 아니라 여기서 막는다(DB 로는 "행 개수 제한"을 표현할 수 없다).
 * - 이미 인증한 동네를 다시 인증하면 새 행이 아니라 verifyCount 가 올라간다.
 *   (user_id, region_id) UNIQUE 가 중복 행을 최종적으로 막는다.
 * - 첫 동네는 자동으로 대표가 된다. 대표 없는 상태를 허용하면 화면이 매번 그 경우를 처리해야 한다.
 * - 대표를 삭제하면 남은 동네가 대표로 승격된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserRegionService {

    private static final int MAX_REGIONS = 2;

    private final UserRegionRepository userRegionRepository;
    private final UserRepository userRepository;
    private final RegionService regionService;

    public List<MyRegionResponse> findMyRegions(long userId) {
        return userRegionRepository.findAllWithRegion(userId).stream()
                .map(MyRegionResponse::from)
                .toList();
    }

    /** @return 갱신된 내 동네 전체. 화면이 한 번의 호출로 목록을 새로 그릴 수 있게 한다. */
    @Transactional
    public List<MyRegionResponse> verify(AuthUser viewer, double lat, double lng) {
        Region nearest = regionService.findNearest(lat, lng);

        var alreadyVerified = userRegionRepository.findByUserIdAndRegionId(viewer.id(), nearest.getId());
        if (alreadyVerified.isPresent()) {
            alreadyVerified.get().reVerify();
            return findMyRegions(viewer.id());
        }

        long count = userRegionRepository.countByUserId(viewer.id());
        if (count >= MAX_REGIONS) {
            throw new BusinessException(ErrorCode.REGION_LIMIT_EXCEEDED);
        }
        try {
            userRegionRepository.saveAndFlush(UserRegion.verify(
                    userRepository.getReferenceById(viewer.id()), nearest, count == 0));
        } catch (DataIntegrityViolationException e) {
            // 같은 동네를 동시에 두 번 인증한 경우. 이미 등록된 것이므로 에러 대신 현재 상태를 돌려준다.
            return findMyRegions(viewer.id());
        }
        return findMyRegions(viewer.id());
    }

    @Transactional
    public List<MyRegionResponse> delete(AuthUser viewer, long regionId) {
        UserRegion target = userRegionRepository.findByUserIdAndRegionId(viewer.id(), regionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_REGION_NOT_FOUND));
        boolean wasPrimary = target.isPrimary();
        userRegionRepository.delete(target);
        userRegionRepository.flush();

        if (wasPrimary) {
            // 목록은 대표 → 최근 인증 순이므로, 대표가 사라진 지금은 가장 최근에 인증한 동네가 맨 앞이다.
            userRegionRepository.findAllWithRegion(viewer.id()).stream()
                    .findFirst()
                    .ifPresent(remaining -> remaining.markPrimary(true));
        }
        return findMyRegions(viewer.id());
    }

    @Transactional
    public List<MyRegionResponse> markPrimary(AuthUser viewer, long regionId) {
        List<UserRegion> myRegions = userRegionRepository.findAllWithRegion(viewer.id());
        UserRegion target = myRegions.stream()
                .filter(userRegion -> userRegion.getRegion().getId().equals(regionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_REGION_NOT_FOUND));

        myRegions.forEach(userRegion -> userRegion.markPrimary(userRegion == target));
        return findMyRegions(viewer.id());
    }
}
