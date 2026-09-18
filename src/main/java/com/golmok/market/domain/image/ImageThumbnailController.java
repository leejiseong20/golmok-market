package com.golmok.market.domain.image;

import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 목록용 축소본 조회: {@code GET /api/images/thumb/{원본과 같은 경로}}.
 *
 * 축소본이 없으면(webp · 이미 작은 사진 · 이 기능 이전에 올린 사진) 원본을 대신 내려준다.
 * 화면이 404 를 받고 다시 요청하는 대신 서버가 한 번에 해결한다. 주소 규칙만으로 찾으므로 DB 에 컬럼을 늘리지 않았다.
 *
 * 이 경로는 정적 리소스 핸들러(StaticResourceConfig)보다 먼저 잡힌다(컨트롤러 매핑이 우선).
 * 그래서 경로 조작 검사도 여기서 직접 한다. 정규화한 경로가 업로드 폴더 안인지 반드시 확인한다.
 */
@RestController
@RequiredArgsConstructor
public class ImageThumbnailController {

    private static final String PREFIX = ImageService.URL_PREFIX + ImageService.THUMBNAIL_DIR + "/";

    private final ImageProperties properties;

    @GetMapping(ImageService.URL_PREFIX + ImageService.THUMBNAIL_DIR + "/**")
    public ResponseEntity<Resource> thumbnail(HttpServletRequest request) {
        String relative = request.getRequestURI().substring(PREFIX.length());
        Path root = Path.of(properties.uploadDir()).toAbsolutePath().normalize();
        Path thumbnail = resolveInside(root.resolve(ImageService.THUMBNAIL_DIR), relative);
        Path original = resolveInside(root, relative);

        Path file = Files.isRegularFile(thumbnail) ? thumbnail : original;
        if (!Files.isRegularFile(file)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이미지를 찾을 수 없습니다.");
        }
        return ResponseEntity.ok()
                // 파일명이 UUID 라 내용이 바뀌지 않는다. 원본 서빙과 같은 정책이다.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .contentType(mediaType(file))
                .body(new FileSystemResource(file));
    }

    /** ../ 로 업로드 폴더 밖을 가리키면 없는 이미지로 본다(존재 여부를 알려주지 않는다). */
    private Path resolveInside(Path base, String relative) {
        Path resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이미지를 찾을 수 없습니다.");
        }
        return resolved;
    }

    private MediaType mediaType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (name.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
