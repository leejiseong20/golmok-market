#!/usr/bin/env bash
# DuckDNS 도메인을 이 서버의 현재 공인 IP 로 갱신한다.
#
# AWS Academy Learner Lab 은 세션이 끝나면 EC2 를 멈추고, 다시 켜면 공인 IP 가 바뀐다.
# (탄력적 IP 를 붙이면 고정되지만, 인스턴스가 꺼져 있는 동안에도 요금이 나가 예산을 갉아먹는다.)
# 그래서 부팅할 때와 5분마다 이 스크립트가 도메인을 지금 IP 로 맞춘다.
#
# 값은 같은 폴더의 .env 에서 읽는다(커밋하지 않는 파일):
#   DOMAIN=golmok-api.duckdns.org   또는  DUCKDNS_SUBDOMAIN=golmok-api
#   DUCKDNS_TOKEN=... (duckdns.org 로그인 후 화면 위쪽에 있는 token)
set -euo pipefail

cd "$(dirname "$0")"
if [ ! -f .env ]; then
  echo "deploy/.env 가 없다. .env.example 을 복사해 채운다." >&2
  exit 1
fi
# shellcheck disable=SC1091
set -a; . ./.env; set +a

SUBDOMAIN="${DUCKDNS_SUBDOMAIN:-${DOMAIN%%.duckdns.org}}"
TOKEN="${DUCKDNS_TOKEN:-}"
if [ -z "$SUBDOMAIN" ] || [ -z "$TOKEN" ]; then
  echo "DUCKDNS_SUBDOMAIN(또는 DOMAIN)과 DUCKDNS_TOKEN 을 .env 에 설정한다." >&2
  exit 1
fi

# EC2 메타데이터 서비스(IMDSv2)에서 공인 IP 를 읽는다. 토큰을 먼저 받아야 한다.
META_TOKEN="$(curl -fsS -X PUT "http://169.254.169.254/latest/api/token" \
  -H "X-aws-ec2-metadata-token-ttl-seconds: 60" || true)"
if [ -n "$META_TOKEN" ]; then
  IP="$(curl -fsS -H "X-aws-ec2-metadata-token: $META_TOKEN" \
    "http://169.254.169.254/latest/meta-data/public-ipv4" || true)"
fi
# 메타데이터를 못 읽는 환경(EC2 가 아닌 곳)에서는 DuckDNS 가 요청한 쪽 IP 로 잡게 비워 둔다.
IP="${IP:-}"

RESULT="$(curl -fsS "https://www.duckdns.org/update?domains=${SUBDOMAIN}&token=${TOKEN}&ip=${IP}")"
if [ "$RESULT" != "OK" ]; then
  echo "DuckDNS 갱신 실패: ${RESULT}" >&2
  exit 1
fi
echo "DuckDNS 갱신 완료: ${SUBDOMAIN}.duckdns.org -> ${IP:-요청 IP}"
