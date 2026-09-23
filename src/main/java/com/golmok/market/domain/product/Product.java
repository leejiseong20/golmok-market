package com.golmok.market.domain.product;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.user.User;
import com.golmok.market.global.entity.BaseTimeEntity;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    private static final int BUMP_COOLDOWN_HOURS = 24;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id")
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    /** 대표 동네 변경에는 영향받지 않는다. 명시적 수정 시 인증한 동네로 옮길 수 있다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id")
    private Region region;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /** 0 = 나눔 */
    @Column(nullable = false)
    private int price;

    @Column(name = "is_negotiable", nullable = false)
    private boolean negotiable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false, length = 10)
    private TradeType tradeType;

    // 조회수는 원자적 벌크 UPDATE 전용이다. 오래된 엔티티 저장으로 증가분을 덮어쓰지 않는다.
    @Column(name = "view_count", nullable = false, updatable = false)
    private int viewCount;

    // 조회수와 마찬가지로 원자적 벌크 UPDATE 전용이다.
    @Column(name = "favorite_count", nullable = false, updatable = false)
    private int favoriteCount;

    // 채팅방 수도 원자적 벌크 UPDATE 전용이다.
    @Column(name = "chat_count", nullable = false, updatable = false)
    private int chatCount;

    /** 최신순 정렬 기준. 끌어올리기를 하면 갱신된다. */
    @Column(name = "bumped_at", nullable = false)
    private LocalDateTime bumpedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 이미지만 양방향으로 둔다. 상품 없이 이미지가 존재할 이유가 없고,
     * 상세 화면에서 항상 함께 조회되기 때문.
     * 나머지 연관관계(찜, 채팅, 거래)는 전부 단방향이다.
     */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductImage> images = new ArrayList<>();

    @Builder
    private Product(User seller, Category category, Region region,
                    String title, String description, int price,
                    boolean negotiable, TradeType tradeType, LocalDateTime bumpedAt) {
        this.seller = seller;
        this.category = category;
        this.region = region;
        this.title = title;
        this.description = description;
        this.price = price;
        this.negotiable = negotiable;
        this.tradeType = tradeType != null ? tradeType : TradeType.DIRECT;
        this.status = ProductStatus.ON_SALE;
        this.bumpedAt = bumpedAt != null ? bumpedAt : LocalDateTime.now();
    }

    // ---------- 이미지 ----------

    public void addImage(String imageUrl) {
        ProductImage image = ProductImage.of(this, imageUrl, this.images.size());
        this.images.add(image);
    }

    public void replaceImages(List<String> imageUrls) {
        this.images.clear();
        for (int i = 0; i < imageUrls.size(); i++) {
            this.images.add(ProductImage.of(this, imageUrls.get(i), i));
        }
    }

    public String getThumbnailUrl() {
        return this.images.isEmpty() ? null : this.images.get(0).getImageUrl();
    }

    // ---------- 수정 ----------

    public void update(String title, String description, int price,
                       Category category, Region region, boolean negotiable, TradeType tradeType) {
        if (this.status == ProductStatus.SOLD) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "판매완료 상품은 수정할 수 없습니다.");
        }
        this.title = title;
        this.description = description;
        this.price = price;
        this.category = category;
        this.region = region;
        this.negotiable = negotiable;
        this.tradeType = tradeType;
    }

    // ---------- 상태 전이 ----------
    // setStatus 를 두지 않는 이유: 아무 데서나 상태를 바꿀 수 있으면
    // "왜 이 상품이 SOLD 가 됐는지" 추적이 불가능해진다.

    public void reserve() {
        requireStatus(ProductStatus.ON_SALE, "판매중인 상품만 예약할 수 있습니다.");
        this.status = ProductStatus.RESERVED;
    }

    public void cancelReservation() {
        requireStatus(ProductStatus.RESERVED, "예약중인 상품만 예약을 취소할 수 있습니다.");
        this.status = ProductStatus.ON_SALE;
    }

    public void markSold() {
        if (this.status == ProductStatus.SOLD) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "이미 거래가 완료된 상품입니다.");
        }
        this.status = ProductStatus.SOLD;
    }

    /** 거래가 취소/환불되면 다시 판매중으로 되돌린다. */
    public void reopen() {
        this.status = ProductStatus.ON_SALE;
    }

    private void requireStatus(ProductStatus expected, String message) {
        if (this.status != expected) {
            throw new BusinessException(ErrorCode.INVALID_STATE, message);
        }
    }

    // ---------- 끌어올리기 ----------

    public void bump(LocalDateTime now) {
        if (this.status == ProductStatus.SOLD) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "판매완료 상품은 끌어올릴 수 없습니다.");
        }
        if (!canBump(now)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "끌어올리기는 24시간에 한 번만 가능합니다.");
        }
        this.bumpedAt = now;
    }

    public boolean canBump(LocalDateTime now) {
        return this.status != ProductStatus.SOLD && !now.isBefore(this.bumpedAt.plusHours(BUMP_COOLDOWN_HOURS));
    }

    // ---------- 카운터 ----------
    // COUNT(*) 대신 컬럼으로 들고 있는다. 목록 화면에서 상품마다
    // 집계 쿼리가 나가는 것을 막기 위한 의도적인 비정규화.

    // 찜 수·채팅방 수도 조회수와 같은 이유로 엔티티에서 증감하지 않는다.
    // 두 사람이 동시에 찜하면 메모리에서 ++ 한 값끼리 덮어써 하나가 유실된다.
    // ProductRepository 의 원자적 UPDATE 만 쓴다.

    // ---------- 삭제 ----------
    // 채팅방과 거래가 이 상품을 참조하므로 물리 삭제하면 안 된다.

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /** 관리자가 내린 상품을 되살린다. 누가 지웠는지는 조치 기록으로 판단하므로 호출하는 쪽이 확인한다. */
    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public boolean isOwnedBy(Long userId) {
        return this.seller.getId().equals(userId);
    }

    public boolean isFree() {
        return this.price == 0;
    }
}
