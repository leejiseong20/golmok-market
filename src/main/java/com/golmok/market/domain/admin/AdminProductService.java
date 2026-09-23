package com.golmok.market.domain.admin;

import com.golmok.market.domain.admin.dto.AdminActionResponse;
import com.golmok.market.domain.admin.dto.AdminProductDetail;
import com.golmok.market.domain.admin.dto.AdminProductSummary;
import com.golmok.market.domain.product.AdminProductRepository;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.product.ProductThumbnails;
import com.golmok.market.domain.report.AdminReportRepository;
import com.golmok.market.domain.report.ReportTarget;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.pagination.Cursor;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.pagination.PageSize;
import com.golmok.market.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 상품 관리(관리자). 삭제한 상품도 보고, 관리자가 내린 상품은 되살린다.
 *
 * **되살리기는 마지막으로 관리자가 내린 상품만 된다.** 판매자가 직접 지운 상품을 관리자가 되돌리면 판매자의 뜻을 뒤집고,
 * 탈퇴한 판매자의 상품은 주인이 없다. 누가 지웠는지는 상품에 적혀 있지 않아 조치 기록의 마지막 내리기·되살리기로 가른다
 * (신고 처리에서 이미 지워진 상품은 내리기 기록을 남기지 않으므로 이 규칙이 성립한다).
 *
 * 관리자 내리기는 진행 중인 거래가 있어도 막지 않는다. 신고 처리의 "상품 내리기"와 같은 규칙이다(문제 상품은 거래 중이어도 내려야 한다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProductService {

    private static final Set<AdminActionType> VISIBILITY_ACTIONS =
            Set.of(AdminActionType.DELETE_PRODUCT, AdminActionType.RESTORE_PRODUCT);

    private final AdminProductRepository adminProductRepository;
    private final ProductRepository productRepository;
    private final ProductThumbnails productThumbnails;
    private final AdminReportRepository adminReportRepository;
    private final AdminActionRepository adminActionRepository;
    private final UserRepository userRepository;

    /**
     * @param deleted true 면 삭제한 상품만, false 면 보이는 상품만, null 이면 전부
     */
    public CursorResponse<AdminProductSummary> list(String query, Long sellerId, Boolean deleted,
                                                    String rawCursor, Integer rawSize) {
        PageSize size = PageSize.of(rawSize);
        Long cursorId = rawCursor == null || rawCursor.isBlank() ? null : Cursor.parse(rawCursor).id();
        List<Product> products = adminProductRepository.findPage(sellerId, AdminText.likeKeyword(query), deleted,
                cursorId, PageRequest.of(0, size.fetchSize()));
        return CursorResponse.of(summaries(products), size,
                summary -> new Cursor(String.valueOf(summary.id()), summary.id()));
    }

    public AdminProductDetail detail(long productId) {
        Product product = adminProductRepository.findWithSeller(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        List<AdminActionResponse> actions = adminActionRepository.findHistory(AdminActionTarget.PRODUCT, productId)
                .stream().map(AdminActionResponse::from).toList();
        return new AdminProductDetail(summaries(List.of(product)).get(0), product.getDescription(), actions);
    }

    /** 내리기(soft delete). 잠근 뒤 확인해 두 관리자가 동시에 눌러도 기록이 한 번만 남는다. */
    @Transactional
    public AdminProductDetail delete(AuthUser viewer, long productId, String reason) {
        Product product = lockedProduct(productId);
        if (product.isDeleted()) {
            throw new BusinessException(ErrorCode.ADMIN_ACTION_NOT_ALLOWED, "이미 삭제된 상품입니다.");
        }
        product.softDelete();
        record(viewer, AdminActionType.DELETE_PRODUCT, productId, reason);
        return detail(productId);
    }

    @Transactional
    public AdminProductDetail restore(AuthUser viewer, long productId, String reason) {
        Product product = lockedProduct(productId);
        if (!product.isDeleted()) {
            throw new BusinessException(ErrorCode.ADMIN_ACTION_NOT_ALLOWED, "삭제된 상품이 아닙니다.");
        }
        if (!deletedByAdmin(List.of(productId)).getOrDefault(productId, false)) {
            throw new BusinessException(ErrorCode.ADMIN_ACTION_NOT_ALLOWED, "판매자가 직접 지운 상품은 되살릴 수 없습니다.");
        }
        if (product.getSeller().isWithdrawn()) {
            throw new BusinessException(ErrorCode.ADMIN_ACTION_NOT_ALLOWED, "탈퇴한 회원의 상품은 되살릴 수 없습니다.");
        }
        product.restore();
        record(viewer, AdminActionType.RESTORE_PRODUCT, productId, reason);
        return detail(productId);
    }

    private Product lockedProduct(long productId) {
        return productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private void record(AuthUser viewer, AdminActionType action, long productId, String reason) {
        User admin = userRepository.getReferenceById(viewer.id());
        adminActionRepository.save(AdminAction.of(admin, action, AdminActionTarget.PRODUCT, productId, null, reason));
    }

    /** 썸네일·신고 수·관리자 내림 여부를 종류별로 한 번씩만 조회해 붙인다(상품마다 조회하면 N+1). */
    private List<AdminProductSummary> summaries(List<Product> products) {
        List<Long> ids = products.stream().map(Product::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, String> thumbnails = productThumbnails.of(ids);
        Map<Long, Long> reportCounts = new HashMap<>();
        adminReportRepository.countByTargets(ReportTarget.PRODUCT, ids)
                .forEach(row -> reportCounts.put(row.getTargetId(), row.getTotal()));
        Map<Long, Boolean> byAdmin = deletedByAdmin(ids);
        return products.stream()
                .map(product -> AdminProductSummary.of(product, thumbnails.get(product.getId()),
                        byAdmin.getOrDefault(product.getId(), false), reportCounts.getOrDefault(product.getId(), 0L)))
                .toList();
    }

    /** 상품마다 마지막 내리기·되살리기 조치가 "내리기"인지. 최근 것부터 오므로 처음 만난 것이 마지막이다. */
    private Map<Long, Boolean> deletedByAdmin(List<Long> productIds) {
        Map<Long, Boolean> result = new HashMap<>();
        adminActionRepository.findLatestOf(AdminActionTarget.PRODUCT, productIds, VISIBILITY_ACTIONS)
                .forEach(action -> result.putIfAbsent(action.getTargetId(),
                        action.getAction() == AdminActionType.DELETE_PRODUCT));
        return result;
    }
}
