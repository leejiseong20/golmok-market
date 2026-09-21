package com.golmok.market.domain.report.dto;

import com.golmok.market.domain.report.ReportReason;
import com.golmok.market.domain.report.ReportTarget;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReportCreateRequest(

        @NotNull(message = "신고 대상을 선택해 주세요.")
        ReportTarget targetType,

        @NotNull(message = "신고 대상을 선택해 주세요.")
        @Positive(message = "신고 대상이 올바르지 않습니다.")
        Long targetId,

        @NotNull(message = "신고 사유를 선택해 주세요.")
        ReportReason reason,

        @Size(max = 500, message = "신고 내용은 500자 이하로 입력해 주세요.")
        String detail
) {
    public ReportCreateRequest {
        // 공백만 적은 것은 안 적은 것과 같다. 아래에서 "기타" 사유를 검사할 때 이 정규화가 필요하다.
        detail = detail == null || detail.isBlank() ? null : detail.trim();
    }
}
