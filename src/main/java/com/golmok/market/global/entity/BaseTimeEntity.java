package com.golmok.market.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * created_at / updated_at 공통 처리.
 * DB에도 DEFAULT CURRENT_TIMESTAMP 가 걸려 있지만,
 * JPA가 값을 채워 INSERT 하므로 애플리케이션 기준 시각이 들어간다.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedTimeEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
