package com.golmok.market.domain.category;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 계층 구조용. 지금은 전부 최상위(null)지만 확장 여지를 남겨둔다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(nullable = false, length = 50, unique = true)
    private String name;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 마스터 데이터 생성. 생성 후 변경은 setter 대신 별도 정책으로 다룬다. */
    public static Category create(Category parent, String name, String iconUrl, int sortOrder) {
        Category category = new Category();
        category.parent = parent;
        category.name = name;
        category.iconUrl = iconUrl;
        category.sortOrder = sortOrder;
        return category;
    }

    public boolean isRoot() {
        return this.parent == null;
    }
}
