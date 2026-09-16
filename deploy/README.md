# 골목마켓 배포 안내 (Oracle Cloud 상시 무료 + Vercel)

```
사용자 ─HTTPS→ Vercel (React 프론트)
                  │  /api/* 를 백엔드로 전달
                  ▼
     Oracle Cloud 서버 한 대 (Docker Compose)
         ├─ Caddy       80/443, HTTPS 인증서 자동 발급
         ├─ Spring Boot  (외부 노출 없음)
         ├─ MySQL 8      (외부 노출 없음)
         └─ 업로드 이미지 볼륨
```

이 Compose 구성은 로컬 Docker에서 실제로 띄워 확인했다. 스키마 자동 적용, HTTPS 경유 API, CORS 허용/차단,
데모 데이터 적재, 이미지 업로드·서빙, 앱 컨테이너 재생성 후 이미지 유지, 운영 로그에 SQL 값이 남지 않음,
메모리(앱 약 290MB · MySQL 약 160MB · Caddy 약 15MB). ARM 이미지는 빌드와 `aarch64` 실행까지 확인했다.

> **계정 가입·카드 인증은 직접 해야 한다.** 요금·무료 한도·정책은 바뀔 수 있으니 가입 시 최신 조건을 확인한다.

---

## 0. Oracle Cloud 가입

1. https://www.oracle.com/cloud/free/ → **Start for free**
2. **홈 리전(Home Region)을 신중히 고른다. 나중에 바꿀 수 없고, 상시 무료 자원은 홈 리전에서만 만들 수 있다.**
   - 한국: 서울 `ap-seoul-1` 또는 춘천 `ap-chuncheon-1`
   - 인기 리전은 ARM 서버 자원이 부족해 생성이 안 될 때가 있다(3단계 참고)
3. 카드 인증을 요구한다. 인증용이며, 계정을 **유료(Pay As You Go)로 업그레이드하지 않으면 과금되지 않는다.**

### 알아둘 정책
- **오래 놀리는 상시 무료 서버는 회수(중지)될 수 있다.** 사용률이 매우 낮은 상태가 일정 기간 이어지는 경우다.
  시연용 사이트는 트래픽이 적어 해당될 수 있으므로 **DB 백업을 정기적으로 받는다**(아래 "백업").
- 유료로 업그레이드하면 이 회수 정책에서 벗어나지만 무료 한도를 넘는 사용분이 과금된다.
  업그레이드한다면 먼저 **Billing → Budgets**에서 예산 알림을 만든다.

---

## 1. 무료 도메인 (DuckDNS)

1. https://www.duckdns.org 에 로그인 → 서브도메인 추가 (예: `golmok-api`)
2. **IP는 3단계에서 서버를 만든 뒤** 그 공인 IP로 입력한다
3. 최종 주소: `golmok-api.duckdns.org` — 이하 `<도메인>`

---

## 2. 백엔드 이미지 (GitHub)

백엔드 저장소 `main`에 푸시하면 GitHub Actions(`.github/workflows/docker-image.yml`)가
테스트 → 이미지 빌드 → `ghcr.io/leejiseong20/golmok-market:latest` 업로드를 한다.

- **x86과 ARM을 같은 태그에 함께 올린다.** 서버가 자기 CPU에 맞는 것을 자동으로 받는다.
- 서버에서 직접 빌드하지 않는다. Gradle 빌드가 메모리를 많이 써서 작은 서버에서는 느리거나 실패한다.
- 이미지는 공개라 로그인 없이 받아진다. `docker compose pull`이 `unauthorized`로 실패하면
  GitHub 프로필 → **Packages → golmok-market → Package settings → Change visibility → Public**.

---

## 3. 서버 만들기

Oracle Cloud 콘솔 → **Compute → Instances → Create instance**

