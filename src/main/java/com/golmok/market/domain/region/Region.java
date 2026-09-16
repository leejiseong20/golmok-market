package com.golmok.market.domain.region;

import com.golmok.market.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "regions", uniqueConstraints =
        @UniqueConstraint(name = "uk_regions_full", columnNames = {"sido", "sigungu", "dong"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String sido;

    @Column(nullable = false, length = 30)
    private String sigungu;

    @Column(nullable = false, length = 30)
    private String dong;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    /** DB 기본값 없이도 JPA auditing 으로 생성 시각을 채운다. */
    public static Region create(String sido, String sigungu, String dong, double lat, double lng) {
        Region region = new Region();
        region.sido = sido;
        region.sigungu = sigungu;
        region.dong = dong;
        region.lat = lat;
        region.lng = lng;
        return region;
    }

    /** 화면 표시용 전체 주소 */
    public String getFullName() {
        return sido + " " + sigungu + " " + dong;
    }
}
