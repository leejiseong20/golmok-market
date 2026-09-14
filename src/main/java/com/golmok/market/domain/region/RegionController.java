package com.golmok.market.domain.region;

import com.golmok.market.domain.region.dto.RegionResponse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @InitBinder
    void trimSearchKeyword(WebDataBinder binder) {
        // 길이 검증도 앞뒤 공백을 제거한 검색어를 기준으로 한다.
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }

    @GetMapping
    public List<RegionResponse> search(
            @RequestParam @NotBlank(message = "검색어는 필수입니다.")
            @Size(max = 82, message = "검색어는 82자 이하여야 합니다.") String keyword) {
        return regionService.search(keyword);
    }

    @GetMapping("/nearby")
    public List<RegionResponse> findNearby(
            @RequestParam @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 합니다.")
            @DecimalMax(value = "90", message = "위도는 90 이하여야 합니다.") double lat,
            @RequestParam @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 합니다.")
            @DecimalMax(value = "180", message = "경도는 180 이하여야 합니다.") double lng) {
        return regionService.findNearby(lat, lng);
    }
}
