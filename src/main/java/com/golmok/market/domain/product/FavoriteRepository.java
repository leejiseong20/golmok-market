package com.golmok.market.domain.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    @Query("select f.product.id from Favorite f where f.user.id = :userId and f.product.id in :productIds")
    List<Long> findLikedProductIds(long userId, Collection<Long> productIds);

    boolean existsByUserIdAndProductId(long userId, long productId);
}
