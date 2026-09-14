package com.golmok.market.domain.region;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoDistanceTest {

    @Test
    void 같은_좌표의_거리는_0이다() {
        assertThat(GeoDistance.kilometers(37.5, 127, 37.5, 127)).isZero();
    }

    @Test
    void 적도에서_경도_1도의_거리는_약_111점195km이다() {
        assertThat(GeoDistance.kilometers(0, 0, 0, 1)).isCloseTo(111.195, within(0.001));
    }

    @Test
    void 대척점의_거리도_NaN없이_반원_거리이다() {
        assertThat(GeoDistance.kilometers(37.5, 127, -37.5, -53))
                .isFinite().isCloseTo(20015.114, within(0.001));
    }

    @Test
    void 날짜변경선은_짧은_쪽으로_거리를_계산한다() {
        assertThat(GeoDistance.kilometers(0, 179.99, 0, -179.99)).isCloseTo(2.224, within(0.001));
    }

    @Test
    void 극점은_경도가_달라도_동일한_위치이다() {
        assertThat(GeoDistance.kilometers(90, -180, 90, 0)).isCloseTo(0, within(1e-9));
    }

    @Test
    void 후보_사각형은_여러_위치와_반경에서_원의_경계를_빠짐없이_포함한다() {
        double[][] origins = {{0, 0}, {37.5, 127}, {0, 179.99}, {0, -179.99},
                {89.99, 45}, {-89.99, -45}, {90, 180}, {-90, -180}};
        for (double[] origin : origins) {
            for (double radius : new double[]{5, 20, 80, 320, 1280, 5120}) {
                GeoDistance.Bounds bounds = GeoDistance.bounds(origin[0], origin[1], radius);
                for (int bearing = 0; bearing < 360; bearing += 15) {
                    // 거리의 역산 대신 출발점·방위각·이동 거리로 원 경계의 목적지를 만든다.
                    double[] point = destination(origin[0], origin[1], radius, bearing);
                    assertThat(point[0]).as("위도: 출발점=%s,%s 반경=%s 방위=%s", origin[0], origin[1], radius, bearing)
                            .isBetween(bounds.minLat(), bounds.maxLat());
                    boolean longitudeInside = bounds.crossesDateLine()
                            ? point[1] >= bounds.minLng() || point[1] <= bounds.maxLng()
                            : point[1] >= bounds.minLng() && point[1] <= bounds.maxLng();
                    assertThat(longitudeInside).as("경도: 출발점=%s,%s 반경=%s 방위=%s", origin[0], origin[1], radius, bearing)
                            .isTrue();
                }
            }
        }
    }

    private double[] destination(double lat, double lng, double kilometers, double bearing) {
        double angularDistance = kilometers / 6371.0088;
        double latitude = Math.toRadians(lat);
        double direction = Math.toRadians(bearing);
        double targetLat = Math.asin(Math.sin(latitude) * Math.cos(angularDistance)
                + Math.cos(latitude) * Math.sin(angularDistance) * Math.cos(direction));
        double targetLng = Math.toRadians(lng) + Math.atan2(
                Math.sin(direction) * Math.sin(angularDistance) * Math.cos(latitude),
                Math.cos(angularDistance) - Math.sin(latitude) * Math.sin(targetLat));
        return new double[]{Math.toDegrees(targetLat), (Math.toDegrees(targetLng) + 540) % 360 - 180};
    }
}
