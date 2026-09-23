package com.golmok.market.domain.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminActionRepository extends JpaRepository<AdminAction, Long> {

    /** 이 대상에 어떤 조치가 있었는지(신고 상세에서 보여 준다). 최근 것부터. */
    List<AdminAction> findByTargetTypeAndTargetIdOrderByIdDesc(AdminActionTarget targetType, long targetId);
}
