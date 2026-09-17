package com.golmok.market.domain.trade;

import com.golmok.market.domain.trade.dto.ReviewCreateRequest;
import com.golmok.market.domain.trade.dto.ReviewResponse;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/api/trades/{tradeId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse write(@PathVariable @Positive(message = "거래 ID는 양수여야 합니다.") long tradeId,
                                @AuthenticationPrincipal AuthUser viewer,
                                @Valid @RequestBody ReviewCreateRequest request) {
        return reviewService.write(tradeId, viewer, request);
    }

    @GetMapping("/api/users/{id}/reviews")
    public CursorResponse<ReviewResponse> findReceived(@PathVariable @Positive(message = "사용자 ID는 양수여야 합니다.") long id,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(required = false) Integer size) {
        return reviewService.findReceived(id, cursor, size);
    }
}
