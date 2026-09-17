package com.golmok.market.domain.user;

import com.golmok.market.domain.image.ImageProperties;
import com.golmok.market.domain.image.ImageService;
import com.golmok.market.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PATCH /api/users/me — 닉네임·프로필 사진 수정. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileUpdateApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ImageService imageService;
    @Autowired ImageProperties imageProperties;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired EntityManager em;

    private User me;
    private String token;
    private String imageUrl;

    @BeforeEach
    void 준비() {
        me = userRepository.save(User.builder().email("me@example.com").password("hash").nickname("골목이").build());
        userRepository.save(User.builder().email("other@example.com").password("hash").nickname("이웃").build());
        token = tokenProvider.createAccessToken(me.getId(), me.getRole());
        imageUrl = imageService.upload(List.of(new MockMultipartFile("files", "me.jpg", "image/jpeg",
                new byte[]{(byte) 255, (byte) 216, (byte) 255, 0}))).getFirst();
    }

    @AfterEach
    void 파일_정리() throws Exception {
        Files.deleteIfExists(Path.of(imageProperties.uploadDir(), imageUrl.substring(ImageService.URL_PREFIX.length())));
    }

    private ResultActions update(String body) throws Exception {
        return mockMvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private User reload() {
        em.flush();
        em.clear();
        return userRepository.findById(me.getId()).orElseThrow();
    }

    @Test
    void 닉네임과_사진을_바꾸면_갱신된_내_정보를_돌려준다() throws Exception {
        update("{\"nickname\":\"  새이름  \",\"profileImageUrl\":\"" + imageUrl + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(me.getId()))
                .andExpect(jsonPath("$.nickname").value("새이름"))
                .andExpect(jsonPath("$.profileImageUrl").value(imageUrl))
                .andExpect(jsonPath("$.mannerTemp").value(36.5))
                .andExpect(jsonPath("$.regions").isArray());

        User saved = reload();
        assertThat(saved.getNickname()).isEqualTo("새이름");
        assertThat(saved.getProfileImageUrl()).isEqualTo(imageUrl);
    }

    @Test
    void 지금_닉네임을_그대로_보내도_성공이고_사진을_null_로_보내면_지운다() throws Exception {
        update("{\"nickname\":\"골목이\",\"profileImageUrl\":\"" + imageUrl + "\"}").andExpect(status().isOk());

        update("{\"nickname\":\"골목이\",\"profileImageUrl\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("골목이"))
                .andExpect(jsonPath("$.profileImageUrl", nullValue()));
        assertThat(reload().getProfileImageUrl()).isNull();
    }

    @Test
    void 다른_사람이_쓰는_닉네임은_409() throws Exception {
        update("{\"nickname\":\"이웃\",\"profileImageUrl\":null}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));
        assertThat(reload().getNickname()).isEqualTo("골목이");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"nickname\":\"가\",\"profileImageUrl\":null}",
            "{\"nickname\":\"   \",\"profileImageUrl\":null}",
            "{\"nickname\":\" 가 \",\"profileImageUrl\":null}",
            "{\"profileImageUrl\":null}"
    })
    void 닉네임은_앞뒤_공백을_뺀_2에서_30자여야_한다(String body) throws Exception {
        update(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("nickname"));
    }

    @Test
    void 닉네임_31자는_400() throws Exception {
        update("{\"nickname\":\"" + "가".repeat(31) + "\",\"profileImageUrl\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nickname"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/me.jpg", "/api/images/../secret",
            "/api/images/2026/09/17/00000000-0000-0000-0000-000000000000.jpg"})
    void 서버에_업로드한_사진_경로가_아니면_400(String bad) throws Exception {
        update("{\"nickname\":\"골목이\",\"profileImageUrl\":\"" + bad + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE_URL"));
        assertThat(reload().getProfileImageUrl()).isNull();
    }

    @Test
    void 비로그인은_401() throws Exception {
        mockMvc.perform(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새이름\",\"profileImageUrl\":null}"))
                .andExpect(status().isUnauthorized());
    }
}
