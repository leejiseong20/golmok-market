package com.golmok.market.domain.trade;

import com.golmok.market.domain.trade.dto.PurchaseResponse;
import com.golmok.market.global.security.AuthUser;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    /** 구매확정. 구매내역 한 건을 갱신해 돌려주므로 목록을 다시 부르지 않아도 된다. */
    @PatchMapping("/api/trades/{id}/confirm")
    public PurchaseResponse confirm(@PathVariable @Positive(message = "거래 ID는 양수여야 합니다.") long id,
                                    @AuthenticationPrincipal AuthUser viewer) {
        return tradeService.confirm(id, viewer);
    }
}
