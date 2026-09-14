package com.golmok.market.domain.region;

/** 유효한 위경도를 입력받아 구면 거리(km)와 원을 포함하는 후보 범위를 계산한다. */
final class GeoDistance {

    private static final double EARTH_RADIUS_KM = 6371.0088;
    static final double MAX_DISTANCE_KM = Math.PI * EARTH_RADIUS_KM;
    // 부동소수점 반올림으로 원 경계의 후보가 DB 범위에서 빠지는 것을 방지한다.
    private static final double BOUNDS_MARGIN_DEGREES = 1e-9;

    private GeoDistance() {
    }

    static double kilometers(double lat, double lng, double otherLat, double otherLng) {
        double latitude = Math.toRadians(lat);
        double otherLatitude = Math.toRadians(otherLat);
        double sinLat = Math.sin((otherLatitude - latitude) / 2);
        double sinLng = Math.sin(Math.toRadians(otherLng - lng) / 2);
        double haversine = sinLat * sinLat + Math.cos(latitude) * Math.cos(otherLatitude) * sinLng * sinLng;
        // 대척점 부근에서 반올림으로 1을 넘으면 asin 이 NaN 이 되므로 보정한다.
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(Math.clamp(haversine, 0, 1)));
    }

    static Bounds bounds(double lat, double lng, double radiusKm) {
        double angularRadius = radiusKm / EARTH_RADIUS_KM;
        double latitudeDelta = Math.toDegrees(angularRadius) + BOUNDS_MARGIN_DEGREES;
        double minLat = Math.max(-90, lat - latitudeDelta);
        double maxLat = Math.min(90, lat + latitudeDelta);
        if (minLat == -90 || maxLat == 90) {
            // 극점을 포함하는 원은 모든 경도를 후보로 허용해야 한다.
            return new Bounds(minLat, maxLat, -180, 180);
        }
        double longitudeDelta = Math.toDegrees(Math.asin(Math.clamp(
                Math.sin(angularRadius) / Math.cos(Math.toRadians(lat)), -1, 1))) + BOUNDS_MARGIN_DEGREES;
        return new Bounds(minLat, maxLat, normalize(lng - longitudeDelta), normalize(lng + longitudeDelta));
    }

    private static double normalize(double longitude) {
        return (longitude + 540) % 360 - 180;
    }

    record Bounds(double minLat, double maxLat, double minLng, double maxLng) {

        boolean crossesDateLine() {
            return minLng > maxLng;
        }
    }
}
