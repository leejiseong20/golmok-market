package com.golmok.market.domain.user;

import com.golmok.market.domain.product.FavoriteService;
import com.golmok.market.domain.product.dto.ProductSummaryResponse;
import com.golmok.market.domain.trade.TradeService;
import com.golmok.market.domain.trade.dto.PurchaseResponse;
import com.golmok.market.domain.user.dto.MyProfileResponse;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final FavoriteService favoriteService;
    private final TradeService tradeService;

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

    /** 내 구매내역. 커서는 거래 생성 시각 기준이다. */
    @GetMapping("/me/purchases")
    public CursorResponse<PurchaseResponse> findMyPurchases(
            @AuthenticationPrincipal AuthUser viewer,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return tradeService.findMyPurchases(viewer, cursor, size);
    }
}
