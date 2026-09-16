package com.golmok.market.domain.product;

import com.golmok.market.global.pagination.Cursor;
import com.golmok.market.global.pagination.PageSize;

import java.util.List;

public interface ProductRepositoryCustom {

    List<Product> findPage(long regionId, Long categoryId, String keyword, ProductSort sort, Cursor cursor, PageSize pageSize);
    List<Product> findMyPage(long sellerId, ProductStatus status, Cursor cursor, PageSize pageSize);
}
