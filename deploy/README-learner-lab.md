# 골목마켓 배포 — AWS Academy Learner Lab 기준

**이 문서는 "배포 연습과 면접 시연"용이다. 24시간 살아 있는 주소를 만드는 방법이 아니다.**
*2026-09-18 이 순서대로 실제 배포에 성공했다(Ubuntu 24.04 · t3.small · us-east-1).*
상시 운영은 [README.md](README.md)(Oracle Cloud 상시 무료)를 따른다. 실행 방법 자체는 두 문서가 같다.

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

## 먼저 알아야 할 제약

Learner Lab 안내문에 적힌 내용이고, 이 문서의 구성은 전부 여기서 나왔다.

| 제약 | 이 배포에 미치는 영향 |
|---|---|
| 세션은 4시간, 끝나면 **EC2 가 자동으로 멈춘다** | 실습 창을 닫으면 사이트가 내려간다. 다음 세션에서 Start 하면 다시 뜬다 |
| 다시 켜면 **공인 IP 가 바뀐다** | 도메인이 따라가야 한다 → 아래 4단계(DuckDNS 자동 갱신) |
| 예산 **$50 초과 시 환경 접근 불가 + 작업 삭제** | NAT·로드밸런서를 만들지 않는다. 남는 자원을 그대로 두지 않는다 |
| 강의가 끝나면 **랩 접근 종료** | 이력서에 적을 주소로 쓸 수 없다 |
| 랩을 **Reset** 하면 전부 사라진다 | DB 백업은 서버 밖에 보관한다(README.md 의 "백업") |

> 과정마다 허용 서비스가 다르다. **EC2 를 쓸 수 있는지** 강의 자료의 지원 서비스 목록에서 먼저 확인한다.
> 여기 적힌 화면 이름·버튼은 AWS 콘솔 기준이라 시점에 따라 조금 다를 수 있다.

---

## 1. 랩 시작하고 콘솔 열기

1. 과목 → **Modules → Learner Lab → Start Lab**. 왼쪽 위 점이 **초록색**이 되면 준비 완료(1~2분).
2. **AWS** 링크를 누르면 AWS 콘솔이 열린다.
3. **AWS Details → Download PEM** 으로 키 파일(`labsuser.pem`)을 내려받는다. SSH 접속에 쓴다.
   - 랩을 새로 시작해도 같은 키(`vockey`)를 쓴다. 파일은 한 번만 받아 두면 된다.
4. 리전을 **N. Virginia(us-east-1)** 로 맞춘다. 콘솔 오른쪽 위에서 바꾼다.
   - 서울(ap-northeast-2)에서 인스턴스를 만들려고 하면 **AMI 오류**가 난다(랩이 허용한 리전이 아니다). 실제로 여기서 한 번 막혔다.

키 파일은 권한을 좁혀 둬야 한다. 넓으면 ssh 가 `UNPROTECTED PRIVATE KEY FILE!` 로 거부한다.

**macOS · Linux · Git Bash**

```bash
chmod 400 labsuser.pem
```

**Windows (PowerShell)** — `chmod` 는 통하지 않는다. Windows OpenSSH 는 파일의 **ACL** 을 검사한다.

```powershell
$key = "D:\projects\golmok-market\PEM_KEY\labsuser.pem"
icacls $key /inheritance:r
icacls $key /grant:r "${env:USERNAME}:R"
icacls $key /remove:g "NT AUTHORITY\Authenticated Users" "BUILTIN\Users"
icacls $key
```

마지막 출력에 **본인 계정 `:(R)` 과 Administrators · SYSTEM 만** 남아야 한다.
`Authenticated Users` 나 `Users` 가 보이면 ssh 가 계속 거부한다.

> 실제로 여기서 가장 오래 막혔다. 이 파일의 권한은 상위 폴더에서 **상속된 것이 아니라 파일에 직접 박혀 있어서**
> `/inheritance:r` 만으로는 지워지지 않았다(`icacls` 출력에 `(I)` 표시가 없으면 직접 설정된 권한이다).
> 그래서 `/remove:g` 로 해당 그룹을 명시적으로 지워야 했다. `/inheritance:r` 뒤에는 **반드시 `/grant:r` 로
> 본인 읽기 권한을 다시 주어야 한다.** 빠뜨리면 본인도 키를 읽지 못한다.

---

## 2. EC2 인스턴스 만들기

콘솔 → **EC2 → Instances → Launch instances**

