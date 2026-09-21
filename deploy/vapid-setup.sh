#!/usr/bin/env bash
# 서버에서 VAPID 키 쌍을 만들어 deploy/.env 에 넣는다. 개인키는 화면·기록에 남기지 않는다.
# 이미 키가 있으면 아무것도 하지 않는다 — 키를 바꾸면 기존 구독이 모두 무효가 된다.
#
# 파이프(… | tail | head)로 자르지 않는다. head 가 먼저 끝나면 앞 명령이 SIGPIPE 로 죽고,
# pipefail 때문에 스크립트가 조용히 빠져나간다(첫 시도에서 실제로 그랬다). 파일로 받아 dd 로 자른다.
set -euo pipefail
cd ~/golmok-market/deploy

if grep -q '^VAPID_PRIVATE_KEY=..' .env; then
  echo "== VAPID 키가 이미 있어 새로 만들지 않는다 =="
else
  umask 077
  work=$(mktemp -d)
  trap 'rm -rf "$work"' EXIT
  openssl ecparam -name prime256v1 -genkey -noout -out "$work/vapid.pem"
  openssl ec -in "$work/vapid.pem" -outform DER -out "$work/priv.der" 2>/dev/null
  openssl ec -in "$work/vapid.pem" -pubout -outform DER -out "$work/pub.der" 2>/dev/null

  # SEC1 DER(P-256) 머리: 30 77 02 01 01 04 20 → 바로 뒤 32바이트가 개인키다. 형식이 다르면 멈춘다.
  header=$(od -An -tx1 -N7 "$work/priv.der" | tr -d ' \n')
  [ "$header" = "30770201010420" ] || { echo "개인키 DER 머리가 예상과 다르다: $header"; exit 1; }
  priv=$(dd if="$work/priv.der" bs=1 skip=7 count=32 status=none | base64 -w0 | tr '+/' '-_' | tr -d '=')
  # SPKI DER 의 마지막 65바이트가 비압축 공개키(0x04 + X + Y)
  size=$(stat -c %s "$work/pub.der")
  pub=$(dd if="$work/pub.der" bs=1 skip=$((size - 65)) count=65 status=none | base64 -w0 | tr '+/' '-_' | tr -d '=')

  # 길이 검사: 32바이트 → 43자, 65바이트 → 87자. 공개키는 0x04 로 시작하므로 base64 첫 글자가 B 다.
  [ "${#priv}" -eq 43 ] && [ "${#pub}" -eq 87 ] && [ "${pub:0:1}" = "B" ] \
    || { echo "키 모양이 이상하다(개인 ${#priv}자, 공개 ${#pub}자)"; exit 1; }

  cp .env ".env.bak.$(date +%Y%m%d%H%M%S)"
  printf '\n# 웹 푸시(VAPID) — %s 서버에서 생성\nVAPID_PUBLIC_KEY=%s\nVAPID_PRIVATE_KEY=%s\n' "$(date +%F)" "$pub" "$priv" >> .env
  echo "== VAPID 키를 만들어 .env 에 넣었다(개인키는 출력하지 않음) =="
fi

echo "VAPID 줄 수: $(grep -c '^VAPID_' .env)"
echo "공개키(공개해도 되는 값): $(grep '^VAPID_PUBLIC_KEY=' .env | cut -d= -f2-)"
echo ".env 권한: $(stat -c '%a %U' .env)"
