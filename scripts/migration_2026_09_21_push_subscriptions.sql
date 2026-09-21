-- ============================================================
-- 웹 푸시 구독 테이블 추가 (2026-09-21)
--
-- 순서가 중요하다. 앱은 ddl-auto: validate 라서 테이블이 없으면 **시작하지 못한다.**
-- 반드시 새 이미지를 올리기 전에 이 스크립트를 먼저 실행한다.
--
--   서버(Ubuntu)에서:
--   cd ~/golmok-market && git pull && cd deploy
--   docker compose exec -T mysql sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" golmok' < ../scripts/migration_2026_09_21_push_subscriptions.sql
--   (VAPID 키를 deploy/.env 에 넣은 뒤) docker compose pull app && docker compose up -d app
--
-- 두 번 실행해도 안전하다(IF NOT EXISTS).
-- ============================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS push_subscriptions (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    endpoint   VARCHAR(500) NOT NULL COMMENT '푸시 서비스가 기기마다 발급한 주소',
    p256dh     VARCHAR(100) NOT NULL COMMENT '브라우저 P-256 공개키(base64url). 본문 암호화에 쓴다',
    auth       VARCHAR(50)  NOT NULL COMMENT '브라우저 인증 비밀(base64url)',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_push_endpoint (endpoint) COMMENT '기기 하나에 구독 하나. 다른 계정으로 다시 구독하면 주인을 바꾼다',
    KEY idx_push_user (user_id) COMMENT '한 사람의 모든 기기로 보낼 때',
    CONSTRAINT fk_push_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB COMMENT='웹 푸시 구독';
