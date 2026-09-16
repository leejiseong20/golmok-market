package com.golmok.market.domain.user;

import com.golmok.market.domain.region.Region;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 동네 인증 내역. 1인 최대 2개까지 허용하는 정책은 서비스 레이어에서 검사한다.
 */
@Entity
@Getter
// 같은 동네를 두 번 등록할 수 없다. 재인증은 새 행이 아니라 verifyCount 증가다.
@Table(name = "user_regions", uniqueConstraints =
        @UniqueConstraint(name = "uk_user_region", columnNames = {"user_id", "region_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id")
    private Region region;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "verify_count", nullable = false)
    private int verifyCount;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    private UserRegion(User user, Region region, boolean primary) {
        this.user = user;
        this.region = region;
        this.primary = primary;
        this.verifyCount = 1;
        this.verifiedAt = LocalDateTime.now();
    }

    public static UserRegion verify(User user, Region region, boolean primary) {
        return new UserRegion(user, region, primary);
    }

    /** 같은 동네에서 재인증. 횟수가 쌓이면 노출 반경을 넓혀줄 수 있다. */
    public void reVerify() {
        this.verifyCount++;
        this.verifiedAt = LocalDateTime.now();
    }

    public void markPrimary(boolean primary) {
        this.primary = primary;
    }
}
