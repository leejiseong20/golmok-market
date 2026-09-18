package com.golmok.market.domain.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 목록에 쓸 축소본을 만든다.
 *
 * 목록 카드의 사진은 화면에서 100~400px 인데 원본(수 MB)을 그대로 내려받고 있었다.
 * 긴 변을 기준으로 줄여 비율은 그대로 두고, 원본보다 크게 만들지 않는다.
 *
 * 만들지 못하는 경우(webp · 깨진 파일 · 이미 작은 사진)에는 null 을 돌려주고 원본을 그대로 쓴다.
 * webp 는 기본 ImageIO 가 읽지 못한다. 추가 의존성 없이 갈 수 있는 선까지만 한다(알려진 한계).
 *
 * 축소는 업로드 요청 안에서 한다. 사진 몇 장 수준이라 수백 ms 면 끝나고,
 * 비동기로 미루면 "아직 없는 축소본"을 다루는 상태가 늘어난다.
 */
@Slf4j
@Component
public class ImageResizer {

    /** @return 축소본 바이트, 만들 수 없거나 만들 필요가 없으면 null */
    public byte[] resize(byte[] content, ImageType type, int maxEdge) {
        if (type == ImageType.WEBP) {
            return null;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            if (source == null) {
                return null;
            }
            int longEdge = Math.max(source.getWidth(), source.getHeight());
            if (longEdge <= maxEdge) {
                return null; // 이미 충분히 작다. 같은 크기의 파일을 하나 더 두지 않는다.
            }
            double ratio = (double) maxEdge / longEdge;
            int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
            int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));

            // JPEG 은 투명도를 표현하지 못한다. 투명한 PNG 를 JPEG 로 저장하면 검게 나온다.
            int imageType = type == ImageType.PNG ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
            BufferedImage target = new BufferedImage(width, height, imageType);
            var graphics = target.createGraphics();
            try {
                graphics.drawImage(source.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);
            } finally {
                graphics.dispose();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(target, type.getExtension(), out)) {
                return null;
            }
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            // 축소본은 있으면 좋은 것이다. 실패해도 업로드는 성공해야 한다.
            log.warn("썸네일 생성 실패, 원본을 그대로 쓴다", e);
            return null;
        }
    }
}
