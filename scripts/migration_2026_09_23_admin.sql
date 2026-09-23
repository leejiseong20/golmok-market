-- ============================================================
-- 관리자 화면(신고 처리 + 감사 로그) 스키마 변경.
--
-- **새 이미지를 올리기 전에 실행한다.** ddl-auto: validate 라서 칸이 없으면 앱이 아예 뜨지 않는다.
--
-- 실행: docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot golmok' \
--         < scripts/migration_2026_09_23_admin.sql
-- 실행 전 백업을 받는다(deploy/backup.sh).
--
-- 기존 신고는 status 기본값으로 모두 PENDING(처리 전)이 된다.
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE reports
    ADD COLUMN status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | RESOLVED | REJECTED' AFTER detail,
    ADD COLUMN handled_at DATETIME     NULL COMMENT '관리자가 처리한 시각' AFTER status,
    ADD COLUMN handled_by BIGINT       NULL COMMENT '처리한 관리자' AFTER handled_at,
    ADD COLUMN admin_memo VARCHAR(500) NULL COMMENT '관리자가 남긴 판단 근거' AFTER handled_by,
    ADD KEY idx_reports_status (status, id),
    ADD CONSTRAINT fk_reports_handler FOREIGN KEY (handled_by) REFERENCES users(id);

CREATE TABLE IF NOT EXISTS admin_actions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    admin_id    BIGINT       NOT NULL,
    action      VARCHAR(30)  NOT NULL COMMENT 'SUSPEND_USER | UNSUSPEND_USER | DELETE_PRODUCT | RESOLVE_REPORT | REJECT_REPORT',
    target_type VARCHAR(20)  NOT NULL COMMENT 'USER | PRODUCT | REPORT',
    target_id   BIGINT       NOT NULL,
    report_id   BIGINT       NULL COMMENT '신고를 처리하며 한 조치면 그 신고',
    reason      VARCHAR(500) NOT NULL COMMENT '관리자가 적은 근거',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_actions_target (target_type, target_id),
    KEY idx_admin_actions_admin (admin_id, id),
    CONSTRAINT fk_admin_actions_admin FOREIGN KEY (admin_id) REFERENCES users(id)
) ENGINE=InnoDB COMMENT='관리자 조치 기록';

-- 확인용. 신고 상태 분포와 새 표가 만들어졌는지 본다.
SELECT status, COUNT(*) AS reports FROM reports GROUP BY status;
SELECT COUNT(*) AS admin_actions FROM admin_actions;
