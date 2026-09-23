#!/usr/bin/env bash
# 백업 파일로 되돌린다. 복구는 드물게, 급할 때 하는 일이라 절차를 스크립트로 굳혀 둔다.
#
#   ./restore.sh golmok_restore_check ~/backups/golmok-db-2026-09-23.sql.gz            # 연습용 임시 DB
#   ./restore.sh golmok ~/backups/golmok-db-2026-09-23.sql.gz ~/backups/golmok-uploads-2026-09-23.tar.gz
#
# 대상 DB 이름을 반드시 직접 적어야 한다. 기본값을 두면 연습하려다 운영 DB 를 덮어쓴다.
# 운영 DB(golmok)에 복구하려면 CONFIRM=yes 를 함께 준다.
set -euo pipefail

cd "$(dirname "$0")"
TARGET_DB="${1:-}"
DB_FILE="${2:-}"
UPLOADS_FILE="${3:-}"

if [ -z "$TARGET_DB" ] || [ -z "$DB_FILE" ]; then
  echo "사용법: ./restore.sh <대상DB> <db.sql.gz> [uploads.tar.gz]" >&2
  exit 1
fi
if [ ! -f "$DB_FILE" ]; then
  echo "백업 파일이 없다: $DB_FILE" >&2
  exit 1
fi
if [ "$TARGET_DB" = "golmok" ] && [ "${CONFIRM:-}" != "yes" ]; then
  echo "운영 DB(golmok)를 덮어쓰려 한다. 정말이라면 CONFIRM=yes ./restore.sh golmok ... 로 실행한다." >&2
  exit 1
fi

mysql_in() {
  # 비밀번호는 MYSQL_PWD 로 넘긴다(-p 로 주면 ps 에 보이고 "insecure" 경고가 뜬다).
  docker compose exec -T mysql sh -c "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" exec mysql -uroot --default-character-set=utf8mb4 $1"
}

# 덤프에 DROP TABLE IF EXISTS 가 들어 있어 기존 테이블은 덮어쓴다. DB 자체는 없으면 만든다.
mysql_in "" <<SQL
CREATE DATABASE IF NOT EXISTS $TARGET_DB
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SQL
zcat "$DB_FILE" | mysql_in "$TARGET_DB"
echo "DB 복구 완료 → $TARGET_DB"

if [ -n "$UPLOADS_FILE" ]; then
  if [ "$TARGET_DB" != "golmok" ]; then
    echo "사진은 운영 볼륨 하나뿐이라 연습 복구에서는 풀지 않는다(운영 파일을 덮어쓴다)." >&2
    exit 1
  fi
  docker compose exec -T app tar -C /data -xzf - < "$UPLOADS_FILE"
  echo "사진 복구 완료"
fi
