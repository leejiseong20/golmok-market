# 골목마켓 배포 안내 (AWS EC2 + Vercel)

```
사용자 ─HTTPS→ Vercel (React 프론트)
                  │  /api/* 를 백엔드로 전달
                  ▼
        EC2 한 대 (Docker Compose)
         ├─ Caddy       80/443, HTTPS 인증서 자동 발급
         ├─ Spring Boot  (외부 노출 없음)
         ├─ MySQL 8      (외부 노출 없음)
         └─ 업로드 이미지 볼륨
```

이 구성은 로컬 Docker에서 실제로 띄워 확인했다. 스키마 자동 적용, HTTPS 경유 API, CORS 허용/차단,
데모 데이터 적재, 이미지 업로드·서빙, 앱 컨테이너 재생성 후 이미지 유지, 운영 로그에 SQL 값이 남지 않음,
메모리(앱 약 290MB · MySQL 약 160MB · Caddy 약 15MB).

> **계정 가입·결제 정보 입력은 직접 해야 한다.** 아래 순서대로 진행한다.

---

## 0. 비용 안전장치 (가장 먼저)

AWS 무료 혜택은 기간·한도가 있다. 가입 조건을 확인하고, **리소스를 만들기 전에 예산 알림부터 설정한다.**

1. AWS 콘솔 → **Billing and Cost Management → Budgets → Create budget**
2. 템플릿 **Zero spend budget**(또는 월 1달러) 선택 → 알림 받을 이메일 입력

요금이 나오기 쉬운 것: RDS, NAT 게이트웨이, 연결되지 않은 탄력적 IP, 30GB를 넘는 디스크, 켜둔 채 잊은 인스턴스.
이 구성은 RDS·NAT를 쓰지 않는다.

---

## 1. 무료 도메인 (DuckDNS)

1. https://www.duckdns.org 에 로그인 → 원하는 이름으로 서브도메인 추가 (예: `golmok-api`)
2. **IP는 3단계에서 EC2를 만든 뒤** 그 공인 IP로 입력한다
3. 최종 주소: `golmok-api.duckdns.org` — 이하 `<도메인>`

---

## 2. 백엔드 이미지 준비 (GitHub)

백엔드 저장소 `main`에 푸시하면 GitHub Actions(`.github/workflows/docker-image.yml`)가
테스트 → 이미지 빌드 → `ghcr.io/leejiseong20/golmok-market:latest` 업로드를 한다.

**EC2(메모리 1GB)에서 직접 빌드하지 않는 이유**: Gradle 빌드가 메모리를 많이 써서 매우 느리거나 실패한다.

첫 업로드 후 **이미지를 공개로 바꾼다.** (비공개면 EC2에서 토큰으로 로그인해야 한다)

1. GitHub 프로필 → **Packages → golmok-market → Package settings**
2. **Change visibility → Public**

---

## 3. EC2 만들기

AWS 콘솔 → **EC2 → 인스턴스 시작**

| 항목 | 값 |
|---|---|
| 리전 | 아시아 태평양(서울) `ap-northeast-2` |
| AMI | Ubuntu Server 24.04 LTS |
| 인스턴스 유형 | **t3.micro** (프리 티어 표시 확인). **ARM(t4g) 말고 x86** — 이미지를 x86으로 빌드한다 |
| 키 페어 | 새로 만들고 `.pem` 파일을 안전한 곳에 보관 |
| 스토리지 | gp3 **20GB** (무료 한도 30GB 이내) |
| 보안 그룹 인바운드 | SSH 22 → **내 IP만** / HTTP 80 → 0.0.0.0/0 / HTTPS 443 → 0.0.0.0/0 |

**3306(MySQL)과 8080(앱)은 열지 않는다.** Compose가 외부에 노출하지 않는다.

인스턴스를 껐다 켜면 공인 IP가 바뀐다. 고정하려면 **탄력적 IP**를 만들어 인스턴스에 연결한다
(연결하지 않은 탄력적 IP는 요금이 나온다). 공인 IPv4 주소 자체에도 과금 정책이 있으니 가입 조건에서 확인한다.

만들어지면 공인 IP를 DuckDNS에 입력한다.

---

## 4. 서버 준비 (SSH 접속 후)

```bash
ssh -i golmok.pem ubuntu@<공인 IP>
```

**스왑 2GB** — 메모리 1GB에서 앱과 MySQL을 함께 돌리기 위한 안전판.

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

