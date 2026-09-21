package com.golmok.market.domain.product;

import com.golmok.market.global.pagination.Cursor;
import com.golmok.market.global.pagination.PageSize;

import java.util.List;

public interface ProductRepositoryCustom {

    /**
     * @param viewerId 보는 사람. 이 사람이 차단한 판매자의 상품을 뺀다(비로그인이면 null 이라 아무도 빼지 않는다).
     *                 목록에서만 뺀다. 주소로 상세에 들어오면 상품은 보인다(차단은 숨김이 아니다).
     */
    List<Product> findPage(long regionId, Long categoryId, String keyword, ProductSort sort,
                           Cursor cursor, PageSize pageSize, Long viewerId);
    List<Product> findMyPage(long sellerId, ProductStatus status, Cursor cursor, PageSize pageSize);
}
