package com.golmok.market.domain.region;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RegionRepository extends JpaRepository<Region, Long> {

    /** escape 문자를 명시해 사용자의 %, _ 를 LIKE 와일드카드로 해석하지 않는다. */
    @Query("""
            select r from Region r
            where concat(r.sido, ' ', r.sigungu, ' ', r.dong) like :pattern escape '!'
            order by r.id asc
            """)
    List<Region> searchByFullName(String pattern);

    // MySQL 전용 공간 함수 없이 두 DB 에서 동일한 범위 조건을 사용한다.
    List<Region> findByLatBetweenAndLngBetween(double minLat, double maxLat, double minLng, double maxLng);
}
