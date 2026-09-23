package com.golmok.market.domain.admin.dto;

/**
 * 관리자 현황판 숫자. "오늘"은 서버 시간대(Asia/Seoul) 자정부터다.
 *
 * @param completedTradesLast7Days 최근 7일(지금부터 7×24시간) 안에 끝난 거래 수
 */
public record AdminSummaryResponse(long pendingReports, long suspendedUsers, long newUsersToday,
                                   long newProductsToday, long completedTradesLast7Days) {
}
