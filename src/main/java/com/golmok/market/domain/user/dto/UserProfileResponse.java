package com.golmok.market.domain.user.dto;

import java.math.BigDecimal;

public record UserProfileResponse(Long id, String nickname, String profileImageUrl, BigDecimal mannerTemp,
                                  long productCount, long reviewCount) {
}
