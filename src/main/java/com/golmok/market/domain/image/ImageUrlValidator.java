package com.golmok.market.domain.image;

import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/** 업로드 API가 발급한 경로만 받는다. 파일 소유권은 별도 메타데이터 도입 전까지 확인하지 않는다. */
@Component
@RequiredArgsConstructor
public class ImageUrlValidator {
    private static final Pattern URL = Pattern.compile(
            "/api/images/\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");
    private final ImageProperties properties;

    public void validate(List<String> urls) {
        for (String url : urls) {
            if (url == null || !URL.matcher(url).matches()) {
                throw new BusinessException(ErrorCode.INVALID_IMAGE_URL);
            }
            try {
                Path root = Path.of(properties.uploadDir()).toRealPath();
                Path file = root.resolve(url.substring(ImageService.URL_PREFIX.length())).toRealPath();
                // 실제 경로도 검사해 심볼릭 링크를 통한 저장 디렉터리 이탈을 막는다.
                if (!file.startsWith(root) || !Files.isRegularFile(file) || !Files.isReadable(file)) {
                    throw new BusinessException(ErrorCode.INVALID_IMAGE_URL);
                }
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.INVALID_IMAGE_URL);
            }
        }
    }
}
