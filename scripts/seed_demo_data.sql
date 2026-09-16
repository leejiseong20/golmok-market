-- ============================================================
-- 데모용 시드 데이터 (개발/시연 전용. 운영 DB에 넣지 말 것)
--
-- 상품 쓰기 API가 아직 없어서 화면을 실제 데이터로 확인할 수 없다.
-- 그 사이를 메우는 임시 데이터이며, 상품 등록 API가 생기면 이 파일은 필요 없다.
--
-- 판매자·구매자 계정은 실제 가입 API(POST /api/auth/signup)로 먼저 만든다.
--   demo1@golmok.test 역삼이웃 / demo2@golmok.test 서교상회
--   demo3@golmok.test 성수살림 / demo4@golmok.test 골목이웃(찜 담당)
--   비밀번호는 모두 Golmok123! (데모 전용)
--
-- 실행:  mysql -u root -p golmok < scripts/seed_demo_data.sql
-- 삭제:  mysql -u root -p golmok < scripts/clean_demo_data.sql
--
-- 되돌리기 위해 모든 데모 계정 이메일은 @golmok.test 도메인을 쓴다.
-- 삭제 스크립트는 이 도메인만 골라 지운다.
-- ============================================================

SET NAMES utf8mb4;

SET @seller_yeoksam = (SELECT id FROM users WHERE email = 'demo1@golmok.test');
SET @seller_seogyo  = (SELECT id FROM users WHERE email = 'demo2@golmok.test');
SET @seller_seongsu = (SELECT id FROM users WHERE email = 'demo3@golmok.test');
SET @buyer          = (SELECT id FROM users WHERE email = 'demo4@golmok.test');

SET @region_yeoksam = (SELECT id FROM regions WHERE dong = '역삼동');
SET @region_seogyo  = (SELECT id FROM regions WHERE dong = '서교동');
SET @region_seongsu = (SELECT id FROM regions WHERE dong = '성수동');

SET @digital   = (SELECT id FROM categories WHERE name = '디지털');
SET @furniture = (SELECT id FROM categories WHERE name = '가구');
SET @clothes   = (SELECT id FROM categories WHERE name = '의류');
SET @appliance = (SELECT id FROM categories WHERE name = '생활가전');
SET @sports    = (SELECT id FROM categories WHERE name = '스포츠');
SET @baby      = (SELECT id FROM categories WHERE name = '유아');

-- 아래 값 중 NULL 이 있으면 계정·마스터 데이터가 없는 것이다.
-- 그대로 두면 "Column 'seller_id' cannot be null" 로 INSERT 가 멈춘다.
SELECT @seller_yeoksam, @seller_seogyo, @seller_seongsu, @buyer,
       @region_yeoksam, @region_seogyo, @region_seongsu;

-- ============================================================
-- 상품
--   bumped_at 을 어긋나게 둬서 최신순 정렬과 커서 페이징을 눈으로 확인할 수 있게 한다.
--   price 에 0(나눔)과 고가를 섞어 낮은가격순 정렬도 확인한다.
--   favorite_count 는 아래 favorites 와 정확히 맞춘다(비정규화 컬럼이라 어긋나면 화면이 거짓말을 한다).
--   chat_count 는 채팅 데이터가 없으므로 0 을 유지한다.
-- ============================================================
INSERT INTO products
  (seller_id, category_id, region_id, title, description, price, is_negotiable, status, trade_type,
   view_count, favorite_count, chat_count, bumped_at, created_at, updated_at)
