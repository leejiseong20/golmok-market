package com.golmok.market.domain.image;

import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 목록용 축소본. 테스트 설정의 긴 변 기준은 64px 이다(application.yml).
 *
 * 축소본이 없을 수 있는 경우(webp · 이미 작은 사진 · 기능 도입 전 사진)에도
 * 화면이 깨지지 않도록 조회가 원본으로 대신하는지까지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ImageThumbnailTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired ImageProperties properties;
    @Autowired ImageResizer resizer;

    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x10, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};

    /** 실제로 읽을 수 있는 JPEG 을 만든다. 시그니처만 흉내 낸 바이트로는 축소가 되지 않는다. */
    private static byte[] jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(210, 80, 47));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private String token() {
        User user = userRepository.save(User.builder()
                .email("thumb" + System.nanoTime() + "@example.com").password("hash")
                .nickname("썸네일" + System.nanoTime()).build());
        return tokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    private String upload(byte[] content, String name) throws Exception {
        String body = mockMvc.perform(multipart("/api/images")
                        .file(new MockMultipartFile("files", name, "image/jpeg", content))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.imageUrls[0]");
    }

    private Path filePath(String url, boolean thumbnail) {
        String relative = url.substring(ImageService.URL_PREFIX.length());
        Path root = Path.of(properties.uploadDir());
        return thumbnail ? root.resolve(ImageService.THUMBNAIL_DIR).resolve(relative) : root.resolve(relative);
    }

    private static String thumbnailUrl(String url) {
        return ImageService.URL_PREFIX + ImageService.THUMBNAIL_DIR + "/"
                + url.substring(ImageService.URL_PREFIX.length());
    }

    @Test
    void 큰_사진을_올리면_축소본이_함께_저장되고_비율이_유지된다() throws Exception {
        String url = upload(jpeg(400, 200), "big.jpg");

        Path thumbnail = filePath(url, true);
        assertThat(Files.exists(thumbnail)).isTrue();
        BufferedImage saved = ImageIO.read(thumbnail.toFile());
        assertThat(saved.getWidth()).isEqualTo(64);
        assertThat(saved.getHeight()).isEqualTo(32);
        // 축소본이 원본보다 작아야 의미가 있다.
        assertThat(Files.size(thumbnail)).isLessThan(Files.size(filePath(url, false)));
    }

    @Test
    void 이미_작은_사진은_축소본을_만들지_않는다() throws Exception {
        String url = upload(jpeg(40, 40), "small.jpg");

        assertThat(Files.exists(filePath(url, true))).isFalse();
    }

    @Test
    void webp_는_축소하지_못하지만_업로드는_성공한다() throws Exception {
        String url = upload(WEBP, "photo.webp");

        assertThat(Files.exists(filePath(url, false))).isTrue();
        assertThat(Files.exists(filePath(url, true))).isFalse();
    }

    @Test
    void 축소본이_있으면_축소본을_없으면_원본을_내려준다() throws Exception {
        String big = upload(jpeg(400, 200), "big.jpg");
        String small = upload(jpeg(40, 40), "small.jpg");

        byte[] served = mockMvc.perform(get(thumbnailUrl(big)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=2592000, public"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(Files.readAllBytes(filePath(big, true)));

        byte[] fallback = mockMvc.perform(get(thumbnailUrl(small)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(fallback).isEqualTo(Files.readAllBytes(filePath(small, false)));
    }

    @Test
    void 없는_사진은_404_이고_경로_조작은_막힌다() throws Exception {
        mockMvc.perform(get("/api/images/thumb/2026/01/01/none.jpg")).andExpect(status().isNotFound());
        // ../ 는 Spring 의 요청 방화벽이 컨트롤러에 닿기 전에 400 으로 막는다.
        // 컨트롤러의 경로 검사는 그 뒤의 이중 방어다(방화벽 설정이 바뀌어도 폴더 밖을 읽지 않는다).
        mockMvc.perform(get("/api/images/thumb/../../secret.jpg")).andExpect(status().is4xxClientError());
    }

    @Test
    void 읽을_수_없는_파일은_축소를_건너뛴다() {
        assertThat(resizer.resize("깨진 파일".getBytes(), ImageType.JPEG, 64)).isNull();
    }
}