| 항목 | 값 |
|---|---|
| Image | **Canonical Ubuntu 24.04** |
| Shape (1순위) | **Ampere → VM.Standard.A1.Flex**, OCPU 2 / 메모리 12GB (상시 무료 한도 안) |
| Shape (대안) | 1순위가 "Out of capacity"로 안 되면 **AMD → VM.Standard.E2.1.Micro** (x86, 메모리 1GB) |
| Networking | 새 VCN + **공용(public) 서브넷**, **Assign a public IPv4 address** 체크 |
| SSH keys | **Generate a key pair** → 개인 키(`.key`) 다운로드해서 안전하게 보관 |
| Boot volume | 기본값 (상시 무료 블록 볼륨 한도 안) |

**"Out of capacity" 오류**는 코드 문제가 아니라 그 리전에 남은 무료 ARM 자원이 없다는 뜻이다.
OCPU·메모리를 1 / 6GB로 줄이거나, 다른 가용 도메인(Availability Domain)을 고르거나, 시간을 두고 다시 시도한다.
계속 안 되면 E2.1.Micro로 만든다. 이미지는 두 종류 모두 지원한다.

만들어지면 인스턴스 상세의 **Public IP**를 DuckDNS에 입력한다.
서버를 재시작해도 IP를 고정하려면 **Networking → Reserved public IPs**로 예약 IP를 만들어 붙인다.

---

## 4. 포트 열기 — 두 군데 모두 열어야 한다

**Oracle Cloud에서 가장 흔한 함정이다.** 콘솔에서만 열면 접속이 안 된다.

### 4-1. 클라우드 방화벽 (Security List)

인스턴스 상세 → **Subnet** 링크 → **Security Lists → Default Security List → Add Ingress Rules**

| Source CIDR | IP Protocol | Destination Port |
|---|---|---|
| `0.0.0.0/0` | TCP | `80` |
| `0.0.0.0/0` | TCP | `443` |

22(SSH)는 기본으로 열려 있다. **3306(MySQL)·8080(앱)은 열지 않는다.**

### 4-2. 서버 안 방화벽 (iptables)

Oracle의 Ubuntu 이미지는 **서버 안에서도** 22번 외 포트를 막아 둔다. SSH로 접속해서 연다.

```bash
chmod 600 ssh-key.key
ssh -i ssh-key.key ubuntu@<공인 IP>
```

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

`netfilter-persistent save`를 빼먹으면 재부팅 후 다시 막힌다.

---

## 5. 서버 준비

**Docker 설치** (x86·ARM 모두 같은 명령)

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit   # 다시 접속해야 docker 그룹이 적용된다
```

**E2.1.Micro(메모리 1GB)로 만든 경우에만 스왑 2GB** — 앱과 MySQL을 함께 돌리기 위한 안전판.
ARM(A1)은 메모리가 넉넉해 필요 없다.

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

---

## 6. 실행

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
- `CORS_ALLOWED_ORIGINS` = 아직 Vercel 주소가 없으면 임시로 `https://example.com`. **8단계에서 반드시 고친다**
- 위에서 만든 비밀번호 3개

```bash
docker compose up -d
docker compose logs -f app    # "Started GolmokMarketApplication" 이 나오면 Ctrl+C
curl https://<도메인>/api/categories
```

첫 기동 때 `golmok_schema_v2.sql`이 자동으로 실행된다(DB 볼륨이 비어 있을 때만).
HTTPS 인증서는 Caddy가 자동 발급한다. 1~2분 걸릴 수 있다.

---

## 7. 데모 데이터

```bash
chmod +x seed-demo.sh
./seed-demo.sh
```

데모 계정 4개를 가입 API로 만들고, 상품 30개·찜·거래를 넣는다. 로그인: `demo4@golmok.test` / `Golmok123!`

**주의**: 데모 비밀번호가 공개돼 있으므로 누구나 로그인해 데모 상품을 고치거나 지울 수 있다. 망가지면 다시 넣는다.

