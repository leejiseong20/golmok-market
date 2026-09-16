package com.golmok.market.domain.product;

import com.golmok.market.domain.product.dto.FavoriteResponse;
import com.golmok.market.global.security.AuthUser;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/{id}/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping
    public FavoriteResponse favorite(@PathVariable @Positive(message = "상품 ID는 양수여야 합니다.") long id,
                                     @AuthenticationPrincipal AuthUser viewer) {
        return favoriteService.favorite(id, viewer);
    }

    @DeleteMapping
    public FavoriteResponse unfavorite(@PathVariable @Positive(message = "상품 ID는 양수여야 합니다.") long id,
                                       @AuthenticationPrincipal AuthUser viewer) {
        return favoriteService.unfavorite(id, viewer);
    }
}
