package com.golmok.market.domain.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {
    long countBySellerIdAndDeletedAtIsNull(long sellerId);

    // 삭제된 상품도 거래 기록에서는 필요하므로 삭제 여부는 호출자가 판단한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(long id);

    @Query("""
            select p from Product p
            join fetch p.seller join fetch p.category join fetch p.region
            where p.id = :id and p.deletedAt is null
            """)
    Optional<Product> findVisibleById(long id);

    /**
     * 엔티티 ++ 대신 DB 가 현재 값에 더해 동시 요청의 증가분을 보존한다.
     * 벌크 갱신 후 영속성 컨텍스트를 비워 상세 응답에도 새 값을 읽는다.
     * updatedAt 자기 대입은 MySQL ON UPDATE 로 조회만 해도 수정 시각이 바뀌는 것을 막는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Product p set p.viewCount = p.viewCount + 1, p.updatedAt = p.updatedAt
            where p.id = :id and p.deletedAt is null
            and (:viewerId is null or p.seller.id <> :viewerId)
            """)
    int incrementViewCount(long id, Long viewerId);

    /** 조회수와 같은 이유로 DB 에서 더한다. 동시에 찜해도 증가분이 유실되지 않는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Product p set p.favoriteCount = p.favoriteCount + 1, p.updatedAt = p.updatedAt
            where p.id = :id
            """)
    int incrementFavoriteCount(long id);

    /** 0 미만으로 내려가지 않게 DB 에서 막는다. 중복 취소가 와도 음수가 되지 않는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Product p
            set p.favoriteCount = case when p.favoriteCount > 0 then p.favoriteCount - 1 else 0 end,
                p.updatedAt = p.updatedAt
            where p.id = :id
            """)
    int decrementFavoriteCount(long id);

    /**
     * 채팅방이 새로 생겼을 때만 호출한다. 찜 수와 같은 이유로 DB 에서 더한다.
     * 영속성 컨텍스트를 비우지 않는다. 호출한 쪽이 잠근 상품으로 응답을 만들어야 하고,
     * chat_count 는 updatable = false 라 남아 있는 엔티티가 증가분을 덮어쓰지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Product p set p.chatCount = p.chatCount + 1, p.updatedAt = p.updatedAt
            where p.id = :id
            """)
    int incrementChatCount(long id);

    /** 증감 직후의 값만 필요할 때. 상품 전체를 다시 읽지 않는다. */
    @Query("select p.favoriteCount from Product p where p.id = :id")
    int findFavoriteCount(long id);
}
