package com.golmok.market.domain.product;

import com.golmok.market.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "product_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    /** 0 이면 대표 이미지 */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private ProductImage(Product product, String imageUrl, int sortOrder) {
        this.product = product;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    static ProductImage of(Product product, String imageUrl, int sortOrder) {
        return new ProductImage(product, imageUrl, sortOrder);
    }

    public boolean isThumbnail() {
        return this.sortOrder == 0;
    }
}
