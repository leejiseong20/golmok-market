-- ============================================================
-- 데모 데이터 삭제. seed_demo_data.sql 로 넣은 것만 지운다.
--
-- 기준은 @golmok.test 이메일이다. 이 도메인은 예약된 테스트용 도메인이라
-- 실제 사용자 계정과 겹치지 않는다.
--
-- 실행: mysql -u root -p golmok < scripts/clean_demo_data.sql
--
-- 지우는 순서는 외래키 방향의 역순이다.
-- (favorites·images → products → refresh_tokens·user_regions → users)
-- ============================================================

SET NAMES utf8mb4;

SET @demo_users = (SELECT GROUP_CONCAT(id) FROM users WHERE email LIKE '%@golmok.test');

DELETE f FROM favorites f
JOIN products p ON p.id = f.product_id
JOIN users u ON u.id = p.seller_id
WHERE u.email LIKE '%@golmok.test';

-- 데모 사용자가 남의 상품을 찜한 경우까지 정리한다.
DELETE f FROM favorites f
JOIN users u ON u.id = f.user_id
WHERE u.email LIKE '%@golmok.test';

-- 거래는 상품·회원을 참조하므로 먼저 지운다.
DELETE t FROM trades t
JOIN users u ON u.id = t.buyer_id OR u.id = t.seller_id
WHERE u.email LIKE '%@golmok.test';

DELETE pi FROM product_images pi
JOIN products p ON p.id = pi.product_id
JOIN users u ON u.id = p.seller_id
WHERE u.email LIKE '%@golmok.test';

DELETE p FROM products p
JOIN users u ON u.id = p.seller_id
WHERE u.email LIKE '%@golmok.test';

DELETE rt FROM refresh_tokens rt
JOIN users u ON u.id = rt.user_id
WHERE u.email LIKE '%@golmok.test';

DELETE ur FROM user_regions ur
JOIN users u ON u.id = ur.user_id
WHERE u.email LIKE '%@golmok.test';

DELETE FROM users WHERE email LIKE '%@golmok.test';

SELECT
  (SELECT COUNT(*) FROM users) AS users_left,
  (SELECT COUNT(*) FROM products) AS products_left,
  (SELECT COUNT(*) FROM favorites) AS favorites_left;
