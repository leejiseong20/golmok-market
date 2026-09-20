package com.golmok.market.domain.product.dto;

import com.golmok.market.domain.product.TradeType;
import jakarta.validation.constraints.*;
import java.util.List;

/**
 * 상품 등록·수정 요청.
 *
 * 제약마다 메시지를 직접 적는다. 기본 메시지는 사용자용이 아니다.
 * 예를 들어 {@code @Size(min = 10)} 의 기본 문구는 "크기가 10에서 2147483647 사이여야 합니다" 인데,
 * 2147483647 은 Integer.MAX_VALUE(= 위쪽 제한 없음)일 뿐이라 읽는 사람에게 아무 의미가 없다.
 * 프론트는 서버가 준 message 를 그대로 보여 주므로, 사람이 읽을 문장은 여기서 만들어야 한다.
 */
public record ProductWriteRequest(
        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(min = 2, max = 100, message = "제목은 2자 이상 100자 이하로 입력해 주세요.")
        String title,

        @NotBlank(message = "설명을 입력해 주세요.")
        @Size(min = 10, message = "설명은 10자 이상 입력해 주세요.")
        String description,

        @NotNull(message = "가격을 입력해 주세요.")
        @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
        Integer price,

        @NotNull(message = "카테고리를 선택해 주세요.")
        @Positive(message = "카테고리를 선택해 주세요.")
        Long categoryId,

        @NotNull(message = "거래 동네를 선택해 주세요.")
        @Positive(message = "거래 동네를 선택해 주세요.")
        Long regionId,

        @NotNull(message = "가격 제안 가능 여부를 지정해 주세요.")
        Boolean isNegotiable,

        @NotNull(message = "거래 방식을 선택해 주세요.")
        TradeType tradeType,

        @NotNull(message = "사진을 1장 이상 올려 주세요.")
        @Size(min = 1, max = 10, message = "사진은 1장 이상 10장까지 올릴 수 있습니다.")
        List<@NotBlank(message = "사진 주소가 비어 있습니다.")
             @Size(max = 500, message = "사진 주소가 너무 깁니다.") String> imageUrls
) {
    public ProductWriteRequest {
        title = title == null ? null : title.trim();
        description = description == null ? null : description.trim();
    }
}
