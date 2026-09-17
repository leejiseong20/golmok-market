package com.golmok.market.domain.search;

import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.user.User;
import com.golmok.market.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 검색어를 그대로 쌓아두고, 인기 검색어는 집계 쿼리로 뽑는다.
 * 비로그인 검색도 허용하므로 user 는 null 일 수 있다.
 * 검색어는 저장 전에 SearchKeywords.normalize 로 맞춘다(같은 검색어가 공백·대소문자 차이로 갈라지지 않게).
 */
@Entity
@Getter
@Table(name = "search_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchLog extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    @Column(nullable = false, length = 50)
    private String keyword;

    private SearchLog(User user, Region region, String keyword) {
        this.user = user;
        this.region = region;
        this.keyword = keyword;
    }

    /** @param keyword 정규화된 검색어 */
    public static SearchLog of(User user, Region region, String keyword) {
        return new SearchLog(user, region, keyword);
    }
}