```bash
docker compose exec -T mysql mysql -uroot -p"$(grep MYSQL_ROOT_PASSWORD .env | cut -d= -f2-)" golmok < ../scripts/clean_demo_data.sql
./seed-demo.sh
```

---

## 8. 프론트 (Vercel)

1. 프론트 저장소의 `vercel.json`에서 `YOUR_SUBDOMAIN.duckdns.org`를 `<도메인>`으로 바꿔 커밋·푸시
2. https://vercel.com → **Add New → Project** → `golmok-market-frontend` 가져오기
   (Framework: Vite, 나머지 기본값) → **Deploy**
3. 발급된 주소 확인 (예: `https://golmok-market-frontend.vercel.app`)
4. **서버의 `.env`에서 `CORS_ALLOWED_ORIGINS`를 그 주소로 바꾸고 앱을 재시작**

```bash
nano .env
docker compose up -d app
```

**이 단계를 빠뜨리면 목록은 보이는데 로그인만 403이 난다.**
Vercel이 요청을 전달할 때 브라우저의 Origin(Vercel 주소)이 그대로 백엔드에 가기 때문이다.

---

## 새 버전 배포

1. 백엔드 `main`에 푸시 → GitHub Actions 완료 확인
2. 서버에서

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

서버가 회수되거나 망가질 때를 대비해 정기적으로 받아 **서버 밖(내 PC 등)에 보관**한다.

```bash
docker compose exec -T mysql mysqldump -uroot -p"$(grep MYSQL_ROOT_PASSWORD .env | cut -d= -f2-)" \
  --single-transaction --default-character-set=utf8mb4 golmok > backup-$(date +%F).sql
```

내 PC로 가져오기:

```bash
scp -i ssh-key.key ubuntu@<공인 IP>:~/golmok-market/deploy/backup-*.sql .
```

---

## 문제 해결

| 증상 | 원인 · 조치 |
|---|---|
| `https://<도메인>` 접속 불가·인증서 발급 실패 | **4단계 두 군데(Security List, iptables)를 모두 열었는지** 확인 / DuckDNS IP가 서버 공인 IP와 같은지. `docker compose logs caddy` |
| 목록은 되는데 로그인·가입만 403 | `CORS_ALLOWED_ORIGINS`에 Vercel 주소가 없다. 8-4단계 |
| 서버 생성 시 "Out of capacity" | 무료 ARM 자원 부족. 3단계의 대안(자원 줄이기, 다른 AD, E2.1.Micro) |
| `docker compose pull` 이 `no matching manifest` | 이미지에 서버 CPU 종류가 없다. GitHub Actions가 `linux/amd64,linux/arm64` 둘 다 올렸는지 확인 |
| 앱이 계속 재시작 | `docker compose logs app`. "JVM 기본 시간대가 …" 오류면 이미지를 바꾸지 말고 최신 이미지를 쓴다 |
| 카테고리·동네 이름이 깨짐 | 스키마 파일의 `SET NAMES utf8mb4` 적용 전에 만든 DB. 데이터가 없다면 `docker compose down -v` 후 재기동 |
| 느려지거나 멈춤 (E2.1.Micro) | `free -h`로 스왑 확인. 5단계 스왑 설정 |
| 사진이 안 보임 | 데모 상품 사진은 외부(picsum.photos) 주소다. 직접 올린 사진은 `/api/images/...` |

## 알려진 한계

- **서버 한 대**라 장애 시 전체가 멈춘다. DB 백업은 수동이다.
- **상시 무료 서버는 사용률이 낮으면 회수될 수 있다.** 백업을 정기적으로 받는다.
- **이미지가 서버 디스크 볼륨**에 있다. 서버를 지우면 함께 사라진다. 오브젝트 스토리지 이전은 후속 과제.
- 채팅(WebSocket)을 추가하면 Vercel을 거치지 못하므로 브라우저가 `wss://<도메인>`으로 직접 연결해야 한다.
  Caddy는 WebSocket을 그대로 전달하므로 서버 구성은 바꿀 필요가 없다.
