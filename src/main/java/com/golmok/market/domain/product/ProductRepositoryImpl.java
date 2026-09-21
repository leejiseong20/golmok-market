package com.golmok.market.domain.product;

import com.golmok.market.domain.block.Block;
import com.golmok.market.global.pagination.Cursor;
import com.golmok.market.global.pagination.PageSize;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 선택 필터 조합에 필요한 조건만 구성한다. 추가 쿼리 라이브러리 없이 JPA Criteria 를 사용한다. */
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final EntityManager em;

    @Override
    public List<Product> findMyPage(long sellerId, ProductStatus status, Cursor cursor, PageSize pageSize) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Product> query = cb.createQuery(Product.class);
        Root<Product> product = query.from(Product.class);
        product.fetch("seller");
        product.fetch("category");
        product.fetch("region");
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(cb.equal(product.get("seller").get("id"), sellerId));
        conditions.add(cb.isNull(product.get("deletedAt")));
        if (status != null) conditions.add(cb.equal(product.get("status"), status));
        if (cursor != null) {
            LocalDateTime time = cursor.valueAsDateTime();
            conditions.add(cb.or(cb.lessThan(product.get("createdAt"), time),
                    cb.and(cb.equal(product.get("createdAt"), time), cb.lessThan(product.get("id"), cursor.id()))));
        }
        query.select(product).where(conditions.toArray(Predicate[]::new))
                .orderBy(cb.desc(product.get("createdAt")), cb.desc(product.get("id")));
        return em.createQuery(query).setMaxResults(pageSize.fetchSize()).getResultList();
    }

    @Override
    public List<Product> findPage(long regionId, Long categoryId, String keyword, ProductSort sort,
                                  Cursor cursor, PageSize pageSize, Long viewerId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Product> query = cb.createQuery(Product.class);
        Root<Product> product = query.from(Product.class);
        // 단일 연관만 fetch 한다. 컬렉션 fetch + limit 는 메모리 페이징을 유발할 수 있다.
        product.fetch("seller", JoinType.INNER);
        product.fetch("category", JoinType.INNER);
        product.fetch("region", JoinType.INNER);
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(cb.isNull(product.get("deletedAt")));
        conditions.add(cb.equal(product.get("region").get("id"), regionId));
        if (categoryId != null) {
            conditions.add(cb.equal(product.get("category").get("id"), categoryId));
        }
        /*
         * 차단한 판매자의 상품을 뺀다.
         * 차단 id 를 먼저 조회해 IN 으로 거르지 않는 이유: 목록 조회 쿼리가 한 번 늘고,
         * 차단이 많은 계정에서는 IN 목록이 그만큼 커진다. 상관 서브쿼리면 둘 다 없다.
         */
        if (viewerId != null) {
            Subquery<Integer> blocked = query.subquery(Integer.class);
            Root<Block> block = blocked.from(Block.class);
            blocked.select(cb.literal(1)).where(
                    cb.equal(block.get("blocker").get("id"), viewerId),
                    cb.equal(block.get("blocked").get("id"), product.get("seller").get("id")));
            conditions.add(cb.not(cb.exists(blocked)));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            conditions.add(cb.or(cb.like(product.get("title"), pattern, '!'),
                    cb.like(product.get("description"), pattern, '!')));
        }
        if (sort == ProductSort.LATEST) {
            query.orderBy(cb.desc(product.get("bumpedAt")), cb.desc(product.get("id")));
            if (cursor != null) {
                LocalDateTime time = cursor.valueAsDateTime();
                conditions.add(cb.or(cb.lessThan(product.get("bumpedAt"), time),
                        cb.and(cb.equal(product.get("bumpedAt"), time), cb.lessThan(product.get("id"), cursor.id()))));
            }
        } else {
            query.orderBy(cb.asc(product.get("price")), cb.asc(product.get("id")));
            if (cursor != null) {
                int price = (int) cursor.valueAsLong();
                conditions.add(cb.or(cb.greaterThan(product.get("price"), price),
                        cb.and(cb.equal(product.get("price"), price), cb.greaterThan(product.get("id"), cursor.id()))));
            }
        }
        query.select(product).where(conditions.toArray(Predicate[]::new));
        return em.createQuery(query).setMaxResults(pageSize.fetchSize()).getResultList();
    }
}
