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
}