VALUES
  (@seller_yeoksam, @furniture, @region_yeoksam, '원목 4인 식탁 (의자 2개 포함)',
   '3년 사용했고 상태 좋습니다. 상판에 생활 기스는 조금 있어요. 직접 보러 오시면 좋습니다.',
   80000, TRUE, 'ON_SALE', 'DIRECT', 0, 2, 0,
   NOW() - INTERVAL 40 MINUTE, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 40 MINUTE),

  (@seller_yeoksam, @digital, @region_yeoksam, '에어팟 프로 2세대',
   '작년에 구매했고 배터리 성능 좋습니다. 구성품 모두 있고 케이스만 생활 기스 있어요.',
   145000, FALSE, 'ON_SALE', 'DELIVERY', 0, 3, 0,
   NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 2 HOUR),

  (@seller_yeoksam, @appliance, @region_yeoksam, '캡슐 커피머신 거의 새것',
   '선물 받았는데 커피를 안 마셔서 내놓습니다. 두세 번 써봤고 캡슐 10개 같이 드려요.',
   55000, TRUE, 'ON_SALE', 'DIRECT', 0, 1, 0,
   NOW() - INTERVAL 6 HOUR, NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 6 HOUR),

  (@seller_yeoksam, @clothes, @region_yeoksam, '겨울 패딩 (여성 M)',
   '두 번 입었습니다. 검정색이고 기장은 무릎 위예요. 세탁 후 보관했습니다.',
   40000, TRUE, 'RESERVED', 'DIRECT', 0, 0, 0,
   NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 1 DAY),

  (@seller_yeoksam, @baby, @region_yeoksam, '아기 원목 장난감 나눔',
   '아이가 커서 정리합니다. 깨끗이 닦아뒀어요. 필요한 분 가져가세요.',
   0, FALSE, 'ON_SALE', 'DIRECT', 0, 4, 0,
   NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 2 DAY),

  (@seller_yeoksam, @sports, @region_yeoksam, '요가매트 6mm',
   '집에서 쓰던 요가매트입니다. 냄새 없고 미끄럼 방지 잘 됩니다.',
   12000, FALSE, 'SOLD', 'DIRECT', 0, 0, 0,
   NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 4 DAY),

  (@seller_seogyo, @digital, @region_seogyo, '아이패드 9세대 64GB',
   '인강용으로 쓰다가 노트북으로 넘어갑니다. 액정 깨끗하고 충전기 포함입니다.',
   270000, TRUE, 'ON_SALE', 'DELIVERY', 0, 2, 0,
   NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 3 HOUR),

  (@seller_seogyo, @furniture, @region_seogyo, '1인용 리클라이너 소파',
   '자취방에서 쓰던 소파입니다. 등받이 각도 조절되고 가죽 벗겨짐 없습니다.',
   95000, TRUE, 'ON_SALE', 'DIRECT', 0, 1, 0,
   NOW() - INTERVAL 8 HOUR, NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 8 HOUR),

  (@seller_seogyo, @clothes, @region_seogyo, '빈티지 데님 자켓',
   '홍대에서 구매한 데님 자켓입니다. 남녀공용이고 오버핏이에요.',
   35000, FALSE, 'ON_SALE', 'DIRECT', 0, 0, 0,
   NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 3 DAY),

  (@seller_seongsu, @appliance, @region_seongsu, '다이슨 무선청소기 V8',
   '필터 새로 교체했습니다. 흡입력 좋고 거치대까지 드립니다.',
   210000, TRUE, 'ON_SALE', 'DIRECT', 0, 3, 0,
   NOW() - INTERVAL 5 HOUR, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 HOUR),

  (@seller_seongsu, @sports, @region_seongsu, '자전거 헬멧 (성인용)',
   '한 시즌 쓰고 자전거를 팔아서 같이 정리합니다. 사이즈 조절 됩니다.',
   18000, FALSE, 'ON_SALE', 'DIRECT', 0, 0, 0,
   NOW() - INTERVAL 1 DAY - INTERVAL 5 HOUR, NOW() - INTERVAL 11 DAY, NOW() - INTERVAL 1 DAY - INTERVAL 5 HOUR),

  (@seller_seongsu, @furniture, @region_seongsu, '철제 책장 5단',
   '책 무게 잘 버팁니다. 나사 여분 있고 분해해서 가져가시면 됩니다.',
   30000, TRUE, 'ON_SALE', 'DIRECT', 0, 1, 0,
   NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 14 DAY, NOW() - INTERVAL 6 DAY);

-- ============================================================
-- 역삼동 추가 상품
--   프론트는 한 번에 20개를 요청한다. 역삼동 상품이 20개를 넘어야
--   "더 보기" 버튼과 커서 페이징이 화면에서 동작한다. (6 + 18 = 24개)
--   bumped_at 을 7시간 전부터 1시간 간격으로 흩어 놓아 정렬 순서를 눈으로 확인할 수 있다.
-- ============================================================
INSERT INTO products
  (seller_id, category_id, region_id, title, description, price, is_negotiable, status, trade_type,
   view_count, favorite_count, chat_count, bumped_at, created_at, updated_at)
