package com.golmok.market.domain.image;

import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ImageApiTest {

    // 각 형식의 실제 시그니처. 확장자가 아니라 이 바이트로 형식을 판별한다.
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x11, 0x22};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x10, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired ImageProperties properties;

    private String token;

    @BeforeEach
    void 준비() {
        User user = userRepository.save(User.builder().email("me@example.com").password("hash").nickname("골목이").build());
        token = tokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("files", name, "image/jpeg", content);
    }

    private MockMultipartHttpServletRequestBuilder upload(MockMultipartFile... files) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/images");
        for (MockMultipartFile file : files) {
            request.file(file);
        }
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    // ---------- 업로드 ----------

    @Test
    void 이미지를_올리면_URL_을_돌려주고_실제_파일이_저장된다() throws Exception {
        String body = mockMvc.perform(upload(file("사진.jpg", JPEG)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrls", hasSize(1)))
                // 날짜 폴더 + UUID 파일명. 원본 파일명은 쓰지 않는다.
                .andExpect(jsonPath("$.imageUrls[0]",
                        matchesPattern("/api/images/\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}\\.jpg")))
                .andReturn().getResponse().getContentAsString();

        String url = JsonPath.read(body, "$.imageUrls[0]");
        Path saved = Path.of(properties.uploadDir(), url.substring("/api/images/".length()));
        assertThat(Files.exists(saved)).isTrue();
        assertThat(Files.readAllBytes(saved)).isEqualTo(JPEG);
    }

    @Test
    void 여러_장을_한_번에_올릴_수_있다() throws Exception {
        mockMvc.perform(upload(file("a.jpg", JPEG), file("b.png", PNG), file("c.webp", WEBP)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrls", hasSize(3)));
    }

    @Test
    void 형식은_확장자가_아니라_파일_내용으로_판별한다() throws Exception {
        // 이름은 .jpg 지만 내용은 PNG → png 로 저장된다
        mockMvc.perform(upload(file("가짜.jpg", PNG)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrls[0]", matchesPattern(".*\\.png")));
    }

    @Test
    void 이미지가_아닌_파일은_400() throws Exception {
        // 이름과 Content-Type 은 이미지지만 내용은 실행 파일(MZ 헤더)
        mockMvc.perform(upload(file("악성.jpg", new byte[]{'M', 'Z', 0x00, 0x01})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_TYPE"));
    }

    @Test
    void 빈_파일은_400() throws Exception {
        mockMvc.perform(upload(file("빈파일.jpg", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 장수_제한을_넘으면_400_이고_파일이_남지_않는다() throws Exception {
        MockMultipartFile[] files = new MockMultipartFile[properties.maxCount() + 1];
        for (int i = 0; i < files.length; i++) {
            files[i] = file("사진" + i + ".jpg", JPEG);
        }

        mockMvc.perform(upload(files))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 일부가_실패하면_먼저_저장된_파일도_지운다() throws Exception {
        long before = countSavedFiles();

        mockMvc.perform(upload(file("정상.jpg", JPEG), file("깨진.jpg", new byte[]{'M', 'Z'})))
                .andExpect(status().isBadRequest());

        assertThat(countSavedFiles()).isEqualTo(before);
    }

    @Test
    void 비로그인_업로드는_401() throws Exception {
        mockMvc.perform(multipart("/api/images").file(file("사진.jpg", JPEG)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ---------- 서빙 ----------

    @Test
    void 올린_이미지는_비로그인으로도_받을_수_있다() throws Exception {
        String body = mockMvc.perform(upload(file("사진.png", PNG)))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.imageUrls[0]");

        byte[] served = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(served).isEqualTo(PNG);
    }

    @Test
    void 없는_이미지는_404() throws Exception {
        mockMvc.perform(get("/api/images/2026/01/01/없는파일.jpg"))
                .andExpect(status().isNotFound());
    }

    private long countSavedFiles() throws Exception {
        Path root = Path.of(properties.uploadDir());
        if (!Files.exists(root)) {
            return 0;
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

}
