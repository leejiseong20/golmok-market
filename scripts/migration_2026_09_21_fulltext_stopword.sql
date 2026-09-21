-- ============================================================
-- 상품 검색 FULLTEXT 인덱스를 불용어 없이 다시 만든다 (2026-09-21)
--
-- 왜: ngram 파서는 불용어를 "포함한" 2글자 조각을 색인에서 뺀다. 기본 불용어 목록에 영어 한 글자
--     a · i 가 있어서 ipad · air · mini · adidas 같은 검색이 실패한다. 한글 검색은 영향이 없다.
--     (로컬 MySQL 8.0.44 로 확인: 불용어를 켜고 만들면 33개 검색어 중 6개 실패, 끄고 만들면 0개)
--
-- 순서가 중요하다. 이 스크립트를 먼저 실행하고 그다음에 새 이미지를 올린다.
-- 옛 이미지는 LIKE 로 검색하므로 인덱스를 먼저 바꿔도 아무 영향이 없다.
-- 반대로 새 이미지를 먼저 올리면, 인덱스를 바꾸기 전까지 영어 검색 일부가 실패한다.
--
--   서버(Ubuntu)에서:
--   cd ~/golmok-market && git pull && cd deploy
--   docker compose exec -T mysql sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" golmok' < ../scripts/migration_2026_09_21_fulltext_stopword.sql
--   docker compose pull app && docker compose up -d app
--
-- 지우기와 만들기를 **두 문장으로 나눠야 한다.** 불용어 설정은 인덱스가 아니라 테이블에 저장되는데,
-- 한 ALTER 문 안에서 지우고 만들면 예전 설정이 그대로 남는다(로컬에서 실제로 그랬다).
-- 상품 수가 적어 몇 초 안에 끝난다. 그동안 검색은 LIKE(옛 이미지)라 서비스에 영향이 없다.
-- ============================================================
SET NAMES utf8mb4;

SET SESSION innodb_ft_enable_stopword = OFF;

ALTER TABLE products DROP INDEX ft_products_search;
ALTER TABLE products ADD FULLTEXT INDEX ft_products_search (title, description) WITH PARSER ngram;

-- 확인: 한 줄이 나오면 된다.
SHOW INDEX FROM products WHERE Key_name = 'ft_products_search' AND Seq_in_index = 1;
