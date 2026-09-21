package com.golmok.market.global.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * MySQL 의 {@code MATCH(...) AGAINST(... IN BOOLEAN MODE)} 를 JPA Criteria 에서 쓰기 위한 함수 등록.
 *
 * 등록하지 않으면 Hibernate 는 모르는 함수를 {@code match_against(a, b, c)} 처럼 그대로 그려 MySQL 이 거부한다.
 * META-INF/services 로 Hibernate 가 직접 찾아 불러온다(스프링 빈이 아니다).
 *
 * 모든 방언에 등록하지만 호출은 MySQL 에서만 한다(ProductRepositoryImpl). H2 에는 이 문법이 없다.
 * 결과는 관련도 점수(0 이면 불일치)라 Double 로 받는다.
 */
public class SearchFunctionContributor implements FunctionContributor {

    public static final String MATCH_AGAINST = "match_against";

    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        contributions.getFunctionRegistry().registerPattern(
                MATCH_AGAINST,
                "match(?1, ?2) against(?3 in boolean mode)",
                contributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.DOUBLE));
    }
}
