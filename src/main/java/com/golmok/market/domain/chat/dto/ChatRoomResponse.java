package com.golmok.market.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.golmok.market.domain.chat.ChatRoom;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductStatus;
import com.golmok.market.domain.trade.Trade;
import com.golmok.market.domain.trade.TradeStatus;
import com.golmok.market.domain.user.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 채팅방 화면 상단에 필요한 정보. 채팅하기·채팅방 조회·예약/취소/거래완료 응답이 모두 같다.
 *
 * buyer/seller 대신 "나를 기준으로 한 상대"를 내려준다. 프론트가 자기 id 와 비교해
 * 누가 상대인지 계산하지 않아도 된다.
 *
 * tradeActions 는 지금 누를 수 있는 거래 버튼을 서버가 계산한 값이다(구매내역의 canConfirm 과 같은 이유).
 * 프론트가 상태·역할 조합을 따로 들고 있으면 규칙이 두 곳으로 갈라진다.
 */
public record ChatRoomResponse(
        Long roomId,
        ProductInfo product,
        Opponent opponent,
        Role myRole,
        boolean opponentLeft,
        TradeInfo trade,
        TradeActions tradeActions,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt
) {

    public enum Role { BUYER, SELLER }

    /** 판매완료·삭제된 상품의 방도 대화는 남으므로 status 와 deleted 를 함께 내려 화면에서 구분한다. */
    public record ProductInfo(Long id, String title, int price, String thumbnailUrl,
                              ProductStatus status, boolean deleted) {

        public static ProductInfo of(Product product, String thumbnailUrl) {
            return new ProductInfo(product.getId(), product.getTitle(), product.getPrice(), thumbnailUrl,
                    product.getStatus(), product.isDeleted());
        }
    }

    public record Opponent(Long id, String nickname, BigDecimal mannerTemp) {

        public static Opponent of(User user) {
            return new Opponent(user.getId(), user.getNickname(), user.getMannerTemp());
        }
    }

    /** 이 방의 거래(진행 중이거나 완료). 없거나 취소됐으면 응답에서 null 이다. */
    public record TradeInfo(Long id, TradeStatus status, int amount,
                            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime completedAt) {

        public static TradeInfo of(Trade trade) {
            return trade == null ? null
                    : new TradeInfo(trade.getId(), trade.getStatus(), trade.getAmount(), trade.getCompletedAt());
        }
    }

    /**
     * @param reserve  판매자 · 이 방에 거래 없음 · 상품이 판매중(다른 방에 예약이 있으면 상품이 예약중이라 false)
     * @param cancel   판매자·구매자 · 이 방의 거래가 예약(REQUESTED)
     * @param complete 판매자 · 이 방의 거래가 예약(REQUESTED)
     */
    public record TradeActions(boolean reserve, boolean cancel, boolean complete) {

        public static TradeActions of(ChatRoom room, Trade trade, Long viewerId) {
            boolean seller = !room.isBuyer(viewerId);
            Product product = room.getProduct();
            boolean reserved = trade != null && trade.getStatus() == TradeStatus.REQUESTED;
            return new TradeActions(
                    seller && trade == null && !product.isDeleted() && product.getStatus() == ProductStatus.ON_SALE,
                    reserved,
                    seller && reserved);
        }
    }

    public static ChatRoomResponse of(ChatRoom room, Long viewerId, String thumbnailUrl, Trade trade) {
        return new ChatRoomResponse(
                room.getId(),
                ProductInfo.of(room.getProduct(), thumbnailUrl),
                Opponent.of(room.getOpponent(viewerId)),
                room.isBuyer(viewerId) ? Role.BUYER : Role.SELLER,
                room.hasOpponentLeft(viewerId),
                TradeInfo.of(trade),
                TradeActions.of(room, trade, viewerId),
                room.getCreatedAt());
    }
}
