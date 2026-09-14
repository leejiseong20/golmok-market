package com.golmok.market.domain.category;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoryApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired CategoryRepository categoryRepository;

    @Test
    void 비로그인으로_카테고리_7개를_명세의_배열로_조회한다() throws Exception {
        String[] names = {"디지털", "가구", "의류", "생활가전", "스포츠", "유아", "기타"};
        for (int i = names.length - 1; i >= 0; i--) {
            categoryRepository.save(Category.create(null, names[i], null, i + 1));
        }

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[0]", aMapWithSize(4)))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].name").value("디지털"))
                .andExpect(jsonPath("$[0].iconUrl", nullValue()))
                .andExpect(jsonPath("$[0].sortOrder").value(1))
                .andExpect(jsonPath("$[6].name").value("기타"));
    }

    @Test
    void 데이터가_없으면_빈_배열이다() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 공개_조회에도_위조_토큰을_보내면_401이다() throws Exception {
        mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }
}
