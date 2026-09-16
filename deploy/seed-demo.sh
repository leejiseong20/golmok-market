#!/usr/bin/env bash
# ============================================================
# 운영 서버에 데모 데이터를 넣는다. 서버에서 deploy 폴더 안에서 실행한다.
#   ./seed-demo.sh
#
# 순서가 중요하다.
#  1) 데모 계정은 실제 가입 API 로 만든다. 비밀번호를 BCrypt 로 저장해야 로그인이 되기 때문이다.
#  2) 계정이 생긴 뒤에 상품·찜·거래 SQL 을 넣는다(seed_demo_data.sql 이 이메일로 계정을 찾는다).
#
# 여러 번 실행하면 상품이 중복으로 들어간다. 다시 넣으려면 먼저 clean_demo_data.sql 을 실행한다.
# ============================================================
set -euo pipefail

cd "$(dirname "$0")"
set -a
source .env
set +a

API="https://${DOMAIN}/api"

signup() {
  local email="$1" nickname="$2"
  local status
  status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "${API}/auth/signup" \
    -H 'Content-Type: application/json' \
    --data-binary "{\"email\":\"${email}\",\"password\":\"Golmok123!\",\"nickname\":\"${nickname}\"}")
  case "$status" in
    201) echo "  가입: ${email}" ;;
    409) echo "  이미 있음: ${email}" ;;
    *)   echo "  실패(${status}): ${email} — 서버가 떠 있는지, DOMAIN 이 맞는지 확인하세요" >&2; exit 1 ;;
  esac
}

echo "1) 데모 계정 가입"
signup demo1@golmok.test 역삼이웃
signup demo2@golmok.test 서교상회
signup demo3@golmok.test 성수살림
signup demo4@golmok.test 골목이웃

echo "2) 상품·찜·거래 데이터"
docker compose exec -T mysql \
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 golmok \
  < ../scripts/seed_demo_data.sql

echo "완료. https://${DOMAIN}/api/products?regionId=1 로 확인하세요."
