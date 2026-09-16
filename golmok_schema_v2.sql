-- ============================================================
-- 골목마켓 (Golmok Market) - Database Schema v2
-- MySQL 8.0+
--
-- v1 대비 변경점
--   - categories 초기데이터를 프론트 초안 기준으로 교체
--   - products 가격 정렬 전용 인덱스 추가
--   - trades UNIQUE를 생성컬럼 기반 조건부 UNIQUE로 교체
--   - search_logs, refresh_tokens 테이블 추가
-- ============================================================

-- 이 파일을 읽는 클라이언트의 문자셋을 고정한다.
-- 없으면 실행 환경의 기본값을 따르는데, MySQL Docker 이미지의 초기화 스크립트는 latin1 로 읽어
-- 아래 초기 데이터의 한글(카테고리·동네 이름)이 이중 인코딩돼 깨진 채 저장된다.
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS golmok
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE golmok;

-- ============================================================
-- 1. 지역 (행정동)
-- ============================================================
CREATE TABLE regions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    sido        VARCHAR(20)  NOT NULL COMMENT '시/도',
    sigungu     VARCHAR(30)  NOT NULL COMMENT '시/군/구',
    dong        VARCHAR(30)  NOT NULL COMMENT '읍/면/동',
    lat         DOUBLE       NOT NULL COMMENT '중심 위도',
    lng         DOUBLE       NOT NULL COMMENT '중심 경도',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_regions_full (sido, sigungu, dong),
    KEY idx_regions_coord (lat, lng)
) ENGINE=InnoDB COMMENT='행정동 마스터';

-- ============================================================
-- 2. 사용자
-- ============================================================
CREATE TABLE users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    email             VARCHAR(100) NOT NULL,
    password          VARCHAR(255) NOT NULL COMMENT 'BCrypt 해시',
    nickname          VARCHAR(30)  NOT NULL,
    phone             VARCHAR(20)  NULL,
    profile_image_url VARCHAR(500) NULL,
    manner_temp       DECIMAL(3,1) NOT NULL DEFAULT 36.5 COMMENT '매너온도',
    role              ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
    status            ENUM('ACTIVE','SUSPENDED','WITHDRAWN') NOT NULL DEFAULT 'ACTIVE',
    last_login_at     DATETIME     NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at        DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_nickname (nickname)
) ENGINE=InnoDB COMMENT='회원';

-- ============================================================
-- 3. 리프레시 토큰 (JWT)
-- ============================================================
CREATE TABLE refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token       VARCHAR(500) NOT NULL,
    device_info VARCHAR(200) NULL COMMENT 'User-Agent 요약',
    expires_at  DATETIME     NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token (token),
    KEY idx_refresh_tokens_user (user_id),
    KEY idx_refresh_tokens_expires (expires_at) COMMENT '만료 토큰 배치 삭제용',
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='리프레시 토큰';

-- ============================================================
-- 4. 사용자 동네 인증 (1인 최대 2개)
-- ============================================================
CREATE TABLE user_regions (
    id           BIGINT   NOT NULL AUTO_INCREMENT,
    user_id      BIGINT   NOT NULL,
    region_id    BIGINT   NOT NULL,
    is_primary   BOOLEAN  NOT NULL DEFAULT FALSE COMMENT '대표 동네 여부',
    verify_count INT      NOT NULL DEFAULT 1 COMMENT '인증 횟수(동네 범위 확장용)',
    verified_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_region (user_id, region_id),
    KEY idx_user_regions_user (user_id),
    CONSTRAINT fk_user_regions_user   FOREIGN KEY (user_id)   REFERENCES users(id),
    CONSTRAINT fk_user_regions_region FOREIGN KEY (region_id) REFERENCES regions(id)
) ENGINE=InnoDB COMMENT='동네 인증 내역';

-- ============================================================
-- 5. 카테고리 (자기참조 계층)
-- ============================================================
CREATE TABLE categories (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id  BIGINT       NULL,
    name       VARCHAR(50)  NOT NULL,
    icon_url   VARCHAR(500) NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_name (name),
    KEY idx_categories_parent (parent_id),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id)
) ENGINE=InnoDB COMMENT='상품 카테고리';

