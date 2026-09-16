package com.golmok.market.domain.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 상품 목록의 썸네일을 한 번에 조회한다.
 *
 * 목록 응답마다 상품별로 이미지를 지연 로딩하면 N+1 이 된다.
 * 페이징이 끝난 상품 id 로만 IN 조회를 하고, sortOrder 가 가장 앞선 이미지를 썸네일로 쓴다.
 * 상품 목록과 찜 목록이 같은 규칙을 쓰도록 한곳에 둔다.
 */
@Component
@RequiredArgsConstructor
public class ProductThumbnails {

    private final ProductImageRepository productImageRepository;

    public Map<Long, String> of(List<Long> productIds) {
        Map<Long, String> thumbnails = new HashMap<>();
        if (productIds.isEmpty()) {
            return thumbnails;
        }
        // 쿼리가 product_id, sort_order, id 순으로 정렬돼 오므로 먼저 담긴 것이 썸네일이다.
        productImageRepository.findOrderedByProductIds(productIds)
                .forEach(image -> thumbnails.putIfAbsent(image.getProduct().getId(), image.getImageUrl()));
        return thumbnails;
    }
}