| 항목 | 값 |
|---|---|
| Name | `golmok` |
| AMI | **Ubuntu Server 24.04 LTS** (Oracle 쪽 문서와 명령을 그대로 쓰기 위해) |
| Instance type | **t3.small** (2GB). 목록에 없으면 `t2.small`·`t3.micro` |
| Key pair | **vockey** (1단계에서 받은 그 키) |
| Network settings → Firewall | **Create security group**, 아래 규칙 3개 |
| Storage | **20GB** gp3 (기본 8GB 는 이미지·DB 에 빠듯하다) |

보안 그룹 규칙:

| Type | Port | Source | 이유 |
|---|---|---|---|
| SSH | 22 | **My IP** | 접속용. 0.0.0.0/0 으로 열지 않는다 |
| HTTP | 80 | 0.0.0.0/0 | 인증서 발급과 HTTPS 리다이렉트 |
| HTTPS | 443 | 0.0.0.0/0 | 실제 서비스 |

**3306(MySQL)·8080(앱)은 열지 않는다.** Compose 가 컨테이너 안에서만 연결한다.

> **만들지 말 것: NAT Gateway, Load Balancer, Elastic IP.**
> 셋 다 인스턴스가 꺼져 있어도 요금이 계속 나간다. $50 예산에서는 이것만으로 며칠 만에 바닥난다.
> IP 가 바뀌는 문제는 4단계에서 도메인 자동 갱신으로 푼다.

만들어지면 인스턴스 상세에서 **Public IPv4 address** 를 확인한다.

---

## 3. 접속과 서버 준비

```bash
ssh -i labsuser.pem ubuntu@<공인 IP>
```

Docker 설치:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit    # 다시 접속해야 docker 그룹이 적용된다
```

스왑 2GB (t3.small 메모리 2GB 에서 앱과 MySQL 을 함께 돌리기 위한 안전판):

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

> Oracle 문서의 4-2단계(서버 안 iptables 열기)는 **필요 없다.** AWS 의 Ubuntu 이미지는 방화벽이 열려 있고,
> 차단은 보안 그룹이 담당한다.

---

## 4. 도메인이 바뀌는 IP 를 따라가게 하기

Learner Lab 에서 가장 성가신 부분이다. 세션을 다시 시작할 때마다 공인 IP 가 바뀌는데,
탄력적 IP 는 예산을 갉아먹으므로 **부팅할 때 도메인을 스스로 갱신**하게 만든다.

1. https://www.duckdns.org 로그인 → 서브도메인 추가(예: `golmok-api`) → 화면 위쪽의 **token** 을 복사해 둔다.
2. 저장소를 받고 설정을 채운다.

```bash
git clone https://github.com/leejiseong20/golmok-market.git
cd golmok-market/deploy
cp .env.example .env

# 비밀값을 새로 만든다(로컬에서 쓰던 값 재사용 금지)
echo "MYSQL_ROOT_PASSWORD=$(openssl rand -base64 24)"
echo "DB_PASSWORD=$(openssl rand -base64 24)"
echo "JWT_SECRET=$(openssl rand -base64 32)"

nano .env   # DOMAIN, DUCKDNS_TOKEN, 비밀번호 3개, CORS_ALLOWED_ORIGINS 채우기
```

3. 갱신 스크립트를 부팅할 때와 5분마다 돌게 등록한다.

```bash
chmod +x duckdns-update.sh
./duckdns-update.sh            # 지금 IP 로 즉시 한 번 갱신(“갱신 완료” 가 나와야 한다)

