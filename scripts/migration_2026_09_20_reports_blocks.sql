-- ============================================================
-- 신고 · 차단 테이블 추가 (2026-09-20)
--
-- golmok_schema_v2.sql 은 **데이터가 없는 첫 기동 때만** 실행된다.
-- 이미 돌고 있는 서버에는 이 파일을 직접 실행해야 한다.
--
-- 실행 순서가 중요하다. 애플리케이션은 ddl-auto: validate 라서
-- 엔티티에 맞는 테이블이 없으면 **시작하지 못한다.**
-- 반드시 새 이미지를 올리기 전에 이 스크립트를 먼저 실행한다.
--
--   서버(Ubuntu)에서:
--   cd ~/golmok-market && git pull && cd deploy
--   docker compose exec -T mysql sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" golmok' < ../scripts/migration_2026_09_20_reports_blocks.sql
--   docker compose pull app && docker compose up -d app
--
-- 비밀번호 변수는 작은따옴표 안에 둬서 **컨테이너 안에서** 풀리게 한다.
-- 서버 셸에는 그 변수가 없다(deploy/.env 는 docker compose 만 읽는다). 셸에서 풀면 빈 비밀번호가 된다.
-- 이 방식이면 비밀번호가 명령줄·셸 기록에 남지도 않는다.
--
-- 두 번 실행해도 안전하다(IF NOT EXISTS).
-- ============================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS reports (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT       NOT NULL,
    target_type VARCHAR(20)  NOT NULL COMMENT 'USER | PRODUCT',
    target_id   BIGINT       NOT NULL,
    reason      VARCHAR(30)  NOT NULL COMMENT 'SPAM | FRAUD | PROHIBITED | ABUSE | OTHER',
    detail      VARCHAR(500) NULL COMMENT '신고자가 적은 설명. 사유가 OTHER 면 필수',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_report (reporter_id, target_type, target_id),
    KEY idx_reports_target (target_type, target_id),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users(id)
) ENGINE=InnoDB COMMENT='신고';

CREATE TABLE IF NOT EXISTS blocks (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    blocker_id BIGINT   NOT NULL COMMENT '차단한 사람',
    blocked_id BIGINT   NOT NULL COMMENT '차단당한 사람',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_block (blocker_id, blocked_id),
    KEY idx_blocks_blocked (blocked_id),
    CONSTRAINT fk_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES users(id),
    CONSTRAINT fk_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES users(id)
) ENGINE=InnoDB COMMENT='사용자 차단';
