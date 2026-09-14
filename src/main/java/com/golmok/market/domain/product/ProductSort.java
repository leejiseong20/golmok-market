package com.golmok.market.domain.product;

import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.pagination.Cursor;

public enum ProductSort {
    LATEST,
    PRICE_ASC;

    public void validateCursor(Cursor cursor) {
        if (cursor == null) {
            return;
        }
        if (this == LATEST) {
            int year = cursor.valueAsDateTime().getYear();
            // MySQL DATETIME 이 지원하는 범위 밖의 날짜를 DB 오류로 넘기지 않는다.
            if (year < 1000 || year > 9999) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "cursor 날짜 범위가 올바르지 않습니다.");
            }
        } else {
            long price = cursor.valueAsLong();
            if (price < 0 || price > Integer.MAX_VALUE) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "cursor 가격 범위가 올바르지 않습니다.");
            }
        }
    }

    public Cursor cursorOf(Product product) {
        // 응답 날짜 표시 형식과 별개로 DB 에서 읽은 정밀도를 그대로 유지한다.
        return this == LATEST ? Cursor.of(product.getBumpedAt(), product.getId())
                : Cursor.of(product.getPrice(), product.getId());
    }
}
