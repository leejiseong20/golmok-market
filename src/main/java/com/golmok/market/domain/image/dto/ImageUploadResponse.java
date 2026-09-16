package com.golmok.market.domain.image.dto;

import java.util.List;

/** 상품 등록 요청의 imageUrls 에 그대로 넣을 수 있는 형태. */
public record ImageUploadResponse(List<String> imageUrls) {
}
