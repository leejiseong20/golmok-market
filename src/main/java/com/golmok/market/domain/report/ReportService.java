package com.golmok.market.domain.report;

import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.report.dto.ReportCreateRequest;
import com.golmok.market.domain.report.dto.ReportResponse;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 접수.
 *
 * 접수만 하고 대상에는 아무 일도 하지 않는다(숨김·정지 없음). 자동 조치는 오남용에 그대로 열려 있다.
 * 신고당한 사람에게 알림도 보내지 않는다. 누가 신고했는지 드러나면 보복으로 이어진다.
 *
 * 중복 신고는 (reporter_id, target_type, target_id) UNIQUE 가 최종 방어선이다.
 * 찜과 같은 방식으로, 미리 한 번 확인하고 그 사이에 들어온 같은 요청은 제약 위반을 잡아 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional
    public ReportResponse report(ReportCreateRequest request, AuthUser viewer) {
        // "기타" 는 사유 자체로는 아무 정보가 없다. 무엇이 문제인지 적어야 신고가 의미를 갖는다.
        if (request.reason() == ReportReason.OTHER && request.detail() == null) {
            throw new BusinessException(ErrorCode.REPORT_DETAIL_REQUIRED);
        }

        long targetId = request.targetId();
        switch (request.targetType()) {
            case USER -> checkUserTarget(targetId, viewer);
            case PRODUCT -> checkProductTarget(targetId, viewer);
        }

        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(viewer.id(), request.targetType(), targetId)) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }
        try {
            // 위 확인과 INSERT 사이에 같은 요청이 한 번 더 들어올 수 있다(더블클릭).
            // flush 를 여기서 해야 제약 위반을 잡을 수 있다.
            Report saved = reportRepository.saveAndFlush(Report.of(
                    userRepository.getReferenceById(viewer.id()),
                    request.targetType(), targetId, request.reason(), request.detail()));
            return ReportResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }
    }

    /** 탈퇴한 회원은 공개 프로필과 같은 기준으로 "없는 사용자"로 본다(익명화된 행만 남아 있다). */
    private void checkUserTarget(long targetId, AuthUser viewer) {
        User target = userRepository.findById(targetId)
                .filter(found -> !found.isWithdrawn())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (target.getId().equals(viewer.id())) {
            throw new BusinessException(ErrorCode.CANNOT_REPORT_SELF);
        }
    }

    private void checkProductTarget(long targetId, AuthUser viewer) {
        Product target = productRepository.findVisibleById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (target.isOwnedBy(viewer.id())) {
            throw new BusinessException(ErrorCode.CANNOT_REPORT_SELF);
        }
    }
}
