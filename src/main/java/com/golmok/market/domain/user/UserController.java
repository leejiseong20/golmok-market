package com.golmok.market.domain.user;

import com.golmok.market.domain.product.FavoriteService;
import com.golmok.market.domain.product.dto.ProductSummaryResponse;
import com.golmok.market.domain.trade.TradeService;
import com.golmok.market.domain.trade.dto.PurchaseResponse;
import com.golmok.market.domain.user.dto.MyProfileResponse;
import com.golmok.market.domain.user.dto.MyRegionResponse;
import com.golmok.market.domain.user.dto.RegionVerifyRequest;
import com.golmok.market.domain.user.dto.UserProfileResponse;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final FavoriteService favoriteService;
    private final TradeService tradeService;
    private final UserRegionService userRegionService;

    @GetMapping("/{id}")
    public UserProfileResponse findProfile(@PathVariable @Positive(message = "사용자 ID는 양수여야 합니다.") long id) {
        return userService.findProfile(id);
    }

    @GetMapping("/me")
    public MyProfileResponse findMe(@AuthenticationPrincipal AuthUser viewer) {
        return userService.findMe(viewer);
    }

    /** 찜 목록은 상품 목록과 같은 응답 형식이라 프론트가 같은 카드 컴포넌트를 재사용한다. */
    @GetMapping("/me/favorites")
    public CursorResponse<ProductSummaryResponse> findMyFavorites(
            @AuthenticationPrincipal AuthUser viewer,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return favoriteService.findMyFavorites(viewer, cursor, size);
    }

    // ---------- 동네 인증 ----------
    // 세 API 모두 갱신된 내 동네 목록 전체를 돌려준다.
    // 대표 동네 이동처럼 한 번의 요청이 여러 행을 바꾸므로, 화면이 부분 갱신을 조합하지 않아도 되게 한다.

    @PostMapping("/me/regions")
    public List<MyRegionResponse> verifyRegion(@AuthenticationPrincipal AuthUser viewer,
                                               @Valid @RequestBody RegionVerifyRequest request) {
        return userRegionService.verify(viewer, request.lat(), request.lng());
    }

    @DeleteMapping("/me/regions/{regionId}")
    public List<MyRegionResponse> deleteRegion(@AuthenticationPrincipal AuthUser viewer,
                                               @PathVariable @Positive(message = "동네 ID는 양수여야 합니다.") long regionId) {
        return userRegionService.delete(viewer, regionId);
    }

    @PatchMapping("/me/regions/{regionId}/primary")
    public List<MyRegionResponse> markPrimaryRegion(@AuthenticationPrincipal AuthUser viewer,
                                                    @PathVariable @Positive(message = "동네 ID는 양수여야 합니다.") long regionId) {
        return userRegionService.markPrimary(viewer, regionId);
    }

    /** 내 구매내역. 커서는 거래 생성 시각 기준이다. */
    @GetMapping("/me/purchases")
    public CursorResponse<PurchaseResponse> findMyPurchases(
            @AuthenticationPrincipal AuthUser viewer,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return tradeService.findMyPurchases(viewer, cursor, size);
    }
}