**Docker 설치**

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit   # 다시 접속해야 docker 그룹이 적용된다
```

---

## 5. 실행

```bash
git clone https://github.com/leejiseong20/golmok-market.git
cd golmok-market/deploy
cp .env.example .env
```

비밀값을 새로 만든다. **로컬에서 쓰던 값을 재사용하지 않는다.**

```bash
echo "MYSQL_ROOT_PASSWORD=$(openssl rand -base64 24)"
echo "DB_PASSWORD=$(openssl rand -base64 24)"
echo "JWT_SECRET=$(openssl rand -base64 32)"
```

`nano .env`로 열어 채운다.

- `DOMAIN` = `<도메인>` (https:// 없이)
- `CORS_ALLOWED_ORIGINS` = 아직 Vercel 주소가 없으면 임시로 `https://example.com`. **7단계에서 반드시 고친다**
- 위에서 만든 비밀번호 3개

```bash
docker compose up -d
docker compose logs -f app    # "Started GolmokMarketApplication" 이 나오면 Ctrl+C
curl https://<도메인>/api/categories
```

첫 기동 때 `golmok_schema_v2.sql`이 자동으로 실행된다(DB 볼륨이 비어 있을 때만).
HTTPS 인증서는 Caddy가 자동 발급한다. 1~2분 걸릴 수 있다.

---

## 6. 데모 데이터

```bash
chmod +x seed-demo.sh
./seed-demo.sh
```

데모 계정 4개를 가입 API로 만들고, 상품 30개·찜·거래를 넣는다. 로그인: `demo4@golmok.test` / `Golmok123!`

**주의**: 데모 비밀번호가 공개돼 있으므로 누구나 로그인해 데모 상품을 고치거나 지울 수 있다.
망가지면 다시 넣는다.

```bash
docker compose exec -T mysql mysql -uroot -p"$(grep MYSQL_ROOT_PASSWORD .env | cut -d= -f2-)" golmok < ../scripts/clean_demo_data.sql
./seed-demo.sh
```

---

## 7. 프론트 (Vercel)

1. 프론트 저장소의 `vercel.json`에서 `YOUR_SUBDOMAIN.duckdns.org`를 `<도메인>`으로 바꿔 커밋·푸시
2. https://vercel.com → **Add New → Project** → `golmok-market-frontend` 가져오기
   (Framework: Vite, 나머지 기본값) → **Deploy**
3. 발급된 주소 확인 (예: `https://golmok-market-frontend.vercel.app`)
4. **EC2의 `.env`에서 `CORS_ALLOWED_ORIGINS`를 그 주소로 바꾸고 앱을 재시작**

```bash
nano .env
docker compose up -d app
```

**이 단계를 빠뜨리면 목록은 보이는데 로그인만 403이 난다.**
Vercel이 요청을 전달할 때 브라우저의 Origin(Vercel 주소)이 그대로 백엔드에 가기 때문이다.

---

## 새 버전 배포

1. 백엔드 `main`에 푸시 → GitHub Actions 완료 확인
2. EC2에서

```bash
cd ~/golmok-market && git pull     # 설정 파일이 바뀌었을 수 있다
cd deploy
docker compose pull app
docker compose up -d app
```

업로드 이미지와 DB는 볼륨에 있어 컨테이너를 바꿔도 남는다.
**`docker compose down -v`는 볼륨(DB·이미지·인증서)까지 지운다. 운영에서 쓰지 않는다.**

---

## 백업

```bash
docker compose exec -T mysql mysqldump -uroot -p"$(grep MYSQL_ROOT_PASSWORD .env | cut -d= -f2-)" \
  --single-transaction --default-character-set=utf8mb4 golmok > backup-$(date +%F).sql
```

---

## 문제 해결

| 증상 | 원인 · 조치 |
|---|---|
| 목록은 되는데 로그인·가입만 403 | `CORS_ALLOWED_ORIGINS`에 Vercel 주소가 없다. 7-4단계 |
| `https://<도메인>` 접속 불가 | DuckDNS IP가 EC2 공인 IP와 다름 / 보안 그룹 80·443 미개방. `docker compose logs caddy` 확인 |
| 앱이 계속 재시작 | `docker compose logs app`. `.env` 값 누락이면 compose가 기동 전에 알려준다 |
| 카테고리·동네 이름이 깨짐 | 스키마 파일의 `SET NAMES utf8mb4`가 적용되기 전에 만든 DB. 데이터가 없다면 `docker compose down -v` 후 재기동 |
| 느려지거나 멈춤 | `free -h`로 스왑 확인. 4단계 스왑 설정 |
| 사진이 안 보임 | 데모 상품 사진은 외부(picsum.photos) 주소다. 직접 올린 사진은 `/api/images/...` |

## 알려진 한계

- **서버 한 대**라 인스턴스 장애 시 전체가 멈춘다. DB 백업은 수동이다.
- **이미지가 EC2 디스크 볼륨**에 있다. 인스턴스를 삭제하면 함께 사라진다. S3 이전은 후속 과제.
- 채팅(WebSocket)을 추가하면 Vercel을 거치지 못하므로 브라우저가 `wss://<도메인>`으로 직접 연결해야 한다.
  Caddy는 WebSocket을 그대로 전달하므로 서버 구성은 바꿀 필요가 없다.
