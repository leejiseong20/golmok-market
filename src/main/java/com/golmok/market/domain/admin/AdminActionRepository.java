package com.golmok.market.domain.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface AdminActionRepository extends JpaRepository<AdminAction, Long> {

    /** 이 대상에 어떤 조치가 있었는지(신고 상세에서 보여 준다). 최근 것부터. */
    List<AdminAction> findByTargetTypeAndTargetIdOrderByIdDesc(AdminActionTarget targetType, long targetId);

    /** 위와 같지만 관리자 닉네임을 함께 읽는다. 사용자·상품 상세처럼 이력을 바로 응답으로 바꾸는 곳에서 쓴다. */
    @Query("""
            select a from AdminAction a join fetch a.admin
            where a.targetType = :targetType and a.targetId = :targetId
            order by a.id desc
            """)
    List<AdminAction> findHistory(AdminActionTarget targetType, long targetId);

    /** 조치 기록 한 페이지. 종류·대상 종류로 거를 수 있다(null 이면 전체). */
    @Query("""
            select a from AdminAction a join fetch a.admin
            where (:action is null or a.action = :action)
              and (:targetType is null or a.targetType = :targetType)
              and (:cursorId is null or a.id < :cursorId)
            order by a.id desc
            """)
    List<AdminAction> findPage(AdminActionType action, AdminActionTarget targetType, Long cursorId, Pageable pageable);

    /**
     * 여러 대상의 특정 조치들. 최근 것부터 오므로 대상마다 처음 만나는 것이 마지막 조치다.
     * 상품 목록에서 "관리자가 내렸는지"를 한 번에 가르는 데 쓴다(idx_admin_actions_target).
     */
    @Query("""
            select a from AdminAction a
            where a.targetType = :targetType and a.targetId in :targetIds and a.action in :actions
            order by a.id desc
            """)
    List<AdminAction> findLatestOf(AdminActionTarget targetType, Collection<Long> targetIds,
                                   Collection<AdminActionType> actions);
}
