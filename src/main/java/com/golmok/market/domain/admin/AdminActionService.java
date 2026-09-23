package com.golmok.market.domain.admin;

import com.golmok.market.domain.admin.dto.AdminActionLogResponse;
import com.golmok.market.domain.product.AdminProductRepository;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.user.AdminUserRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.global.pagination.Cursor;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.pagination.PageSize;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 조치 기록(감사 로그) 조회. 쓰기는 조치하는 서비스가 조치와 같은 트랜잭션으로 한다 — 여기서는 읽기만 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminActionService {

    private final AdminActionRepository adminActionRepository;
    private final AdminUserRepository adminUserRepository;
    private final AdminProductRepository adminProductRepository;

    public CursorResponse<AdminActionLogResponse> list(AdminActionType action, AdminActionTarget targetType,
                                                       String rawCursor, Integer rawSize) {
        PageSize size = PageSize.of(rawSize);
        Long cursorId = rawCursor == null || rawCursor.isBlank() ? null : Cursor.parse(rawCursor).id();
        List<AdminAction> actions = adminActionRepository.findPage(action, targetType, cursorId,
                PageRequest.of(0, size.fetchSize()));

        // 대상 이름은 종류별로 한 번에 읽는다(기록마다 읽으면 N+1). 기록은 지우지 않으므로 대상이 사라졌을 수도 있다.
        Map<Long, String> userNames = namesOf(actions, AdminActionTarget.USER,
                ids -> adminUserRepository.findAllById(ids).stream()
                        .collect(Collectors.toMap(User::getId, User::getNickname)));
        Map<Long, String> productNames = namesOf(actions, AdminActionTarget.PRODUCT,
                ids -> adminProductRepository.findAllById(ids).stream()
                        .collect(Collectors.toMap(Product::getId, Product::getTitle)));

        List<AdminActionLogResponse> logs = actions.stream()
                .map(log -> AdminActionLogResponse.of(log, switch (log.getTargetType()) {
                    case USER -> userNames.getOrDefault(log.getTargetId(), "(없는 사용자)");
                    case PRODUCT -> productNames.getOrDefault(log.getTargetId(), "(없는 상품)");
                    case REPORT -> "신고 #" + log.getTargetId();
                }))
                .toList();
        return CursorResponse.of(logs, size, log -> new Cursor(String.valueOf(log.id()), log.id()));
    }

    private static Map<Long, String> namesOf(List<AdminAction> actions, AdminActionTarget type,
                                             Function<List<Long>, Map<Long, String>> loader) {
        List<Long> ids = actions.stream()
                .filter(action -> action.getTargetType() == type)
                .map(AdminAction::getTargetId).distinct().toList();
        return ids.isEmpty() ? new HashMap<>() : loader.apply(ids);
    }
}
