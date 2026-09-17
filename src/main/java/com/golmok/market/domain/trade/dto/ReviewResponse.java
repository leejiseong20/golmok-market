package com.golmok.market.domain.trade.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.golmok.market.domain.trade.Review;

import java.time.LocalDateTime;

/** 공개 후기에는 거래 번호·금액·채팅방 정보를 싣지 않는다. */
public record ReviewResponse(Long id, Reviewer reviewer, int score, String content,
                             @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt) {

    /** 작성자 사진 경로. 받은 후기 조회가 작성자를 fetch join 해 추가 쿼리는 없다. */
    public record Reviewer(Long id, String nickname, String profileImageUrl) {
    }

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(),
                new Reviewer(review.getReviewer().getId(), review.getReviewer().getNickname(),
                        review.getReviewer().getProfileImageUrl()),
                review.getScore(), review.getContent(), review.getCreatedAt());
    }
}
