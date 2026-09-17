package com.golmok.market.domain.search;

import com.golmok.market.domain.search.dto.PopularKeywordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 비로그인도 볼 수 있다(SecurityConfig 공개 GET 목록). */
@RestController
@RequiredArgsConstructor
public class SearchController {

    private final PopularKeywordService popularKeywordService;

    @GetMapping("/api/search/keywords/popular")
    public List<PopularKeywordResponse> findPopular() {
        return popularKeywordService.findPopular();
    }
}
