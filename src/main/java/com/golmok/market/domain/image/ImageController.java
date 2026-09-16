package com.golmok.market.domain.image;

import com.golmok.market.domain.image.dto.ImageUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    /**
     * 인증한 사용자만 올릴 수 있다. 명세에 없는 제약이지만, 열어두면 디스크를 채우는 공격이 가능하다.
     * (경로 권한은 SecurityConfig 의 기본 차단 규칙이 적용된다)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImageUploadResponse upload(@RequestParam("files") List<MultipartFile> files) {
        return new ImageUploadResponse(imageService.upload(files));
    }
}
