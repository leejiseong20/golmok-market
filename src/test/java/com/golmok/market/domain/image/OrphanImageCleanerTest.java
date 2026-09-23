package com.golmok.market.domain.image;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductImageRepository;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상품이 가리키지 않는 사진 파일(고아) 정리.
 *
 * 파일을 실제로 만들고 지워지는지 본다. 시각은 파일의 수정 시각을 직접 바꿔 "오래된 파일"을 만든다.
 */
@SpringBootTest
@Transactional
class OrphanImageCleanerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-23T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final Duration RETENTION = Duration.ofDays(3);

    @Autowired ProductImageRepository productImageRepository;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired RegionRepository regionRepository;

    @TempDir Path uploadRoot;
    private OrphanImageCleaner cleaner;
    private User seller;
    private Category category;
    private Region region;

    @BeforeEach
    void 준비() {
        cleaner = new OrphanImageCleaner(new ImageProperties(uploadRoot.toString(), 10, 640), productImageRepository, CLOCK);
        seller = userRepository.save(User.builder().email("orphan@example.com").password("hash").nickname("고아정리").build());
        category = categoryRepository.save(Category.create(null, "정리카테고리", null, 1));
        region = regionRepository.save(Region.create("서울", "강남", "역삼동", 37.5, 127.0));
    }

    /** `2026/09/01/이름.jpg` 에 파일을 만들고 주소를 돌려준다. old 면 유예 기간보다 오래된 파일이다. */
    private String file(String name, boolean old) throws IOException {
        return write(uploadRoot, "2026/09/01/" + name, old);
    }

    private String thumbnail(String name, boolean old) throws IOException {
        write(uploadRoot, ImageService.THUMBNAIL_DIR + "/2026/09/01/" + name, old);
        return ImageService.URL_PREFIX + "2026/09/01/" + name;
    }

    private String write(Path root, String relative, boolean old) throws IOException {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, "사진 내용");
        Instant when = old ? CLOCK.instant().minus(RETENTION).minusSeconds(60) : CLOCK.instant();
        Files.setLastModifiedTime(path, FileTime.from(when));
        return ImageService.URL_PREFIX + relative;
    }

    private Product product(String imageUrl, boolean deleted) {
        Product product = Product.builder().seller(seller).category(category).region(region)
                .title("정리 확인용 상품").description("고아 사진 정리를 확인하는 상품입니다.").price(1000).build();
        product.addImage(imageUrl);
        Product saved = productRepository.saveAndFlush(product);
        if (deleted) {
            saved.softDelete();
            productRepository.saveAndFlush(saved);
        }
        return saved;
    }

    private boolean exists(String relative) {
        return Files.exists(uploadRoot.resolve(relative));
    }

    @Test
    void 상품이_가리키지_않는_오래된_사진만_지운다() throws IOException {
        String used = file("used.jpg", true);
        String orphan = file("orphan.jpg", true);
        product(used, false);

        assertThat(cleaner.clean(RETENTION, 500)).isEqualTo(1);
        assertThat(exists("2026/09/01/used.jpg")).isTrue();
        assertThat(exists("2026/09/01/orphan.jpg")).isFalse();
        assertThat(orphan).isNotEqualTo(used);
    }

    @Test
    void 방금_올린_사진은_아직_상품에_붙지_않았어도_남긴다() throws IOException {
        file("just-uploaded.jpg", false);

        assertThat(cleaner.clean(RETENTION, 500)).isZero();
        assertThat(exists("2026/09/01/just-uploaded.jpg")).isTrue();
    }

    @Test
    void 삭제한_상품의_사진은_지우지_않는다() throws IOException {
        String url = file("soft-deleted.jpg", true);
        product(url, true);

        assertThat(cleaner.clean(RETENTION, 500)).isZero();
        assertThat(exists("2026/09/01/soft-deleted.jpg")).isTrue();
    }

    @Test
    void 축소본은_원본과_함께_지워지고_혼자_지워지지_않는다() throws IOException {
        String used = file("used.jpg", true);
        thumbnail("used.jpg", true);          // 쓰이는 사진의 축소본 — 남아야 한다
        file("orphan.jpg", true);
        thumbnail("orphan.jpg", true);        // 고아의 축소본 — 함께 지워져야 한다
        product(used, false);

        assertThat(cleaner.clean(RETENTION, 500)).isEqualTo(1);
        assertThat(exists("thumb/2026/09/01/used.jpg")).isTrue();
        assertThat(exists("thumb/2026/09/01/orphan.jpg")).isFalse();
    }

    @Test
    void 여러_묶음으로_나눠도_모두_지운다() throws IOException {
        for (int i = 0; i < 7; i++) {
            file("orphan-" + i + ".jpg", true);
        }
        String used = file("used.jpg", true);
        product(used, false);

        assertThat(cleaner.clean(RETENTION, 2)).isEqualTo(7);
        assertThat(exists("2026/09/01/used.jpg")).isTrue();
    }

    @Test
    void 사진이_빠져_비어버린_날짜_폴더는_정리한다() throws IOException {
        file("orphan.jpg", true);

        assertThat(cleaner.clean(RETENTION, 500)).isEqualTo(1);
        assertThat(Files.exists(uploadRoot.resolve("2026"))).isFalse();
        assertThat(Files.isDirectory(uploadRoot)).isTrue();   // 루트는 남는다
    }

    @Test
    void 업로드_폴더_밖의_파일은_건드리지_않는다(@TempDir Path outside) throws IOException {
        Path secret = outside.resolve("secret.txt");
        Files.writeString(secret, "지우면 안 되는 파일");
        Files.setLastModifiedTime(secret, FileTime.from(CLOCK.instant().minus(RETENTION).minusSeconds(60)));
        // 업로드 폴더 안에서 밖을 가리키는 링크를 만들 수 있으면 만들어 본다(윈도우는 권한이 없으면 건너뛴다).
        try {
            Files.createDirectories(uploadRoot.resolve("2026/09/01"));
            Files.createSymbolicLink(uploadRoot.resolve("2026/09/01/link.txt"), secret);
        } catch (IOException | UnsupportedOperationException e) {
            return;
        }

        cleaner.clean(RETENTION, 500);
        assertThat(Files.exists(secret)).isTrue();
    }
}
