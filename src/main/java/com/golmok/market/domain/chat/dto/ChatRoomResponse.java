package com.golmok.market.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.golmok.market.domain.chat.ChatRoom;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductStatus;
import com.golmok.market.domain.user.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 채팅방 화면 상단에 필요한 정보. 채팅하기 응답과 채팅방 조회 응답이 같다.
 *
 * buyer/seller 대신 "나를 기준으로 한 상대"를 내려준다. 프론트가 자기 id 와 비교해
 * 누가 상대인지 계산하지 않아도 되고, myRole 로 판매자에게만 보일 버튼(예약·거래완료)을 가를 수 있다.
 */
public record ChatRoomResponse(
        Long roomId,
        ProductInfo product,
        Opponent opponent,
        Role myRole,
        boolean opponentLeft,
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

    public static ChatRoomResponse of(ChatRoom room, Long viewerId, String thumbnailUrl) {
        return new ChatRoomResponse(
                room.getId(),
                ProductInfo.of(room.getProduct(), thumbnailUrl),
                Opponent.of(room.getOpponent(viewerId)),
                room.isBuyer(viewerId) ? Role.BUYER : Role.SELLER,
                room.hasOpponentLeft(viewerId),
                room.getCreatedAt());
    }
}
