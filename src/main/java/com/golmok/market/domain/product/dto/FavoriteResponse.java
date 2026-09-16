package com.golmok.market.domain.product.dto;

/**
 * 찜 토글 결과. 프론트가 카드의 하트와 찜 수를 바로 갱신할 수 있도록
 * 두 값을 함께 내려준다. (목록을 다시 불러오지 않아도 된다)
 */
public record FavoriteResponse(boolean isLiked, int favoriteCount) {
}