VALUES
  (@seller_yeoksam, @digital,   @region_yeoksam, '기계식 키보드 적축',          '타건감 좋습니다. 키캡 따로 챙겨 드려요.',            48000, TRUE,  'ON_SALE', 'DELIVERY', 0, 0, 0, NOW() - INTERVAL  7 HOUR, NOW() - INTERVAL 15 DAY, NOW() - INTERVAL  7 HOUR),
  (@seller_yeoksam, @digital,   @region_yeoksam, '27인치 QHD 모니터',           '듀얼로 쓰다가 한 대 정리합니다. 불량화소 없어요.',  135000, TRUE,  'ON_SALE', 'DIRECT',   0, 0, 0, NOW() - INTERVAL  9 HOUR, NOW() - INTERVAL 15 DAY, NOW() - INTERVAL  9 HOUR),
  (@seller_yeoksam, @digital,   @region_yeoksam, '보조배터리 20000mAh',         '출장용으로 샀는데 거의 안 썼습니다.',                15000, FALSE, 'ON_SALE', 'DELIVERY', 0, 0, 0, NOW() - INTERVAL 11 HOUR, NOW() - INTERVAL 16 DAY, NOW() - INTERVAL 11 HOUR),
  (@seller_yeoksam, @furniture, @region_yeoksam, '접이식 좌식 테이블',          '혼자 밥 먹을 때 쓰기 좋아요. 접으면 자리 안 차지합니다.', 18000, FALSE, 'ON_SALE', 'DIRECT',   0, 0, 0, NOW() - INTERVAL 13 HOUR, NOW() - INTERVAL 16 DAY, NOW() - INTERVAL 13 HOUR),
  (@seller_yeoksam, @furniture, @region_yeoksam, '원목 협탁',                   '침대 옆에 두고 쓰던 협탁입니다. 서랍 한 칸 있어요.',  25000, TRUE,  'ON_SALE', 'DIRECT',   0, 0, 0, NOW() - INTERVAL 15 HOUR, NOW() - INTERVAL 17 DAY, NOW() - INTERVAL 15 HOUR),
  (@seller_yeoksam, @furniture, @region_yeoksam, '행거 옷걸이 2단',             '이사하면서 정리합니다. 나사 조임만 하면 됩니다.',    9000, FALSE, 'ON_SALE', 'DIRECT',   0, 0, 0, NOW() - INTERVAL 17 HOUR, NOW() - INTERVAL 17 DAY, NOW() - INTERVAL 17 HOUR),
  (@seller_yeoksam, @appliance, @region_yeoksam, '전기포트 1.7L',               '물 금방 끓고 보온도 됩니다. 내부 깨끗해요.',         13000, FALSE, 'ON_SALE', 'DIRECT',   0, 0, 0, NOW() - INTERVAL 19 HOUR, NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 19 HOUR),
  (@seller_yeoksam, @appliance, @region_yeoksam, '미니 제습기',                 '장마철에만 쓰던 제습기입니다. 소음 적어요.',         42000, TRUE,  'ON_SALE', 'DIRECT',   0, 0, 0, NOW() - INTERVAL 21 HOUR, NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 21 HOUR),
  (@seller_yeoksam, @appliance, @region_yeoksam, '스탠드형 선풍기',             '3단 풍속에 타이머 됩니다. 날개 세척해뒀어요.',       20000, FALSE, 'RESERVED','DIRECT',   0, 0, 0, NOW() - INTERVAL 23 HOUR, NOW() - INTERVAL 19 DAY, NOW() - INTERVAL 23 HOUR),
  (@seller_yeoksam, @clothes,   @region_yeoksam, '가죽 자켓 (남성 L)',          '두 시즌 입었고 손상 없습니다.',                      60000, TRUE,  'ON_SALE', 'DIRECT',   0, 0, 0, NOW() - INTERVAL 25 HOUR, NOW() - INTERVAL 19 DAY, NOW() - INTERVAL 25 HOUR),
  (@seller_yeoksam, @clothes,   @region_yeoksam, '운동화 270mm',                '몇 번 안 신었는데 발에 안 맞아서 내놓습니다.',       33000, FALSE, 'ON_SALE', 'DELIVERY', 0, 0, 0, NOW() - INTERVAL 27 HOUR, NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 27 HOUR),
  (@seller_yeoksam, @clothes,   @region_yeoksam, '니트 3벌 묶음 나눔',          '사이즈가 안 맞아 통째로 나눔합니다.',                    0, FALSE, 'ON_SALE', 'DIRECT',   0, 0, 0, NOW() - INTERVAL 29 HOUR, NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 29 HOUR),
  (@seller_yeoksam, @sports,    @region_yeoksam, '덤벨 5kg 2개',                '홈트용으로 쓰던 덤벨입니다. 코팅 벗겨짐 없어요.',    22000, TRUE,  'ON_SALE', 'DIRECT',   0, 0, 0, NOW() - INTERVAL 31 HOUR, NOW() - INTERVAL 21 DAY, NOW() - INTERVAL 31 HOUR),
  (@seller_yeoksam, @sports,    @region_yeoksam, '배드민턴 라켓 세트',          '라켓 2개와 셔틀콕 통 포함입니다.',                   16000, FALSE, 'ON_SALE', 'DIRECT',   0, 0, 0, NOW() - INTERVAL 33 HOUR, NOW() - INTERVAL 21 DAY, NOW() - INTERVAL 33 HOUR),
  (@seller_yeoksam, @sports,    @region_yeoksam, '캠핑 의자 2개',               '접이식이고 수납 가방 있습니다.',                     28000, TRUE,  'SOLD',    'DIRECT',   0, 0, 0, NOW() - INTERVAL 35 HOUR, NOW() - INTERVAL 22 DAY, NOW() - INTERVAL 35 HOUR),
  (@seller_yeoksam, @baby,      @region_yeoksam, '아기 띠',                     '허리 벨트형이라 편합니다. 세탁해뒀어요.',            30000, TRUE,  'ON_SALE', 'DIRECT',   0, 0, 0, NOW() - INTERVAL 37 HOUR, NOW() - INTERVAL 22 DAY, NOW() - INTERVAL 37 HOUR),
  (@seller_yeoksam, @baby,      @region_yeoksam, '유아 식탁 의자',              '식탁에 고정하는 방식입니다. 안전벨트 정상입니다.',   45000, TRUE,  'ON_SALE', 'DIRECT',   0, 0, 0, NOW() - INTERVAL 39 HOUR, NOW() - INTERVAL 23 DAY, NOW() - INTERVAL 39 HOUR),
  (@seller_yeoksam, @baby,      @region_yeoksam, '그림책 20권 일괄',            '아이가 다 읽어서 정리합니다. 찢어진 곳 없어요.',     35000, FALSE, 'ON_SALE', 'DIRECT',   0, 0, 0, NOW() - INTERVAL 41 HOUR, NOW() - INTERVAL 23 DAY, NOW() - INTERVAL 41 HOUR);

