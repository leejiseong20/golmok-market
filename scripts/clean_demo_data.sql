-- ============================================================
-- 데모 데이터 삭제. seed_demo_data.sql 로 넣은 것만 지운다.
--
-- 기준은 @golmok.test 이메일이다. 이 도메인은 예약된 테스트용 도메인이라
-- 실제 사용자 계정과 겹치지 않는다.
--
-- 실행: mysql -u root -p golmok < scripts/clean_demo_data.sql
--
-- 지우는 순서는 외래키 방향의 역순이다.
-- (favorites·reviews·trades·chat_rooms·images → products → notifications·refresh_tokens·user_regions → users)
-- ============================================================

SET NAMES utf8mb4;

-- mysql 을 --force 없이 실행한다. 오류로 연결이 끝나면 아직 커밋하지 않은 삭제는 모두 롤백된다.
START TRANSACTION;

SET @demo_users = (SELECT GROUP_CONCAT(id) FROM users WHERE email LIKE '%@golmok.test');

DELETE f FROM favorites f
JOIN products p ON p.id = f.product_id
JOIN users u ON u.id = p.seller_id
WHERE u.email LIKE '%@golmok.test';

-- 데모 사용자가 남의 상품을 찜한 경우까지 정리한다.
DELETE f FROM favorites f
JOIN users u ON u.id = f.user_id
WHERE u.email LIKE '%@golmok.test';

-- 후기는 거래·회원을 참조하므로 거래보다 먼저 지운다. 후기를 쓰고 받는 사람은 항상 거래 당사자이므로
-- 데모 회원이 낀 거래의 후기만 지우면 데모 회원이 남긴·받은 후기가 모두 빠진다.
-- 주의: 이미 반영된 매너온도는 되돌리지 않는다. 데모 회원이 실계정에 후기를 남겼다면 그 실계정의 온도는 그대로다.
DELETE r FROM reviews r
JOIN trades t ON t.id = r.trade_id
JOIN users u ON u.id = t.buyer_id OR u.id = t.seller_id
WHERE u.email LIKE '%@golmok.test';

-- 거래는 상품·회원을 참조하므로 먼저 지운다.
DELETE t FROM trades t
JOIN users u ON u.id = t.buyer_id OR u.id = t.seller_id
WHERE u.email LIKE '%@golmok.test';

-- 채팅방은 상품·회원을 참조한다. 메시지는 ON DELETE CASCADE 로 함께 지워진다.
-- 메시지를 보낸 사람은 항상 방의 참여자이므로 데모 회원이 낀 방만 지우면 남는 메시지가 없다.
-- 거래(trades.chat_room_id)가 방을 참조하므로 거래를 지운 뒤에 지운다.
DELETE r FROM chat_rooms r
JOIN users u ON u.id = r.buyer_id OR u.id = r.seller_id
WHERE u.email LIKE '%@golmok.test';

DELETE pi FROM product_images pi
JOIN products p ON p.id = pi.product_id
JOIN users u ON u.id = p.seller_id
WHERE u.email LIKE '%@golmok.test';

DELETE p FROM products p
JOIN users u ON u.id = p.seller_id
WHERE u.email LIKE '%@golmok.test';

-- 알림은 회원을 참조한다. 데모 회원이 받은 알림만 지운다.
-- 실계정이 받은 알림(예: 데모 회원이 찜함)은 대상 경로만 남고 외래키가 없어 그대로 둔다.
DELETE n FROM notifications n
JOIN users u ON u.id = n.user_id
WHERE u.email LIKE '%@golmok.test';

DELETE rt FROM refresh_tokens rt
JOIN users u ON u.id = rt.user_id
WHERE u.email LIKE '%@golmok.test';

DELETE ur FROM user_regions ur
JOIN users u ON u.id = ur.user_id
WHERE u.email LIKE '%@golmok.test';

-- 데모 회원이 낸 신고만 지운다. 실회원이 남긴 신고는 대상이 없어져도 감사 기록으로 남긴다.
DELETE r FROM reports r JOIN users u ON u.id = r.reporter_id WHERE u.email LIKE '%@golmok.test';
DELETE b FROM blocks b JOIN users u ON u.id = b.blocker_id OR u.id = b.blocked_id
WHERE u.email LIKE '%@golmok.test';
DELETE s FROM push_subscriptions s JOIN users u ON u.id = s.user_id WHERE u.email LIKE '%@golmok.test';

DELETE FROM users WHERE email LIKE '%@golmok.test';

COMMIT;

SELECT
  (SELECT COUNT(*) FROM users) AS users_left,
  (SELECT COUNT(*) FROM products) AS products_left,
  (SELECT COUNT(*) FROM favorites) AS favorites_left,
  (SELECT COUNT(*) FROM chat_rooms) AS chat_rooms_left,
  (SELECT COUNT(*) FROM reviews) AS reviews_left,
  (SELECT COUNT(*) FROM notifications) AS notifications_left;
