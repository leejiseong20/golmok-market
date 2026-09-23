package com.golmok.market.domain.admin;

import com.golmok.market.domain.admin.dto.AdminSummaryResponse;
import com.golmok.market.domain.product.AdminProductRepository;
import com.golmok.market.domain.report.AdminReportRepository;
import com.golmok.market.domain.report.ReportStatus;
import com.golmok.market.domain.trade.TradeRepository;
import com.golmok.market.domain.trade.TradeStatus;
import com.golmok.market.domain.user.AdminUserRepository;
import com.golmok.market.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 관리자 현황판. 관리자가 들어오자마자 "지금 처리할 것이 있는지"를 보게 한다.
 *
 * 가입·등록·거래완료 수는 created_at·completed_at 인덱스가 없어 표를 전부 훑는다. 지금 규모(수백 행)에서는 문제가 없고,
 * 커지면 인덱스를 더하거나 집계 표를 따로 둔다(알려진 과제).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSummaryService {

    private final AdminReportRepository adminReportRepository;
    private final AdminUserRepository adminUserRepository;
    private final AdminProductRepository adminProductRepository;
    private final TradeRepository tradeRepository;
    private final Clock clock;

    public AdminSummaryResponse summary() {
        LocalDateTime startOfToday = LocalDate.now(clock).atStartOfDay();
        LocalDateTime weekAgo = LocalDateTime.now(clock).minusDays(7);
        return new AdminSummaryResponse(
                adminReportRepository.countByStatus(ReportStatus.PENDING),
                adminUserRepository.countByStatus(UserStatus.SUSPENDED),
                adminUserRepository.countByCreatedAtGreaterThanEqual(startOfToday),
                adminProductRepository.countByCreatedAtGreaterThanEqual(startOfToday),
                tradeRepository.countByStatusAndCompletedAtGreaterThanEqual(TradeStatus.CONFIRMED, weekAgo));
    }
}
