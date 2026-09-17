package com.golmok.market.domain.trade;

import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.product.ProductThumbnails;
import com.golmok.market.domain.trade.dto.PurchaseResponse;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.pagination.Cursor;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.pagination.PageSize;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 구매내역 조회와 구매확정.
 *
 * 직거래 생성·완료는 ChatTradeService 가 담당하고, 여기서는 구매자의 기록과 구매확정을 다룬다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeService {

    private final TradeRepository tradeRepository;
    private final ProductThumbnails productThumbnails;
    private final ProductRepository productRepository;
    private final ReviewService reviewService;

    public CursorResponse<PurchaseResponse> findMyPurchases(AuthUser viewer, String rawCursor, Integer size) {
        Cursor cursor = Cursor.parse(rawCursor);
        PageSize pageSize = PageSize.of(size);
        Pageable limit = PageRequest.of(0, pageSize.fetchSize());

        List<Trade> fetched = cursor == null
                ? tradeRepository.findPurchases(viewer.id(), limit)
                : tradeRepository.findPurchasesAfter(viewer.id(), cursor.valueAsDateTime(), cursor.id(), limit);

        CursorResponse<Trade> page = CursorResponse.of(fetched, pageSize,
                trade -> Cursor.of(trade.getCreatedAt(), trade.getId()));
        Map<Long, String> thumbnails = productThumbnails.of(
                page.content().stream().map(trade -> trade.getProduct().getId()).toList());

        Set<Long> reviewed = reviewService.reviewedTradeIds(viewer.id(),
                page.content().stream().map(Trade::getId).toList());
        return page.map(trade -> PurchaseResponse.from(trade, thumbnails.get(trade.getProduct().getId()),
                trade.getStatus() == TradeStatus.CONFIRMED && !reviewed.contains(trade.getId())));
    }

    /**
     * 구매확정. 구매자만 할 수 있다.
     *
     * 판매자가 대신 확정하면 "물건을 받았다"는 사실을 판매자가 선언하는 셈이라 의미가 없다.
     * 상태 전이 가능 여부(결제 완료·발송 상태에서만)는 Trade 가 판단한다.
     */
    @Transactional
    public PurchaseResponse confirm(long tradeId, AuthUser viewer) {
        long productId = tradeRepository.findProductIdForBuyer(tradeId, viewer.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_NOT_FOUND));
        productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        Trade trade = tradeRepository.findByIdForUpdate(tradeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADE_NOT_FOUND));
        if (!trade.getBuyer().getId().equals(viewer.id())) {
            // 남의 거래인지 알려주지 않기 위해 403 대신 404 로 답한다.
            throw new BusinessException(ErrorCode.TRADE_NOT_FOUND);
        }
        trade.confirm();
        return PurchaseResponse.from(trade,
                productThumbnails.of(List.of(trade.getProduct().getId())).get(trade.getProduct().getId()),
                reviewService.canReview(trade, viewer.id()));
    }
}