-- ============================================================
-- 6. 상품
-- ============================================================
CREATE TABLE products (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    seller_id      BIGINT       NOT NULL,
    category_id    BIGINT       NOT NULL,
    region_id      BIGINT       NOT NULL COMMENT '등록 시점 동네 스냅샷',
    title          VARCHAR(100) NOT NULL,
    description    TEXT         NOT NULL,
    price          INT          NOT NULL DEFAULT 0 COMMENT '0 = 나눔',
    is_negotiable  BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '가격 제안 허용',
    status         ENUM('ON_SALE','RESERVED','SOLD') NOT NULL DEFAULT 'ON_SALE',
    trade_type     ENUM('DIRECT','DELIVERY') NOT NULL DEFAULT 'DIRECT' COMMENT '직거래/택배(결제)',
    view_count     INT          NOT NULL DEFAULT 0,
    favorite_count INT          NOT NULL DEFAULT 0,
    chat_count     INT          NOT NULL DEFAULT 0,
    bumped_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '끌어올리기 시각 = 최신순 정렬 기준',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at     DATETIME     NULL COMMENT 'soft delete',
    PRIMARY KEY (id),
    -- 최신순 정렬용
    KEY idx_products_recent (region_id, status, deleted_at, bumped_at DESC),
    -- 낮은 가격순 정렬용 (filesort 회피)
    KEY idx_products_price (region_id, status, deleted_at, price ASC),
    -- 카테고리 필터 + 최신순
    KEY idx_products_category (category_id, status, deleted_at, bumped_at DESC),
    -- 내 판매내역
    KEY idx_products_seller (seller_id, created_at DESC),
    FULLTEXT KEY ft_products_search (title, description) WITH PARSER ngram,
    CONSTRAINT fk_products_seller   FOREIGN KEY (seller_id)   REFERENCES users(id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT fk_products_region   FOREIGN KEY (region_id)   REFERENCES regions(id)
) ENGINE=InnoDB COMMENT='판매 상품';

-- ============================================================
-- 7. 상품 이미지
-- ============================================================
CREATE TABLE product_images (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    product_id BIGINT       NOT NULL,
    image_url  VARCHAR(500) NOT NULL COMMENT 'S3 URL',
    sort_order INT          NOT NULL DEFAULT 0 COMMENT '0 = 대표 이미지(thumbnailUrl)',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_product_images_product (product_id, sort_order),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id)
        REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='상품 이미지';

-- ============================================================
-- 8. 관심목록
-- ============================================================
CREATE TABLE favorites (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    product_id BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorite (user_id, product_id),
    KEY idx_favorites_product (product_id),
    CONSTRAINT fk_favorites_user    FOREIGN KEY (user_id)    REFERENCES users(id),
    CONSTRAINT fk_favorites_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB COMMENT='찜하기';

-- ============================================================
-- 9. 채팅방
-- ============================================================
CREATE TABLE chat_rooms (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    product_id      BIGINT       NOT NULL,
    buyer_id        BIGINT       NOT NULL,
    seller_id       BIGINT       NOT NULL,
    last_message    VARCHAR(200) NULL COMMENT '목록 표시용 캐시',
    last_message_at DATETIME     NULL,
    buyer_left      BOOLEAN      NOT NULL DEFAULT FALSE,
    seller_left     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_room (product_id, buyer_id) COMMENT '같은 상품에 같은 구매자는 방 1개',
    KEY idx_chat_rooms_buyer  (buyer_id, last_message_at DESC),
    KEY idx_chat_rooms_seller (seller_id, last_message_at DESC),
    CONSTRAINT fk_chat_rooms_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_chat_rooms_buyer   FOREIGN KEY (buyer_id)   REFERENCES users(id),
    CONSTRAINT fk_chat_rooms_seller  FOREIGN KEY (seller_id)  REFERENCES users(id)
) ENGINE=InnoDB COMMENT='1:1 채팅방';

-- ============================================================
-- 10. 채팅 메시지
-- ============================================================
CREATE TABLE chat_messages (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    room_id    BIGINT   NOT NULL,
    sender_id  BIGINT   NOT NULL,
    type       ENUM('TEXT','IMAGE','SYSTEM') NOT NULL DEFAULT 'TEXT',
    content    TEXT     NOT NULL,
    is_read    BOOLEAN  NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_chat_messages_room (room_id, id DESC),
    KEY idx_chat_messages_unread (room_id, sender_id, is_read),
    CONSTRAINT fk_chat_messages_room   FOREIGN KEY (room_id)   REFERENCES chat_rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id)
) ENGINE=InnoDB COMMENT='채팅 메시지';

-- ============================================================
-- 11. 거래
--   진행 중 거래는 상품당 1건만, 취소/환불 건은 여러 개 허용.
--   MySQL에 조건부 UNIQUE가 없어 생성컬럼으로 우회한다.
-- ============================================================
CREATE TABLE trades (
    id                BIGINT   NOT NULL AUTO_INCREMENT,
    product_id        BIGINT   NOT NULL,
    chat_room_id      BIGINT   NULL,
    seller_id         BIGINT   NOT NULL,
    buyer_id          BIGINT   NOT NULL,
    amount            INT      NOT NULL,
    status            ENUM('REQUESTED','PAID','SHIPPING','CONFIRMED','CANCELED','REFUNDED')
                      NOT NULL DEFAULT 'REQUESTED',
    cancel_reason     VARCHAR(255) NULL,
    completed_at      DATETIME NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    active_product_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('CANCELED','REFUNDED') THEN NULL ELSE product_id END
    ) STORED COMMENT '진행 중일 때만 product_id, 아니면 NULL',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trades_active_product (active_product_id),
    KEY idx_trades_product (product_id, created_at DESC),
    KEY idx_trades_buyer   (buyer_id, created_at DESC),
    KEY idx_trades_seller  (seller_id, created_at DESC),
    CONSTRAINT fk_trades_product FOREIGN KEY (product_id)   REFERENCES products(id),
    CONSTRAINT fk_trades_room    FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(id),
    CONSTRAINT fk_trades_seller  FOREIGN KEY (seller_id)    REFERENCES users(id),
    CONSTRAINT fk_trades_buyer   FOREIGN KEY (buyer_id)     REFERENCES users(id)
) ENGINE=InnoDB COMMENT='거래 (영수증)';

