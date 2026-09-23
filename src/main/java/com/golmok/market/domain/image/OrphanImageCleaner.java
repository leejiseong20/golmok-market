package com.golmok.market.domain.image;

import com.golmok.market.domain.product.ProductImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 어떤 상품도 가리키지 않는 사진 파일(고아)을 지운다.
 *
 * 고아는 이렇게 생긴다: 사진을 올리고 등록 창을 닫은 경우, 수정하며 사진을 바꾼 경우(행은 지워지고 파일은 남는다),
 * 업로드는 됐는데 상품 저장이 실패한 경우. 지우지 않으면 디스크가 계속 찬다(이미지는 서버 볼륨에 있다).
 *
 * **삭제한 상품(soft delete)의 사진은 고아가 아니다.** product_images 행이 남아 있어 참조로 잡힌다.
 * 상품을 보존한다는 정책과 같은 방향이다.
 *
 * 안전장치가 세 가지다.
 *  1. 최근에 바뀐 파일은 건드리지 않는다(유예 기간). 방금 올린 사진은 아직 상품에 붙지 않았다 —
 *     유예가 없으면 지금 등록 창을 쓰고 있는 사람의 사진을 지운다.
 *  2. 업로드 루트 아래의 경로만 지운다. 실제 경로로 풀어 확인해 심볼릭 링크·`..` 로 빠져나가지 못하게 한다.
 *  3. 축소본(thumb/)은 따로 판단하지 않고 원본과 함께만 지운다. 축소본에는 DB 참조가 없어
 *     혼자 두면 전부 고아로 보이기 때문이다.
 */
@Slf4j
@Component
public class OrphanImageCleaner {

    private final ImageProperties imageProperties;
    private final ProductImageRepository productImageRepository;
    private final Clock clock;

    public OrphanImageCleaner(ImageProperties imageProperties, ProductImageRepository productImageRepository, Clock clock) {
        this.imageProperties = imageProperties;
        this.productImageRepository = productImageRepository;
        this.clock = clock;
    }

    /**
     * @param retention 이 기간 안에 바뀐 파일은 남긴다
     * @param batchSize DB 에 한 번에 물어볼 주소 수
     * @return 지운 원본 수(축소본은 따라 지워진다)
     */
    public long clean(Duration retention, int batchSize) {
        Path root = imageProperties.uploadRoot();
        if (!Files.isDirectory(root)) {
            return 0;
        }
        Instant keepAfter = clock.instant().minus(retention);
        Path thumbnailRoot = root.resolve(ImageService.THUMBNAIL_DIR);

        long deleted = 0;
        List<Path> batch = new ArrayList<>(batchSize);
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith(thumbnailRoot))   // 축소본은 원본을 따라간다
                    .filter(path -> notRecentlyModified(path, keepAfter))::iterator) {
                batch.add(file);
                if (batch.size() >= batchSize) {
                    deleted += deleteOrphans(root, batch);
                    batch.clear();
                }
            }
        } catch (IOException | UncheckedIOException e) {
            log.warn("업로드 폴더를 훑지 못했다. 다음 실행에서 다시 시도한다.", e);
        }
        deleted += deleteOrphans(root, batch);
        removeEmptyDirectories(root);
        return deleted;
    }

    /** 한 묶음의 파일 중 DB 가 모르는 것을 지운다. */
    private long deleteOrphans(Path root, List<Path> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        List<String> urls = batch.stream().map(file -> toUrl(root, file)).toList();
        Set<String> referenced = new HashSet<>(productImageRepository.findExistingUrls(urls));

        long deleted = 0;
        for (int i = 0; i < batch.size(); i++) {
            if (referenced.contains(urls.get(i))) {
                continue;
            }
            Path file = batch.get(i);
            if (delete(root, file)) {
                deleted++;
                // 축소본은 thumb/ 아래에 같은 날짜·같은 이름으로 있다(없을 수도 있다 — webp·작은 사진).
                delete(root, root.resolve(ImageService.THUMBNAIL_DIR).resolve(root.relativize(file)));
            }
        }
        return deleted;
    }

    /** `/api/images/2026/09/23/uuid.jpg` 형태로 되돌린다. DB 에 저장된 형식과 같아야 비교가 된다. */
    private static String toUrl(Path root, Path file) {
        return ImageService.URL_PREFIX + root.relativize(file).toString().replace('\\', '/');
    }

    private boolean notRecentlyModified(Path file, Instant keepAfter) {
        try {
            return Files.getLastModifiedTime(file).toInstant().isBefore(keepAfter);
        } catch (IOException e) {
            return false;   // 시각을 모르면 건드리지 않는다
        }
    }

    /** 업로드 루트 아래인지 실제 경로로 확인한 뒤에만 지운다. 한 파일이 실패해도 나머지는 계속 지운다. */
    private boolean delete(Path root, Path file) {
        try {
            if (!Files.exists(file)) {
                return false;
            }
            Path realRoot = root.toRealPath();
            if (!file.toRealPath().startsWith(realRoot)) {
                log.warn("업로드 폴더 밖의 경로라 지우지 않는다: {}", file);
                return false;
            }
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("사진을 지우지 못했다: {}", file, e);
            return false;
        }
    }

    /** 사진이 빠져 비어 버린 날짜 폴더를 정리한다. 루트는 남긴다. */
    private void removeEmptyDirectories(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isDirectory)
                    .filter(path -> !path.equals(root))
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())   // 깊은 것부터
                    .forEach(directory -> {
                        try (Stream<Path> children = Files.list(directory)) {
                            if (children.findAny().isEmpty()) {
                                Files.deleteIfExists(directory);
                            }
                        } catch (IOException e) {
                            log.debug("빈 폴더를 지우지 못했다: {}", directory);
                        }
                    });
        } catch (IOException | UncheckedIOException e) {
            log.debug("빈 폴더 정리를 건너뛴다", e);
        }
    }
}
