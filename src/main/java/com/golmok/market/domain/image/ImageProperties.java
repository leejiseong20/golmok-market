package com.golmok.market.domain.image;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param uploadDir 이미지를 저장할 디렉터리. 로컬 디스크다.
 *                  배포 시 컨테이너·인스턴스가 바뀌면 파일이 사라지므로 볼륨을 붙이거나 S3 로 옮겨야 한다.
 * @param maxCount  한 번에 올릴 수 있는 장수. 상품 이미지 상한(10장)과 같다.
 * @param thumbnailMaxEdge 목록용 축소본의 긴 변 길이(px). 카드가 화면에서 100~400px 이라 고해상도 화면까지 감안한 값이다.
 */
@ConfigurationProperties(prefix = "app.image")
public record ImageProperties(String uploadDir, int maxCount, int thumbnailMaxEdge) {

    public ImageProperties {
        if (uploadDir == null || uploadDir.isBlank()) {
            throw new IllegalStateException("app.image.upload-dir 이 설정되지 않았습니다.");
        }
        if (maxCount < 1) {
            throw new IllegalStateException("app.image.max-count 는 1 이상이어야 합니다.");
        }
        if (thumbnailMaxEdge < 1) {
            throw new IllegalStateException("app.image.thumbnail-max-edge 는 1 이상이어야 합니다.");
        }
    }
}
