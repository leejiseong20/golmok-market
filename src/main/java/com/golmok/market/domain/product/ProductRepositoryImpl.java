package com.golmok.market.domain.product;

import com.golmok.market.domain.block.Block;
import com.golmok.market.global.config.SearchFunctionContributor;
import com.golmok.market.global.pagination.Cursor;
import com.golmok.market.global.pagination.PageSize;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 선택 필터 조합에 필요한 조건만 구성한다. 추가 쿼리 라이브러리 없이 JPA Criteria 를 사용한다. */
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final EntityManager em;

    /** MySQL 인가(FULLTEXT 를 쓸 수 있는가). 방언은 기동 중 바뀌지 않으므로 한 번만 확인한다. */
    private Boolean fullTextAvailable;

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

    /**
     * 검색어 조건. MySQL 에서는 FULLTEXT(ngram) 인덱스로, 그 밖에서는 LIKE 로 찾는다.
     *
     * LIKE '%식탁%' 은 앞에 % 가 붙어 B-tree 인덱스를 못 쓰고 제목·본문(TEXT) 전체를 읽는다.
     * 스키마에 ft_products_search 인덱스가 있었지만 쿼리가 쓰지 않고 있었다.
     *
     * LIKE 로 찾는 경우:
     *   - 1글자 단어가 섞였을 때. ngram 색인 단위가 2글자라 FULLTEXT 로는 1글자를 찾을 수 없다.
     *   - MySQL 이 아닐 때(테스트의 H2). H2 에는 MATCH AGAINST 가 없다. 그래서 FULLTEXT 쪽 동작은
     *     H2 테스트로 검증되지 않으며 실제 MySQL 로 따로 확인했다.
     */
    private KeywordFilter keywordCondition(CriteriaBuilder cb, Root<Product> product, String keyword) {
        var booleanQuery = ProductSearchQuery.booleanQuery(keyword);
        if (booleanQuery.isPresent() && fullTextAvailable()) {
            /*
             * 검색 식은 반드시 바인딩 변수로 넘긴다. cb.literal() 을 쓰면 Hibernate 7 은 값을 SQL 문자열에
             * 그대로 적는다(실제 MySQL 로그로 확인: against('+"ipad"' in boolean mode)). 사용자 입력이 SQL 에
             * 박히는 구조이고, 검색어마다 다른 SQL 이 만들어져 문장 캐시도 흐트러진다.
             * 관련도 점수가 0 보다 크면 일치다.
             */
            ParameterExpression<String> expression = cb.parameter(String.class);
            Predicate matched = cb.greaterThan(cb.function(SearchFunctionContributor.MATCH_AGAINST, Double.class,
                    product.get("title"), product.get("description"), expression), 0.0);
            return new KeywordFilter(matched, expression, booleanQuery.get());
        }
        String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return new KeywordFilter(
                cb.or(cb.like(product.get("title"), pattern, '!'), cb.like(product.get("description"), pattern, '!')),
                null, null);
    }

    /** 검색어 조건과, 조건에 바인딩할 값(LIKE 는 cb.like 가 알아서 바인딩하므로 없다). */
    private record KeywordFilter(Predicate predicate, ParameterExpression<String> parameter, String value) {
    }

    private boolean fullTextAvailable() {
        if (fullTextAvailable == null) {
            Dialect dialect = em.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class)
                    .getJdbcServices().getDialect();
            fullTextAvailable = dialect instanceof MySQLDialect;
        }
        return fullTextAvailable;
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
        KeywordFilter keywordFilter = null;
        if (keyword != null && !keyword.isBlank()) {
            keywordFilter = keywordCondition(cb, product, keyword);
            conditions.add(keywordFilter.predicate());
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
        TypedQuery<Product> typed = em.createQuery(query).setMaxResults(pageSize.fetchSize());
        if (keywordFilter != null && keywordFilter.parameter() != null) {
            typed.setParameter(keywordFilter.parameter(), keywordFilter.value());
        }
        return typed.getResultList();
    }
}
