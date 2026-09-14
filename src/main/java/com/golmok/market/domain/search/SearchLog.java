package com.golmok.market.domain.search;

import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 검색어를 그대로 쌓아두고, 인기 검색어는 집계 쿼리로 뽑는다.
 * 비로그인 검색도 허용하므로 user 는 null 일 수 있다.
 */
@Entity
@Getter
@Table(name = "search_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchLog {

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

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    private SearchLog(User user, Region region, String keyword) {
        this.user = user;
        this.region = region;
        this.keyword = keyword;
    }

    public static SearchLog of(User user, Region region, String keyword) {
        return new SearchLog(user, region, keyword.trim());
    }
}
