package com.golmok.market.domain.product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 관리자 화면이 쓰는 상품 조회. 일반 목록과 달리 **삭제한 상품도 본다**(내린 상품을 되살려야 하므로).
 */
public interface AdminProductRepository extends JpaRepository<Product, Long> {

    /**
     * 최신 등록순 한 페이지. 판매자·제목·삭제 여부로 거른다(null 이면 거르지 않음).
     * 제목 검색어의 %·_ 는 호출하는 쪽이 '!' 로 이스케이프해 넘긴다. 부분 일치라 인덱스를 못 타지만 관리자 전용이다.
     */
    @Query("""
            select p from Product p join fetch p.seller
            where (:sellerId is null or p.seller.id = :sellerId)
              and (:title is null or p.title like concat('%', :title, '%') escape '!')
              and (:deleted is null
                   or (:deleted = true and p.deletedAt is not null)
                   or (:deleted = false and p.deletedAt is null))
              and (:cursorId is null or p.id < :cursorId)
            order by p.id desc
            """)
    List<Product> findPage(Long sellerId, String title, Boolean deleted, Long cursorId, Pageable pageable);

    /** 삭제 여부와 상관없이 읽는다. 상세에 판매자 닉네임을 함께 보여 준다. */
    @Query("select p from Product p join fetch p.seller where p.id = :id")
    Optional<Product> findWithSeller(long id);

    /** 지금 보이는(삭제하지 않은) 판매 상품 수. idx_products_seller 를 탄다. */
    @Query("select count(p) from Product p where p.seller.id = :sellerId and p.deletedAt is null")
    long countVisibleBySeller(long sellerId);

    long countByCreatedAtGreaterThanEqual(LocalDateTime from);
}
