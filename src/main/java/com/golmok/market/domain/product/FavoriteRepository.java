package com.golmok.market.domain.product;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    @Query("select f.product.id from Favorite f where f.user.id = :userId and f.product.id in :productIds")
    List<Long> findLikedProductIds(long userId, Collection<Long> productIds);

    /** 가격 인하 알림을 받을 사람. */
    @Query("select f.user.id from Favorite f where f.product.id = :productId")
    List<Long> findUserIdsByProductId(long productId);

    boolean existsByUserIdAndProductId(long userId, long productId);

    Optional<Favorite> findByUserIdAndProductId(long userId, long productId);

    /**
     * 내 찜 목록 첫 페이지. 최근에 찜한 순이다.
     *
     * 커서를 (찜한 시각, 찜 id)로 잡는 이유: 같은 초에 여러 개를 찜할 수 있어
     * 시각만으로는 경계가 모호하다. 상품 목록과 정렬 기준 자체가 다르므로 커서 값도 다르다.
     *
     * 삭제된 상품은 제외한다. 찜은 남아 있어도 목록에 보여줄 상품이 없기 때문이다.
     */
    @Query("""
            select f from Favorite f
            join fetch f.product p
            join fetch p.seller
            join fetch p.category
            join fetch p.region
            where f.user.id = :userId and p.deletedAt is null
            order by f.createdAt desc, f.id desc
            """)
    List<Favorite> findFirstPage(long userId, Pageable pageable);

    /** 커서 이후 페이지. 조건을 null 파라미터로 분기하지 않고 쿼리를 나눠 둔다. */
    @Query("""
            select f from Favorite f
            join fetch f.product p
            join fetch p.seller
            join fetch p.category
            join fetch p.region
            where f.user.id = :userId and p.deletedAt is null
              and (f.createdAt < :cursorTime or (f.createdAt = :cursorTime and f.id < :cursorId))
            order by f.createdAt desc, f.id desc
            """)
    List<Favorite> findNextPage(long userId, LocalDateTime cursorTime, long cursorId, Pageable pageable);
}
