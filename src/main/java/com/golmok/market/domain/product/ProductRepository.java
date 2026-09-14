package com.golmok.market.domain.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

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
}