-- ============================================================
-- 12. 결제 (토스페이먼츠)
-- ============================================================
CREATE TABLE payments (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    trade_id     BIGINT       NOT NULL,
    order_id     VARCHAR(64)  NOT NULL COMMENT '우리가 생성하는 주문번호 - 멱등성 키',
    payment_key  VARCHAR(200) NULL     COMMENT '토스 발급 결제키',
    amount       INT          NOT NULL,
    method       VARCHAR(30)  NULL     COMMENT '카드/계좌이체/간편결제',
    status       ENUM('READY','APPROVED','FAILED','CANCELED') NOT NULL DEFAULT 'READY',
    fail_code    VARCHAR(50)  NULL,
    fail_reason  VARCHAR(255) NULL,
    approved_at  DATETIME     NULL,
    canceled_at  DATETIME     NULL,
    raw_response JSON         NULL     COMMENT 'PG 원본 응답 보관',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_order (order_id),
    UNIQUE KEY uk_payments_key (payment_key),
    KEY idx_payments_trade (trade_id, created_at DESC),
    CONSTRAINT fk_payments_trade FOREIGN KEY (trade_id) REFERENCES trades(id)
) ENGINE=InnoDB COMMENT='결제 시도 이력';

-- ============================================================
-- 13. 매너평가
-- ============================================================
CREATE TABLE reviews (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    trade_id    BIGINT       NOT NULL,
    reviewer_id BIGINT       NOT NULL,
    reviewee_id BIGINT       NOT NULL,
    score       TINYINT      NOT NULL COMMENT '1~5',
    content     VARCHAR(500) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review (trade_id, reviewer_id) COMMENT '거래당 1인 1회',
    KEY idx_reviews_reviewee (reviewee_id, created_at DESC),
    CONSTRAINT fk_reviews_trade    FOREIGN KEY (trade_id)    REFERENCES trades(id),
    CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id),
    CONSTRAINT fk_reviews_reviewee FOREIGN KEY (reviewee_id) REFERENCES users(id)
) ENGINE=InnoDB COMMENT='거래 후 매너평가';

-- ============================================================
-- 14. 알림
-- ============================================================
CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       ENUM('CHAT','FAVORITE','PRICE_DROP','TRADE','SYSTEM') NOT NULL,
    title      VARCHAR(100) NOT NULL,
    content    VARCHAR(255) NULL,
    target_url VARCHAR(255) NULL COMMENT '클릭 시 이동 경로',
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notifications_user (user_id, is_read, created_at DESC),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB COMMENT='알림함';

-- ============================================================
-- 15. 검색 로그 (인기 검색어 집계용)
-- ============================================================
CREATE TABLE search_logs (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NULL COMMENT '비로그인 검색 허용',
    region_id  BIGINT      NULL,
    keyword    VARCHAR(50) NOT NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_search_logs_agg (created_at, keyword) COMMENT '기간별 집계용',
    CONSTRAINT fk_search_logs_user   FOREIGN KEY (user_id)   REFERENCES users(id),
    CONSTRAINT fk_search_logs_region FOREIGN KEY (region_id) REFERENCES regions(id)
) ENGINE=InnoDB COMMENT='검색 로그';

-- 인기 검색어 조회 예시 (최근 24시간 TOP 5)
-- SELECT keyword, COUNT(*) AS cnt
--   FROM search_logs
--  WHERE created_at >= NOW() - INTERVAL 1 DAY
--  GROUP BY keyword
--  ORDER BY cnt DESC
--  LIMIT 5;

-- ============================================================
-- 초기 데이터
-- ============================================================
INSERT INTO categories (parent_id, name, sort_order) VALUES
  (NULL, '디지털',   1),
  (NULL, '가구',     2),
  (NULL, '의류',     3),
  (NULL, '생활가전', 4),
  (NULL, '스포츠',   5),
  (NULL, '유아',     6),
  (NULL, '기타',     7);

INSERT INTO regions (sido, sigungu, dong, lat, lng) VALUES
  ('서울특별시', '강남구', '역삼동', 37.5006, 127.0366),
  ('서울특별시', '마포구', '서교동', 37.5520, 126.9180),
  ('서울특별시', '성동구', '성수동', 37.5446, 127.0559);