-- ============================================================
-- 상품 이미지
--   sort_order 0 이 썸네일이다. 일부 상품은 이미지를 비워 두어
--   thumbnailUrl 이 null 일 때의 화면도 확인할 수 있게 한다.
--   외부 이미지(picsum.photos)를 쓰므로 인터넷이 없으면 깨져 보인다.
-- ============================================================
INSERT INTO product_images (product_id, image_url, sort_order, created_at)
SELECT p.id, CONCAT('https://picsum.photos/seed/golmok', p.id, '-', n.i, '/600/450'), n.i, NOW()
FROM products p
JOIN (SELECT 0 AS i UNION ALL SELECT 1 UNION ALL SELECT 2) n
  ON n.i = 0
  OR (n.i = 1 AND p.price >= 30000)
  OR (n.i = 2 AND p.price >= 100000)
WHERE p.seller_id IN (@seller_yeoksam, @seller_seogyo, @seller_seongsu)
  -- 이미지 없는 상품 하나는 남겨 둔다
  AND p.title <> '요가매트 6mm';

-- ============================================================
-- 찜 (favorite_count 와 개수를 맞춘다)
--   한 사람이 같은 상품을 두 번 찜할 수 없으므로(uk_favorite),
--   count 가 2 이상인 상품은 판매자들도 서로 찜한 것으로 채운다.
-- ============================================================
INSERT INTO favorites (user_id, product_id, created_at)
SELECT u.id, p.id, NOW()
FROM products p
JOIN (
  SELECT @buyer AS id, 1 AS rank_no
  UNION ALL SELECT @seller_seogyo, 2
  UNION ALL SELECT @seller_seongsu, 3
  UNION ALL SELECT @seller_yeoksam, 4
) u ON u.rank_no <= p.favorite_count
WHERE p.favorite_count > 0
  AND p.seller_id IN (@seller_yeoksam, @seller_seogyo, @seller_seongsu)
  AND u.id <> p.seller_id;