sudo cp duckdns-update.service duckdns-update.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now duckdns-update.timer
systemctl list-timers duckdns-update.timer   # 다음 실행 시각 확인
```

> 스크립트는 EC2 메타데이터에서 공인 IP 를 읽어 DuckDNS 에 알린다. 실패하면 로그에 이유가 남는다:
> `journalctl -u duckdns-update.service -n 20`

---

## 5. 실행 · 데모 데이터 · 프론트 연결

여기서부터는 Oracle 문서와 완전히 같다. [README.md](README.md) 의 **6~8단계**를 그대로 따른다.

```bash
docker compose up -d
docker compose logs -f app       # "Started GolmokMarketApplication" 이 나오면 Ctrl+C
curl https://<도메인>/api/categories
./seed-demo.sh                   # 데모 데이터
```

프론트(Vercel)의 `vercel.json` 백엔드 주소를 `<도메인>` 으로 바꾸고,
서버 `.env` 의 `CORS_ALLOWED_ORIGINS` 를 Vercel 주소로 맞춘 뒤 `docker compose up -d app`.
**이 두 가지를 빠뜨리면 목록은 보이는데 로그인만 403 이 난다.**

> 새 버전 배포와 **Caddyfile 을 바꿨을 때의 주의(컨테이너를 다시 만들어야 반영된다)** 는
> [README.md 의 "새 버전 배포"](README.md#새-버전-배포)를 그대로 따른다.

---

## 6. 세션이 끝난 뒤, 다시 시작할 때

세션이 끝나면 EC2 가 멈춘다. 데이터(EBS 볼륨)는 그대로 남는다.

**다음 세션에서 할 일**

1. **Start Lab** → 초록 불 확인 → 콘솔 열기
2. EC2 → 인스턴스 선택 → **Instance state → Start instance**
3. 1~2분 뒤 자동으로 복구된다.
   - 컨테이너: Compose 의 `restart: unless-stopped` 덕분에 도커가 뜨면서 함께 올라온다
   - 도메인: 위에서 등록한 타이머가 새 IP 로 갱신한다
4. 확인:

```bash
curl -I https://<도메인>/api/categories     # 200 이면 끝
```

**면접 시연이라면** 시작 버튼을 누르고 2~3분 여유를 두면 된다. 확인용으로 SSH 에 붙어

```bash
cd ~/golmok-market/deploy && docker compose ps
systemctl status duckdns-update.timer --no-pager
```

를 보면 상태가 한눈에 들어온다.

---

## 7. 예산($50) 지키기

| 항목 | 대략 비용 | 메모 |
|---|---|---|
| t3.small **켜져 있는 동안** | 시간당 약 $0.02 | 4시간 세션 한 번에 약 $0.1 |
| EBS 20GB | 월 약 $1.6 | **인스턴스를 꺼도 계속 나간다** |
| 데이터 전송 | 시연 수준이면 미미 | |

- 요금 확인: 랩 화면 위쪽에 **남은 예산**이 표시된다. 콘솔의 Billing 은 Learner Lab 에서 막혀 있을 수 있다.
- **쓰지 않는 자원을 남기지 않는다.** 특히 NAT Gateway·로드밸런서·탄력적 IP·추가 EBS 볼륨.
- 과정이 끝나기 전에 **DB 백업을 내 PC 로** 받아 둔다(README.md 의 "백업"). 랩이 닫히면 되돌릴 수 없다.

---

## 8. 문제 해결 (랩 특유)

| 증상 | 원인 · 조치 |
|---|---|
| SSH 가 `Permission denied (publickey)` · `UNPROTECTED PRIVATE KEY FILE!` | 키 권한. **Windows 는 `chmod` 가 아니라 `icacls`**(1단계 참고). 사용자 이름은 Ubuntu 이미지에서 `ubuntu` |
| SSH 가 응답 없음 | 보안 그룹 22번 Source 가 **My IP** 인데 내 IP 가 바뀐 경우. 규칙을 다시 저장한다 |
| 어제는 됐는데 도메인으로 접속이 안 됨 | 인스턴스를 켰는지, 타이머가 도는지 확인: `systemctl status duckdns-update.timer`, `./duckdns-update.sh` 수동 실행 |
| 인증서 발급 실패 | DuckDNS 가 새 IP 를 가리키는지 먼저 확인(`dig +short <도메인>`). 80 포트가 열려 있어야 한다. `docker compose logs caddy` |
| 재시작 후 컨테이너가 안 올라옴 | `docker compose ps` → 없으면 `docker compose up -d`. 도커 자체는 `systemctl status docker` |
| 예산이 빠르게 줄어듦 | 콘솔에서 **EC2 Instances(running)**, **Elastic IPs**, **NAT Gateways**, **Load Balancers**, **Volumes** 를 확인해 쓰지 않는 것을 지운다 |
| 랩 Reset 후 전부 사라짐 | 정상 동작이다. 2단계부터 다시 하고 백업 SQL 로 복구한다 |

나머지(스키마·CORS·이미지·데모 데이터 관련)는 [README.md](README.md) 의 문제 해결 표를 본다.


---

## 웹 푸시 키(VAPID) 만들기

앱을 닫아도 알림을 받으려면 서버에 VAPID 키 쌍이 있어야 한다. **서버(Ubuntu)에서** 한 번만 실행한다.

```bash
cd ~/golmok-market/deploy && bash vapid-setup.sh && docker compose up -d app
```

- 키를 서버 안에서 openssl 로 만들어 `.env` 에 넣는다. **개인키는 화면에 출력하지 않는다.** 이전 `.env` 는 `.env.bak.<시각>` 으로 남는다.
- 이미 키가 있으면 아무것도 하지 않는다. **키를 바꾸면 모든 기기의 구독이 무효가 되어** 사용자가 알림을 다시 켜야 한다.
- 확인: 로그인한 상태로 `GET /api/push/public-key` 가 `enabled: true` 면 된다. 키 짝이 틀리면 앱은 뜨고 푸시만 꺼진다(`enabled: false` + 오류 로그).
