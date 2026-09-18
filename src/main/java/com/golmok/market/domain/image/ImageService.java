package com.golmok.market.domain.image;

import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 이미지 업로드. 저장은 로컬 디스크, 서빙은 {@code GET /api/images/**} (StaticResourceConfig).
 *
 * 업로드와 상품 등록을 분리한 설계라, 여기서는 파일만 저장하고 URL 을 돌려준다.
 * 상품에 연결되지 않은 파일은 그대로 남는다(고아 파일 정리는 과제).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    public static final String URL_PREFIX = "/api/images/";
    /** 축소본은 원본과 같은 이름으로 이 폴더 아래 같은 날짜 경로에 둔다(주소 규칙만으로 찾을 수 있다). */
    public static final String THUMBNAIL_DIR = "thumb";
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final ImageProperties properties;
    private final ImageResizer resizer;
    private final Clock clock;

    public List<String> upload(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지를 한 장 이상 선택해 주세요.");
        }
        if (files.size() > properties.maxCount()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이미지는 한 번에 %d장까지 올릴 수 있습니다.".formatted(properties.maxCount()));
        }

        // 날짜별 폴더로 나눈다. 한 폴더에 파일이 수십만 개 쌓이면 파일시스템 탐색이 느려진다.
        String datePath = LocalDate.now(clock).format(DATE_PATH);
        Path directory = Path.of(properties.uploadDir(), datePath);
        List<Path> written = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        try {
            Files.createDirectories(directory);
            for (MultipartFile file : files) {
                byte[] content = read(file);
                ImageType type = ImageType.detect(content);
                if (type == null) {
                    throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
                }
                // 원본 파일명은 쓰지 않는다. 경로 조작(../)과 중복·한글 파일명 문제를 원천적으로 없앤다.
                String name = UUID.randomUUID() + "." + type.getExtension();
                Path target = directory.resolve(name);
                Files.write(target, content);
                written.add(target);
                writeThumbnail(datePath, name, content, type, written);
                urls.add(URL_PREFIX + datePath + "/" + name);
            }
            return urls;
        } catch (BusinessException e) {
            deleteQuietly(written);   // 3장 중 2장만 저장된 채로 실패하면 나머지가 고아 파일로 남는다
            throw e;
        } catch (IOException e) {
            deleteQuietly(written);
            log.error("이미지 저장 실패", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 축소본을 같이 저장한다. 만들지 못하면(webp · 이미 작은 사진) 아무것도 남기지 않고,
     * 조회 때 원본으로 대신한다(ImageController.thumbnail).
     */
    private void writeThumbnail(String datePath, String name, byte[] content, ImageType type, List<Path> written)
            throws IOException {
        byte[] thumbnail = resizer.resize(content, type, properties.thumbnailMaxEdge());
        if (thumbnail == null) {
            return;
        }
        Path directory = Path.of(properties.uploadDir(), THUMBNAIL_DIR, datePath);
        Files.createDirectories(directory);
        Path target = directory.resolve(name);
        Files.write(target, thumbnail);
        written.add(target);
    }

    private byte[] read(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "빈 파일은 올릴 수 없습니다.");
        }
        return file.getBytes();
    }

    private void deleteQuietly(List<Path> paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("업로드 실패 후 파일 삭제 실패: {}", path, e);
            }
        }
    }
}