-- 자기 상품은 찜에서 빠지므로 실제 행 수에 맞춰 카운터를 다시 계산한다.
UPDATE products p
SET p.favorite_count = (SELECT COUNT(*) FROM favorites f WHERE f.product_id = p.id)
WHERE p.seller_id IN (@seller_yeoksam, @seller_seogyo, @seller_seongsu);

-- ============================================================
-- 데모 거래 (구매내역 화면용)
--   거래 생성 API 는 채팅·결제와 함께 2단계에서 만든다. 그 전까지 화면을 볼 수 있도록
--   구매자(@buyer) 기준으로 상태별 거래를 만들어 둔다.
--
--   주의 1: trades 의 active_product_id 는 생성컬럼이고 UNIQUE 다.
--           취소/환불이 아닌 거래는 상품당 1건만 존재할 수 있으므로 상품을 겹치지 않게 고른다.
--   주의 2: 진행 중 거래의 상품은 예약중, 확정된 거래의 상품은 판매완료여야 앞뒤가 맞는다.
-- ============================================================
INSERT INTO trades (product_id, chat_room_id, seller_id, buyer_id, amount, status, completed_at, created_at, updated_at)
SELECT p.id, NULL, p.seller_id, @buyer, p.price, t.status, t.completed_at, t.created_at, t.created_at
FROM (
  SELECT '에어팟 프로 2세대'   AS title, 'PAID'      AS status, NULL AS completed_at, NOW() - INTERVAL 2 DAY  AS created_at
  UNION ALL SELECT '아이패드 9세대 64GB',  'SHIPPING',  NULL,                     NOW() - INTERVAL 4 DAY
  UNION ALL SELECT '요가매트 6mm',        'CONFIRMED', NOW() - INTERVAL 6 DAY,   NOW() - INTERVAL 9 DAY
  UNION ALL SELECT '캠핑 의자 2개',       'CONFIRMED', NOW() - INTERVAL 12 DAY,  NOW() - INTERVAL 15 DAY
  UNION ALL SELECT '빈티지 데님 자켓',     'CANCELED',  NULL,                     NOW() - INTERVAL 7 DAY
) t
JOIN products p ON p.title = t.title
WHERE p.seller_id IN (@seller_yeoksam, @seller_seogyo, @seller_seongsu);

-- 거래 상태에 맞춰 상품 상태를 맞춘다. 진행 중이면 예약중, 확정이면 판매완료.
UPDATE products p
JOIN trades t ON t.product_id = p.id AND t.buyer_id = @buyer
SET p.status = CASE
      WHEN t.status = 'CONFIRMED' THEN 'SOLD'
      WHEN t.status IN ('REQUESTED', 'PAID', 'SHIPPING') THEN 'RESERVED'
      ELSE p.status
    END
WHERE p.seller_id IN (@seller_yeoksam, @seller_seogyo, @seller_seongsu);

SELECT
  (SELECT COUNT(*) FROM trades WHERE buyer_id = @buyer) AS trades,
  (SELECT COUNT(*) FROM products WHERE seller_id IN (@seller_yeoksam, @seller_seogyo, @seller_seongsu)) AS products,
  (SELECT COUNT(*) FROM product_images pi JOIN products p ON p.id = pi.product_id
    WHERE p.seller_id IN (@seller_yeoksam, @seller_seogyo, @seller_seongsu)) AS images,
  (SELECT COUNT(*) FROM favorites f JOIN products p ON p.id = f.product_id
    WHERE p.seller_id IN (@seller_yeoksam, @seller_seogyo, @seller_seongsu)) AS favorites;
