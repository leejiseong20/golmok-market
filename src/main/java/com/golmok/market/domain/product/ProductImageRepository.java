package com.golmok.market.domain.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    @Query("""
            select i from ProductImage i where i.product.id in :productIds
            order by i.product.id asc, i.sortOrder asc, i.id asc
            """)
    List<ProductImage> findOrderedByProductIds(Collection<Long> productIds);

    /**
     * 주어진 주소 중 **아직 상품이 가리키고 있는 것**만 돌려준다(고아 사진 정리).
     * 파일을 한 장씩 조회하면 파일 수만큼 쿼리가 나가므로 묶어서 한 번에 묻는다.
     * 삭제한 상품(soft delete)의 사진도 행이 남아 있어 여기에 포함된다 — 지우면 안 되는 쪽이다.
     */
    @Query("select i.imageUrl from ProductImage i where i.imageUrl in :urls")
    List<String> findExistingUrls(Collection<String> urls);
}
