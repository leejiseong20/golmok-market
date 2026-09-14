# 골목마켓 (Golmok Market)

동네 기반 중고거래 플랫폼. 취업 포트폴리오 프로젝트.
백엔드 어필 포인트: **결제 / 실시간 채팅 / 알림**

## 스택

- Spring Boot 4.1.1 / Java 21 / Gradle
- MySQL 8.0 (운영), H2 인메모리 (테스트)
- React + Vite (별도 저장소, 현재 초안 단계)

## 작업 규칙

- 모든 응답은 한국어로.
- 코드 조각이 아니라 **완성된 파일 전체**로 제시한다.
- 왜 그렇게 설계했는지 짧게라도 근거를 붙인다. 면접에서 설명할 수 있어야 한다.
- 문제가 보이면 돌려 말하지 말고 직접 지적한다.

## 지켜야 할 제약

- `ddl-auto: validate`. 엔티티를 바꾸면 `golmok_schema_v2.sql`도 함께 고친다.
- `build.gradle`의 Spring Boot 버전은 임의로 변경하지 않는다.
- API 리소스명은 `products`로 통일한다 (`items` 아님).
- 엔티티에 setter를 만들지 않는다. 의미 있는 비즈니스 메서드만 노출한다.
- 연관관계는 전부 `LAZY`. 양방향은 `Product ↔ ProductImage`만.
- 상품은 soft delete. 물리 삭제 금지.
- DB 비밀번호 등 비밀값은 환경변수로만. 커밋 금지.

## 파일

- `API-SPEC.md` — API 명세 (1단계: 인증/상품/찜/카테고리/동네/검색)
- `golmok_schema_v2.sql` — 실제 DB 스키마. 이게 기준이다.

## 진행 상황

- [완료] DB 스키마 15개 테이블 설계 및 적용
- [완료] JPA 엔티티 15개 + enum 8개, `validate` 통과
- [완료] H2 테스트 분리, GitHub 푸시
- [완료] API 명세 확정 (에러 포맷 `errors` 필드, 목록별 커서 정렬값 반영)
- [완료] 공통: 전역 예외 핸들러, 에러 응답 포맷(`errors` 필드 포함), 커서 페이징 유틸
- [완료] 인증: 가입 / 로그인 / 재발급(rotation) / 로그아웃 / 중복 확인, Spring Security + JWT
- [다음] 카테고리·동네 조회 → 상품 CRUD → **배포** → 채팅 → 알림 → 결제

## 알려진 과제

- 엔티티 9개(`Region`, `ChatRoom`, `ChatMessage`, `Notification`, `Favorite`, `ProductImage`, `SearchLog`, `Payment`, `Review`)의 `created_at`이 `insertable = false`(DB 기본값 의존)다. H2 테스트에서 INSERT가 실패하고, 저장 직후 `createdAt`이 null이다. 각 도메인 구현 시 `BaseCreatedTimeEntity`로 바꾼다. (`RefreshToken`은 완료)
- 엔티티가 비즈니스 규칙 위반 시 `IllegalArgumentException`/`IllegalStateException`을 던진다. 403이어야 할 것(참여자 아님 등)도 400이 된다. 각 도메인 구현 시 `BusinessException`으로 바꾼다.
- 실행 시 환경변수 `DB_PASSWORD`, `JWT_SECRET`(Base64, 256비트 이상) 필요. 배포 시 `-Duser.timezone=Asia/Seoul` 권장.

## 설계 결정 기록

왜 이렇게 했는지. 나중에 뒤집으려 할 때 먼저 읽을 것.

- **`products.status`와 `trades`를 분리했다.** 상품 상태는 화면에 표시하는 팻말이고, 거래는 영수증이다. 결제 실패·환불이 생기면 상품 상태만으로는 표현할 수 없다.
- **`payments`는 `trades`에 1:N이다.** 결제는 실패 후 재시도할 수 있고, 그 이력이 남아야 한다. `order_id` UNIQUE로 멱등성을 보장해 웹훅 중복 처리를 막는다.
- **`trades.active_product_id`는 생성컬럼이다.** `status`가 취소/환불이면 NULL이 되어 UNIQUE 제약에서 빠진다. 덕분에 "진행 중 거래는 상품당 1건"이 서비스 레이어 락 없이 DB에서 보장된다.
- **`view_count`, `favorite_count`, `chat_count`는 의도적 비정규화다.** 목록에서 상품마다 COUNT 쿼리가 나가는 것을 막기 위한 것.
- **목록은 커서 페이징이다.** 끌어올리기로 순서가 계속 바뀌어서 offset 방식은 중복·누락이 생긴다.
- **이미지 업로드와 상품 등록을 분리했다.** 사진 선택 즉시 업로드해야 등록 버튼에서 기다리지 않는다.
- **refresh token은 JWT가 아닌 무작위 문자열이고, DB에는 SHA-256 해시만 저장한다.** 어차피 DB 조회로 검증하니 서명이 필요 없다. 원문을 저장하면 DB 유출 시 바로 로그인에 쓸 수 있다. BCrypt가 아닌 이유는 256비트 난수라 대입 공격이 불가능하고, 솔트가 있으면 해시로 조회(UNIQUE 인덱스)할 수 없어서다.
- **재발급 조회는 `SELECT ... FOR UPDATE`다.** 같은 refresh token으로 동시 재발급이 오면 둘 다 성공해 한쪽 토큰이 조용히 무효가 된다. 잠금으로 두 번째 요청이 명확히 실패하게 한다.
- **401을 `EXPIRED_TOKEN`과 `INVALID_TOKEN`으로 나눴다.** 프론트는 만료일 때만 재발급하고, 위조면 즉시 로그아웃한다.
- **로그아웃해도 access token은 최대 30분 유효하다.** 막으려면 요청마다 블랙리스트를 조회해야 해서 JWT의 무상태 이점이 사라진다. 만료를 짧게 두는 것으로 대응한다.
- **비밀번호는 ASCII 8~64자다.** BCrypt는 72바이트까지만 처리하는데, 한글이 섞이면 글자 수 제한만으로는 넘을 수 있다.
- **테스트는 H2, 운영은 MySQL.** 테스트가 로컬 DB에 의존하면 CI에서 깨진다. 대신 MySQL 전용 문법(생성컬럼, FULLTEXT ngram)은 H2에서 검증되지 않으므로, 스키마 일치는 실제 실행 시 `validate`로 확인한다.
