package com.golmok.market.domain.region;

import com.golmok.market.domain.region.dto.RegionResponse;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionService {

    private static final int NEARBY_LIMIT = 10;
    private static final double INITIAL_RADIUS_KM = 5;
    private static final double RADIUS_MULTIPLIER = 4;

    private final RegionRepository regionRepository;

    public List<RegionResponse> search(String keyword) {
        if (keyword == null || keyword.isBlank() || keyword.trim().length() > 82) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String escaped = keyword.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return regionRepository.searchByFullName("%" + escaped + "%").stream()
                .map(RegionResponse::from)
                .toList();
    }

    public List<RegionResponse> findNearby(double lat, double lng) {
        return nearest(lat, lng, NEARBY_LIMIT).stream()
                .map(candidate -> RegionResponse.from(candidate.region()))
                .toList();
    }

    /**
     * 동네 인증용. 좌표에서 가장 가까운 동네 하나를 준다.
     *
     * 반경 제한을 두지 않는다. 실제 서비스라면 "그 동네 근처에 있을 때만 인증"이 맞지만,
     * 지금 regions 에는 동네가 3개뿐이라 반경을 걸면 대부분의 좌표가 인증에 실패한다.
     * 동네 데이터를 실제로 채울 때 반경 제한을 도입한다.
     */
    public Region findNearest(double lat, double lng) {
        return nearest(lat, lng, 1).stream()
                .findFirst()
                .map(DistanceToRegion::region)
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_FOUND));
    }

    private List<DistanceToRegion> nearest(double lat, double lng, int limit) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        double radius = INITIAL_RADIUS_KM;
        while (true) {
            boolean wholeWorld = radius >= GeoDistance.MAX_DISTANCE_KM;
            List<Region> candidates = wholeWorld ? regionRepository.findAll() : findCandidates(lat, lng, radius);
            double currentRadius = radius;
            List<DistanceToRegion> nearest = candidates.stream()
                    .map(region -> new DistanceToRegion(region,
                            GeoDistance.kilometers(lat, lng, region.getLat(), region.getLng())))
                    .filter(candidate -> wholeWorld || candidate.kilometers() <= currentRadius)
                    .sorted(Comparator.comparingDouble(DistanceToRegion::kilometers)
                            .thenComparing(candidate -> candidate.region().getId()))
                    .limit(limit)
                    .toList();

            // 사각형 후보가 limit 개인 것만으로 끝내면 모서리보다 가까운 바깥 동네를 놓칠 수 있다.
            // 실제 원 안에 limit 개가 있으면 원 밖의 어떤 동네도 상위 limit 개에 들 수 없다.
            if (wholeWorld || nearest.size() == limit) {
                return nearest;
            }
            radius = Math.min(radius * RADIUS_MULTIPLIER, GeoDistance.MAX_DISTANCE_KM);
        }
    }

    private List<Region> findCandidates(double lat, double lng, double radius) {
        GeoDistance.Bounds bounds = GeoDistance.bounds(lat, lng, radius);
        if (!bounds.crossesDateLine()) {
            return regionRepository.findByLatBetweenAndLngBetween(
                    bounds.minLat(), bounds.maxLat(), bounds.minLng(), bounds.maxLng());
        }
        // 날짜변경선을 넘으면 겹치지 않는 두 범위로 나눈다. OR 없이 동일한 범위 쿼리를 재사용한다.
        List<Region> candidates = new ArrayList<>(regionRepository.findByLatBetweenAndLngBetween(
                bounds.minLat(), bounds.maxLat(), bounds.minLng(), 180));
        candidates.addAll(regionRepository.findByLatBetweenAndLngBetween(
                bounds.minLat(), bounds.maxLat(), -180, bounds.maxLng()));
        return candidates;
    }

    private record DistanceToRegion(Region region, double kilometers) {
    }
}
