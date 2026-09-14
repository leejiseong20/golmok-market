package com.golmok.market.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * created_at 만 있는 테이블용. updated_at 까지 있으면 {@link BaseTimeEntity}.
 *
 * DB 의 DEFAULT CURRENT_TIMESTAMP 에 맡기지(insertable = false) 않고 JPA 가 채우는 이유:
 * - DB 기본값에 맡기면 INSERT 직후 엔티티의 createdAt 이 null 이다. 저장 직후 응답에 쓰면 null 이 나간다.
 * - 테스트용 H2 는 엔티티 기준으로 테이블을 만들어 DB 기본값이 없다. NOT NULL 위반으로 INSERT 가 실패한다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseCreatedTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
