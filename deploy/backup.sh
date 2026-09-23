#!/usr/bin/env bash
# DB 와 업로드 사진을 ~/backups 에 받는다. 매일 timer 가 실행하고, 손으로도 실행할 수 있다.
#
# 서버가 회수되거나(상시 무료 서버) 랩 세션이 끝나 사라질 때를 대비한 것이다.
# **서버 안에만 두면 서버와 함께 사라진다.** 정기적으로 내 PC 로 가져와야 한다(README "백업" 참고).
#
# 비밀번호는 명령줄에 적지 않는다. 컨테이너 안의 환경변수를 MYSQL_PWD 로 넘긴다
# (-p 로 주면 ps 에 보이고 "insecure" 경고도 뜬다).
set -euo pipefail

cd "$(dirname "$0")"
OUT="${BACKUP_DIR:-$HOME/backups}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
STAMP=$(date +%F)
umask 077
mkdir -p "$OUT"

DB_FILE="$OUT/golmok-db-$STAMP.sql.gz"
UPLOADS_FILE="$OUT/golmok-uploads-$STAMP.tar.gz"

# --single-transaction: 쓰기를 막지 않고 한 시점의 일관된 덤프를 받는다(InnoDB).
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers \
     --default-character-set=utf8mb4 golmok' | gzip > "$DB_FILE"

docker compose exec -T app tar -C /data -czf - uploads > "$UPLOADS_FILE"

# 받다가 끊긴 파일을 "백업했다"고 믿으면 안 된다. 덤프 끝 표시와 압축 무결성을 본다.
if ! zcat "$DB_FILE" | tail -1 | grep -q "Dump completed"; then
  echo "DB 덤프가 끝까지 받아지지 않았다: $DB_FILE" >&2
  exit 1
fi
if ! gzip -t "$UPLOADS_FILE"; then
  echo "사진 압축 파일이 깨졌다: $UPLOADS_FILE" >&2
  exit 1
fi

TABLES=$(zcat "$DB_FILE" | grep -c "^CREATE TABLE")
PHOTOS=$(tar -tzf "$UPLOADS_FILE" | grep -vc "/$" || true)
echo "$(date +'%F %T') 백업 완료 · 테이블 ${TABLES}개 · 사진 ${PHOTOS}장 · $(du -h "$DB_FILE" | cut -f1)/$(du -h "$UPLOADS_FILE" | cut -f1)"

# 오래된 백업 정리. 디스크가 차면 MySQL 이 먼저 멈춘다.
find "$OUT" -maxdepth 1 -name 'golmok-*-*.gz' -mtime +"$KEEP_DAYS" -print -delete
