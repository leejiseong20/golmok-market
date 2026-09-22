# 골목마켓 API 명세

1단계 범위: 인증 · 회원 · 상품 · 찜 · 카테고리 · 동네 · 검색 · 채팅(REST)
10절: 채팅 실시간(WebSocket). 11절: 채팅방 직거래. 12절: 리뷰·매너온도. 13절: 알림. 결제는 범위에서 제외했다(택배 거래 전용 기능, 2026-09-17).

---

## 공통 규약

| 항목 | 값 |
|---|---|
| Base URL | `/api` |
| 요청/응답 형식 | `application/json` (이미지 업로드만 `multipart/form-data`) |
| 인증 | `Authorization: Bearer {accessToken}` |
| 날짜 형식 | ISO-8601 (`2026-09-14T19:52:31`) — 상대시간("3분 전")은 프론트에서 변환 |
| 네이밍 | 요청/응답 JSON은 camelCase |

### 에러 응답 (모든 실패 공통)

```json
{
  "code": "PRODUCT_NOT_FOUND",
  "message": "상품을 찾을 수 없습니다.",
  "timestamp": "2026-09-14T19:52:31"
}
```

입력 검증 실패(400 `INVALID_INPUT`)일 때만 `errors`가 추가된다. 그 외 에러에는 이 필드가 없다.
프론트가 어느 입력칸에 에러를 표시할지 알아야 하기 때문이다.

```json
{
  "code": "INVALID_INPUT",
  "message": "요청 값이 올바르지 않습니다.",
  "timestamp": "2026-09-14T19:52:31",
  "errors": [
    { "field": "email", "reason": "이메일 형식이 아닙니다." },
    { "field": "password", "reason": "비밀번호는 8자 이상이어야 합니다." }
  ]
}
```

| HTTP | 사용 상황 |
|---|---|
| 400 | 요청 값 검증 실패, 잘못된 상태 전이 |
| 401 | 토큰 없음 / 만료 |
| 403 | 권한 없음 (남의 상품 수정 등) |
| 404 | 리소스 없음 |
| 409 | 중복 (이메일, 닉네임, 찜) |
| 500 | 서버 오류 |

#### 401 코드 구분 (프론트 처리 기준)

| code | 상황 | 프론트 동작 |
|---|---|---|
| `UNAUTHORIZED` | 인증이 필요한 API 에 토큰 없이 요청 | 로그인 화면으로 |
| `EXPIRED_TOKEN` | access token 만료 | `/api/auth/reissue` 호출 후 원래 요청 재시도 |
| `INVALID_TOKEN` | 위조·형식 오류 토큰 | 저장된 토큰 삭제 후 로그인 화면으로 |
| `LOGIN_FAILED` | 이메일 또는 비밀번호 불일치 | 로그인 폼에 에러 표시 |
| `INVALID_REFRESH_TOKEN` | refresh token 없음·만료·이미 사용됨 | 저장된 토큰 삭제 후 로그인 화면으로 |

토큰이 **틀린 경우에는 공개 API(상품 목록 등)라도 401** 을 준다. 조용히 비로그인으로 처리하면 `isLiked` 가 이유 없이 `false` 로 보이고 프론트가 재발급할 기회를 놓치기 때문이다. 토큰을 아예 안 보내면 공개 API 는 비로그인으로 정상 응답한다.

### 페이징 — 커서 방식

목록 API는 offset(`page=2`) 대신 **커서**를 쓴다.

예외: 6절의 카테고리·동네 조회 3개는 커서 페이징 없이 JSON 배열을 반환한다.
카테고리는 전체, 동네 검색은 일치하는 전체, 근처 동네는 가까운 순 최대 10개다.

이유: 끌어올리기(`bumped_at` 갱신)와 신규 등록이 계속 일어나기 때문에, 사용자가 2페이지를 볼 때 목록 순서가 이미 바뀌어 있다. offset 방식은 이때 **같은 상품이 두 번 보이거나 건너뛰어진다.** 커서는 "마지막으로 본 상품 다음부터"를 지정하므로 이 문제가 없다.

책갈피를 꽂아두는 것과 같다. "47페이지"는 책이 개정되면 엉뚱한 곳이지만, 책갈피는 항상 읽던 자리다.

**요청**: `?cursor={정렬값}_{lastId}&size=20`
첫 요청은 `cursor` 생략. `nextCursor`는 서버가 준 값을 그대로 돌려보내면 되고, 프론트가 해석할 필요는 없다.

| 목록 | 정렬값 | 예시 |
|---|---|---|
| 상품 최신순 (`LATEST`) | `bumpedAt` | `2026-09-14T18:20:00_47` |
| 상품 낮은가격순 (`PRICE_ASC`) | `price` | `80000_47` |
| 내 판매내역 | `createdAt` | `2026-09-14T18:20:00_47` |
| 내 찜 목록 | 찜한 시각 | `2026-09-14T18:20:00_12` |
| 채팅 목록 | 마지막 메시지 시각 | `2026-09-17T10:21:30_7` |
| 채팅 메시지 | 메시지 id | `30_30` |

id를 함께 넣는 이유: 같은 초에 끌어올린 상품이나 같은 가격의 상품이 있으면 정렬값만으로는 경계가 모호해 누락·중복이 생긴다.

`size`는 기본 20, 최대 50. 범위를 벗어나면 에러 대신 보정한다(0 이하 → 20, 50 초과 → 50).
`cursor` 형식이 틀리면 `400 INVALID_INPUT`.

**응답**:
```json
{
  "content": [ ... ],
  "nextCursor": "2026-09-14T18:20:00_47",
  "hasNext": true
}
```

`hasNext`가 `false`면 프론트의 "더 보기" 버튼을 숨긴다.

---

## 1. 인증 `/api/auth`

### POST `/api/auth/signup` — 회원가입

```json
// Request
{
  "email": "user@example.com",
  "password": "Password123!",
  "nickname": "골목이",
  "phone": "01012345678"
}
```
```json
// 201 Created
{ "id": 1, "email": "user@example.com", "nickname": "골목이" }
```

검증: 이메일 형식(100자 이하), 비밀번호 8~64자(영문·숫자·특수문자 각 1자 이상, **공백·한글 불가**), 닉네임 2~30자, 휴대폰 `01`로 시작하는 숫자 10~11자리(선택).
중복이면 `409` — `DUPLICATE_EMAIL` / `DUPLICATE_NICKNAME`.

이메일은 소문자로 저장한다. `User@Example.com` 과 `user@example.com` 은 같은 계정이다.

비밀번호를 ASCII 로 제한하는 이유: 비밀번호 해시(BCrypt)는 72바이트까지만 처리한다. 한글은 한 글자가 3바이트라 글자 수 제한만으로는 이 한도를 넘을 수 있다.

---

### POST `/api/auth/login` — 로그인

```json
// Request
{ "email": "user@example.com", "password": "Password123!" }
```
```json
// 200 OK
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "q3Zr8Hk2...Xw",
  "user": {
    "id": 1,
    "nickname": "골목이",
    "profileImageUrl": null,
    "primaryRegion": { "id": 1, "name": "역삼동" }
  }
}
```

`accessToken` 은 JWT, 만료 30분. `refreshToken` 은 JWT 가 아닌 무작위 문자열(43자), 만료 14일.
프론트는 두 값 모두 해석하지 않고 그대로 보관·전송하면 된다.
동네 인증 전이면 `primaryRegion` 은 `null`.

이메일이 없는 경우와 비밀번호가 틀린 경우 모두 `401 LOGIN_FAILED` 로 같게 응답한다(가입 여부 노출 방지).
탈퇴·정지 계정은 비밀번호가 맞을 때만 `403 USER_NOT_ACTIVE`.

여러 기기에서 동시에 로그인할 수 있다. 로그인할 때마다 refreshToken 이 따로 발급된다.

**refreshToken 을 JWT 로 만들지 않은 이유**: refreshToken 은 어차피 서버 DB 에서 조회해 검증한다. 자체 서명이 필요 없다. DB 에는 원문이 아닌 SHA-256 해시만 저장해, DB 가 유출돼도 그 값으로 로그인할 수 없게 한다.

---

### POST `/api/auth/reissue` — 토큰 재발급

```json
// Request
{ "refreshToken": "q3Zr8Hk2...Xw" }
```
```json
// 200 OK
{ "accessToken": "eyJhbGci...", "refreshToken": "Tm9wZ3Vl...Qa" }
```

재발급 시 refreshToken도 새로 발급하고 기존 것은 폐기한다(rotation). 탈취된 토큰의 유효 기간을 줄이기 위한 것.
**응답으로 받은 새 refreshToken 으로 반드시 교체해야 한다.** 이전 값은 즉시 `401 INVALID_REFRESH_TOKEN`.

만료된 accessToken 을 `Authorization` 헤더에 붙인 채 호출해도 된다. 이 API 는 access token 을 검사하지 않는다.

**프론트 주의 — 재발급은 한 번만**: 여러 요청이 동시에 `EXPIRED_TOKEN` 을 받으면 각각 재발급을 호출하게 된다. 첫 번째가 성공하는 순간 기존 refreshToken 은 폐기되므로, 나머지는 `INVALID_REFRESH_TOKEN` 을 받고 로그아웃된다. 재발급 요청은 **진행 중인 것 하나를 공유**하고, 나머지 요청은 그 결과를 기다렸다가 새 토큰으로 재시도해야 한다.

---

### POST `/api/auth/logout` — 로그아웃
인증 필요.
```json
// Request
{ "refreshToken": "q3Zr8Hk2...Xw" }
```
→ `204 No Content`

여러 기기 로그인을 허용하므로 **요청한 기기의 refreshToken 만** 삭제한다. 본인 토큰이 아니거나 이미 없는 토큰이어도 에러 없이 `204` 다.

로그아웃해도 이미 발급된 accessToken 은 만료(최대 30분)까지 서버에서 유효하다. 프론트는 로그아웃 시 두 토큰을 모두 지운다.

---

### GET `/api/auth/check-email?email=` — 이메일 중복 확인
### GET `/api/auth/check-nickname?nickname=` — 닉네임 중복 확인

```json
// 200 OK
{ "available": true }
```

---

## 2. 회원 `/api/users`

### GET `/api/users/me` — 내 정보
인증 필요.
```json
{
  "id": 1,
  "nickname": "골목이",
  "profileImageUrl": null,
  "mannerTemp": 36.5,
  "regions": [
    { "id": 1, "name": "역삼동", "isPrimary": true, "verifyCount": 3 }
  ]
}
```

`email`은 화면에서 쓰지 않아 내려주지 않는다. `regions`의 `id`는 **동네(region) id**다(아래 삭제·대표 지정 경로에 그대로 쓴다).

### PATCH `/api/users/me` — 프로필 수정
인증 필요. **닉네임과 사진을 항상 함께 보낸다.** 보낸 필드만 바꾸는 방식이면 "사진 삭제"로 보낸 `null` 과 "사진은 그대로"를 구분할 수 없기 때문이다.
```json
{ "nickname": "새닉네임", "profileImageUrl": "/api/images/2026/09/17/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg" }
```
응답은 갱신된 내 정보(`GET /api/users/me` 와 같은 형식)라 화면이 다시 조회하지 않아도 된다.

- `nickname`: 필수. 앞뒤 공백을 뺀 뒤 2~30자(가입과 같은 규칙). 지금 닉네임을 그대로 보내도 성공이다.
- `profileImageUrl`: `POST /api/images` 가 발급한 경로만 받는다(상품 사진과 같은 검증). `null` 이면 사진을 지운다. 이전 사진 파일은 바로 지우지 않는다.
- 닉네임 변경 횟수 제한은 없다.

| 상황 | 응답 |
|---|---|
| 다른 사람이 쓰는 닉네임 | `409 DUPLICATE_NICKNAME` (동시에 같은 닉네임으로 바꿔도 하나만 성공한다) |
| 닉네임 누락·길이 오류 | `400 INVALID_INPUT` (`errors[].field = "nickname"`) |
| 서버에 업로드한 경로가 아닌 사진·없는 파일 | `400 INVALID_IMAGE_URL` |
| 비로그인 | `401 UNAUTHORIZED` |

이미 만들어진 알림 문구(`닉네임님 · 5점`)에는 이전 닉네임이 남는다. 후기 목록·채팅·상품 상세는 조회 시점의 닉네임을 쓴다.

### DELETE `/api/users/me` — 회원 탈퇴
```json
{ "password": "Golmok123!" }
```
`204 No Content`. 되돌릴 수 없다. 모든 처리가 한 트랜잭션이라 중간에 실패하면 아무것도 바뀌지 않는다.

| 대상 | 처리 |
|---|---|
| 회원 | 익명화해서 남긴다: 상태 `WITHDRAWN`, 이메일 `withdrawn-{id}@deleted.invalid`, 닉네임 `탈퇴한사용자{id}`, 사진·전화번호 삭제. 원래 이메일·닉네임으로 다시 가입할 수 있다 |
| 판매 상품 | 모두 soft delete |
| 내가 한 찜 | 삭제하고 해당 상품의 찜 수를 줄인다 |
| 채팅방 | 모든 방에서 나감으로 표시. 메시지는 남아 상대가 계속 본다 |
| 알림 · 인증 동네 · refresh token | 삭제(다른 기기도 재발급에서 로그아웃된다) |
| 거래 · 후기 | 그대로 둔다(상대의 기록이다). 후기 작성자는 익명화된 닉네임으로 보인다 |

| 상황 | 응답 |
|---|---|
| 비밀번호 불일치 | `400 PASSWORD_MISMATCH` |
| 비밀번호 누락·공백 | `400 INVALID_INPUT` |
| 진행 중인 거래(예약·결제·배송)가 있음(구매자·판매자 모두) | `409 TRADE_IN_PROGRESS` |
| 이미 탈퇴함(남은 access token 으로 요청) | `404 USER_NOT_FOUND` |
| 비로그인 | `401 UNAUTHORIZED` |

- 탈퇴 뒤 원래 이메일로 로그인하면 `401 LOGIN_FAILED`다.
- 이미 발급된 access token 은 만료(최대 30분)까지 서명 검증을 통과한다(로그아웃과 같은 정책). 그동안 `GET/PATCH /api/users/me`는 `404 USER_NOT_FOUND`다. 열려 있던 WebSocket 연결도 끊지 않는다.
- 탈퇴한 회원의 공개 프로필(`GET /api/users/{id}`)·받은 후기(`GET /api/users/{id}/reviews`)는 `404 USER_NOT_FOUND`다.
- 탈퇴한 회원에게는 알림을 저장하지 않는다. 복구 기능은 없다.

### GET `/api/users/{id}` — 다른 사용자 프로필
```json
{
  "id": 2,
  "nickname": "판매자",
  "profileImageUrl": null,
  "mannerTemp": 38.2,
  "productCount": 12,
  "reviewCount": 5
}
```

### POST `/api/users/me/regions` — 동네 인증
인증 필요.
```json
// Request
{ "lat": 37.5006, "lng": 127.0366 }
```
```json
// 200 OK — 갱신된 내 동네 전체
[
  { "id": 1, "name": "역삼동", "isPrimary": true, "verifyCount": 1 },
  { "id": 2, "name": "서교동", "isPrimary": false, "verifyCount": 1 }
]
```

### DELETE `/api/users/me/regions/{regionId}` — 동네 삭제
### PATCH `/api/users/me/regions/{regionId}/primary` — 대표 동네 지정

**세 API 모두 갱신된 내 동네 목록 전체를 돌려준다.** 대표를 옮기면 두 행이 한 번에 바뀌므로, 화면이 부분 갱신을 조합하지 않아도 되게 했다.

| 정책 | 동작 |
|---|---|
| 좌표 → 동네 | 가장 가까운 동네 1개로 인증 |
| 재인증 | 이미 인증한 동네면 행을 늘리지 않고 `verifyCount`만 +1 |
| 개수 제한 | 최대 2개. 3개째는 `409 REGION_LIMIT_EXCEEDED` (기존 동네 재인증은 2개여도 허용) |
| 첫 동네 | 자동으로 대표가 된다 |
| 대표 삭제 | 남은 동네가 자동으로 대표로 승격. 마지막 하나를 지우면 동네 없음 |
| 인증하지 않은 동네 삭제·대표 지정 | `404 USER_REGION_NOT_FOUND` |
| 좌표 범위 밖·누락 | `400 INVALID_INPUT` (`errors[].field`는 `lat` 또는 `lng`) |

**인증 반경 제한은 두지 않는다.** 실제 서비스라면 "그 동네 근처에 있을 때만" 인증이 맞지만, 지금 `regions`에는 동네가 3개뿐이라 반경을 걸면 대부분의 좌표가 인증에 실패해 개발·시연이 불가능하다. 동네 데이터를 실제로 채울 때 도입한다.

프론트는 **브라우저 위치로만** 인증한다. 동네 이름을 골라 인증하게 하면 가보지도 않은 동네를 등록할 수 있어 동네 인증이라는 장치가 의미를 잃는다.

---

## 3. 상품 `/api/products`

### GET `/api/products` — 상품 목록

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `regionId` | Long | O | 동네 |
| `categoryId` | Long | X | 없으면 전체 |
| `keyword` | String | X | 제목·본문 검색 |
| `sort` | String | X | `LATEST`(기본) / `PRICE_ASC` |
| `cursor` | String | X | 첫 페이지는 생략 |
| `size` | int | X | 기본 20, 최대 50 |

비로그인 조회를 허용한다. `regionId`, `categoryId`는 양수여야 한다.
동네 ID 누락·ID 형식 오류·0 이하 ID·지원하지 않는 `sort`는 `400 INVALID_INPUT`이다.
존재하지 않는 양수 동네·카테고리 ID는 필터에 일치하는 상품이 없는 것으로 보고 빈 목록을 반환한다.

정렬은 `LATEST`: `bumpedAt DESC, id DESC`, `PRICE_ASC`: `price ASC, id ASC`다.
`Cursor / PageSize / CursorResponse` 공통 규칙을 사용한다. 목록 조회로 조회수는 증가하지 않는다.
정렬·필터를 변경하면 커서를 생략하고 첫 페이지부터 요청한다.
`LATEST` 커서는 날짜(연도 1000~9999), `PRICE_ASC` 커서는 가격(0~2147483647)을 정렬값으로 사용한다.
잘못된 커서는 `400 INVALID_INPUT`이다. 기존 커서 오류 응답은 `errors` 없이 코드·메시지·시각을 반환한다.
응답 날짜는 초 단위로 표시하지만 `nextCursor`는 DB에서 읽은 정렬값의 정밀도를 유지하므로 그대로 돌려보낸다.

`keyword`는 앞뒤 공백 제거 후 최대 50자다. 생략·빈 값·공백만 있으면 검색 필터를 적용하지 않는다.
제목 또는 본문의 부분 일치 검색이며 `%`, `_`, `!`는 문자 그대로 취급한다.
길이 초과는 `400 INVALID_INPUT`, `errors`의 `field`는 `keyword`다.

**검색 방식(2026-09-21 변경)** — MySQL FULLTEXT(ngram) 인덱스로 찾는다.
- **띄어 쓴 단어는 모두 들어 있어야 한다.** `원목 식탁` 은 "원목" 과 "식탁" 이 제목·본문 어디든 있으면 찾는다.
  (예전 LIKE 는 "원목 식탁" 이 띄어쓰기까지 그대로 붙어 있어야 찾았다.)
- 한 단어 안에서는 예전과 같은 부분 일치다. `식탁` 은 "원목식탁" 에서도 찾힌다.
- 한 단어라도 **1글자**면 예전처럼 검색어 전체를 부분 일치로 찾는다(색인 단위가 2글자라 1글자는 색인으로 찾을 수 없다).
- `+ - < > ( ) ~ * " @` 는 지우고 단어 구분자로 본다(검색 연산자로 쓰이지 않게).
- 정렬은 `sort` 그대로다. 관련도 순은 없다.

```json
// 200 OK
{
  "content": [
    {
      "id": 1,
      "title": "원목 4인 식탁 (의자 2개 포함)",
      "price": 80000,
      "categoryId": 2,
      "categoryName": "가구",
      "regionName": "역삼동",
      "sellerNickname": "골목이",
      "thumbnailUrl": "https://.../table.jpg",
      "status": "ON_SALE",
      "favoriteCount": 12,
      "chatCount": 4,
      "isLiked": false,
      "createdAt": "2026-09-14T19:49:00",
      "bumpedAt": "2026-09-14T19:49:00"
    }
  ],
  "nextCursor": "2026-09-14T18:20:00_47",
  "hasNext": true
}
```

`isLiked`는 비로그인 시 항상 `false`.
삭제된 상품(`deleted_at IS NOT NULL`)은 목록에서 제외.
`ON_SALE`, `RESERVED`, `SOLD` 모두 포함한다.
`thumbnailUrl`은 이미지의 `sortOrder ASC, id ASC` 순서에서 첫 번째 URL이며 이미지가 없으면 `null`이다.
결과가 없으면 `200 OK`, `content: []`, `nextCursor: null`, `hasNext: false`다.

---

### GET `/api/products/{id}` — 상품 상세

```json
{
  "id": 1,
  "title": "원목 4인 식탁 (의자 2개 포함)",
  "description": "3년 사용했고 상태 좋습니다.",
  "price": 80000,
  "isNegotiable": true,
  "status": "ON_SALE",
  "tradeType": "DIRECT",
  "categoryId": 2,
  "categoryName": "가구",
  "regionId": 1,
  "regionName": "역삼동",
  "images": [
    { "id": 1, "imageUrl": "https://.../1.jpg", "sortOrder": 0 }
  ],
  "seller": {
    "id": 2,
    "nickname": "골목이",
    "profileImageUrl": null,
    "mannerTemp": 36.5
  },
  "viewCount": 132,
  "favoriteCount": 12,
  "chatCount": 4,
  "isLiked": false,
  "isMine": false,
  "createdAt": "2026-09-14T19:49:00"
}
```

조회 시 `viewCount` 증가. 단, **본인 상품 조회는 증가시키지 않는다.**

비로그인 조회도 허용하며 반복 요청마다 증가한다. 응답에는 해당 요청에서 증가한 조회수를 반영한다.
동시 조회의 증가분이 유실되지 않도록 DB에서 원자적으로 증가시키며, 조회수 증가로 상품 수정 시각이나 끌어올리기 시각은 바뀌지 않는다.
비로그인이면 `isLiked`, `isMine`은 모두 `false`다.
`images`는 `sortOrder ASC, id ASC` 순서이고 이미지가 없으면 `[]`다. 판매 완료 상품도 조회할 수 있다.
없는 상품과 삭제된 상품은 동일하게 `404 PRODUCT_NOT_FOUND`이며 조회수를 증가시키지 않는다.
상품 ID 형식 오류·0 이하 ID는 `400 INVALID_INPUT`이다.

---

### POST `/api/products` — 상품 등록
인증 필요.
```json
{
  "title": "원목 4인 식탁",
  "description": "3년 사용했고 상태 좋습니다.",
  "price": 80000,
  "categoryId": 2,
  "regionId": 1,
  "isNegotiable": true,
  "tradeType": "DIRECT",
  "imageUrls": ["/api/images/2026/09/16/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg"]
}
```
→ `201 Created`, 상세 응답과 동일한 본문.

검증: 제목 2~100자, 본문 10자 이상, 가격 0 이상, 이미지 1~10장.
`regionId`는 **본인이 인증한 동네여야 한다.** 아니면 `403`.

모든 필드는 필수다(`isNegotiable`, `tradeType` 포함). 제목·본문은 앞뒤 공백을 제거한 뒤 검증한다.
가격은 정수 `0~2147483647`, 카테고리·동네 ID는 양수다. 입력 검증 실패는 `400 INVALID_INPUT`과 `errors`를 반환한다.
존재하지 않는 카테고리는 `404 CATEGORY_NOT_FOUND`, 인증하지 않은 동네는 `403 REGION_NOT_VERIFIED`다.
이미지는 업로드 응답의 상대 경로(`/api/images/yyyy/MM/dd/UUID.jpg|png|webp`)만 허용한다.
외부 URL, 쿼리·프래그먼트, 인코딩·상위 경로 이동, 저장 디렉터리 밖을 가리키는 링크, 존재하지 않거나 읽을 수 없는 파일은 `400 INVALID_IMAGE_URL`이다.
파일 소유권은 업로더 메타데이터가 없어 확인하지 않는다. 이미지 순서는 배열 순서이며 첫 이미지가 썸네일이다.
상품 쓰기 응답은 조회수를 증가시키지 않는다.

---

### PUT `/api/products/{id}` — 상품 수정
본인만 가능. 등록과 동일한 본문. `SOLD` 상태면 `400`.

→ `200 OK`, 갱신된 상품 상세 응답. 상세에는 `regionId`가 포함된다.
수정 시에도 현재 인증한 동네만 선택할 수 있다. 대표 동네를 바꾸는 것만으로 기존 상품 지역이 바뀌지는 않는다.
이미지 배열 전체를 교체하고 DB의 이전 이미지 행은 제거한다. 디스크 파일은 즉시 지우지 않는다.
데모 상품의 기존 외부 이미지는 조회할 수 있지만 수정할 때는 새로 업로드한 이미지로 교체해야 한다.
`viewCount`, `favoriteCount`, 등록 시각과 끌어올리기 시각은 수정으로 바뀌지 않는다.

### DELETE `/api/products/{id}` — 상품 삭제
본인만 가능. soft delete. → `204 No Content`

판매완료 상품도 삭제할 수 있다. 상품 행·거래 기록·디스크 이미지 파일은 보존한다.
삭제 후 목록·판매내역·상세에서 제외한다. 없는 상품·이미 삭제한 상품의 쓰기는 `404 PRODUCT_NOT_FOUND`, 타인의 상품 쓰기는 `403 FORBIDDEN`이다.
등록 외 수정·삭제·상태 변경·끌어올리기의 상품 ID가 0 이하이거나 형식이 틀리면 `400 INVALID_INPUT`이다.

### PATCH `/api/products/{id}/status` — 상태 변경
```json
{ "status": "RESERVED" }
```
허용 전이: `ON_SALE ↔ RESERVED`, `→ SOLD`.
역방향(`SOLD → ON_SALE`)은 `400`.

인증된 본인만 가능. → `200 OK`, 갱신된 상품 상세 응답.
같은 상태로의 요청과 `SOLD → RESERVED`도 `400 INVALID_STATE`다. 지원하지 않거나 누락된 상태는 `400 INVALID_INPUT`이다.
상품의 표시 상태만 바꾸며 거래 생성·결제·구매확정을 대신하지 않는다.

### POST `/api/products/{id}/bump` — 끌어올리기
본인만 가능. 마지막 끌어올리기로부터 24시간 미경과 시 `400`.

최초 등록 시각부터 24시간을 계산한다. 정확히 24시간 경과한 순간부터 가능하다.
`ON_SALE`·`RESERVED`만 허용하며 판매완료 또는 시간 미경과는 `400 INVALID_STATE`다.
동시 요청은 상품 행을 잠가 검사하므로 같은 시점의 중복 끌어올리기는 하나만 성공한다. 성공은 `200 OK`다.
```json
{ "bumpedAt": "2026-09-14T20:10:00" }
```

### GET `/api/products/me?status=` — 내 판매내역
인증 필요. `status`로 필터(`ON_SALE`/`RESERVED`/`SOLD`). 커서 페이징.

`status` 생략 시 전체 상태를 포함한다. 삭제한 상품은 제외한다. 지원하지 않는 상태는 `400 INVALID_INPUT`이다.
`createdAt DESC, id DESC` 순으로 조회하며 끌어올리기는 판매내역의 순서를 바꾸지 않는다.
`cursor`, `size`는 공통 규약을 따르고 응답은 상품 목록의 `CursorResponse<ProductSummaryResponse>`와 같다.
본인 상품이므로 `isLiked`는 항상 `false`다. 빈 결과는 `content: []`, `nextCursor: null`, `hasNext: false`다.

---

## 4. 찜

### POST `/api/products/{id}/favorite` — 찜하기
인증 필요. 본인 상품은 `400`. 이미 찜했으면 `409`.
```json
// 200 OK
{ "isLiked": true, "favoriteCount": 13 }
```

### DELETE `/api/products/{id}/favorite` — 찜 해제
```json
{ "isLiked": false, "favoriteCount": 12 }
```

### GET `/api/users/me/favorites` — 내 찜 목록
인증 필요. 커서 페이징. 응답은 상품 목록과 동일한 형식이며 `isLiked`는 항상 `true`다.

최근에 찜한 순(`찜한 시각 DESC, 찜 id DESC`)이다. 커서 정렬값은 상품이 아니라 **찜한 시각**이므로 상품 목록 커서와 섞어 쓸 수 없다.
삭제된 상품은 목록에서 제외한다(찜 데이터는 남는다). 찜이 없으면 `content: []`.

**찜하기/해제 응답 보충**
- 없는 상품·삭제된 상품을 찜하면 `404 PRODUCT_NOT_FOUND`.
- 찜하지 않은 상품의 해제는 에러 없이 `200`과 현재 상태(`isLiked: false`)를 준다. 화면 상태가 서버와 어긋나도 한 번 더 눌러 맞출 수 있게 하기 위함이다.
- 찜 수(`favoriteCount`)는 DB에서 원자적으로 증감하므로 동시에 눌러도 유실되지 않으며, 0 미만으로 내려가지 않는다. 찜으로 상품의 수정 시각은 바뀌지 않는다.

---

## 5. 이미지 `/api/images`

### POST `/api/images` — 이미지 업로드
`multipart/form-data`, 필드명 `files` (복수 허용).
```json
// 201 Created
{ "imageUrls": ["/api/images/2026/09/16/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg"] }
```

인증 필요. 한 번에 최대 10장, 장당 5MB 이하.

| 상황 | 응답 |
|---|---|
| 이미지가 아닌 파일 | `400 UNSUPPORTED_IMAGE_TYPE` |
| 장당 5MB 초과 | `400 IMAGE_TOO_LARGE` |
| 빈 파일 / 10장 초과 | `400 INVALID_INPUT` |
| 비로그인 | `401 UNAUTHORIZED` |

**형식은 확장자나 Content-Type 이 아니라 파일 내용(시그니처)으로 판별한다.** 둘 다 클라이언트가 정하는 값이라, 실행 파일 이름만 `.jpg` 로 바꿔 올리는 것을 막지 못한다. 저장 확장자도 판별한 형식을 따른다.

**저장 파일명은 UUID 로 바꾼다.** 원본 파일명을 쓰면 경로 조작(`../`)과 중복·한글 파일명 문제가 생긴다. 날짜별 폴더(`yyyy/MM/dd`)로 나눠 한 폴더에 파일이 몰리지 않게 한다.

여러 장 중 하나라도 실패하면 **그 요청에서 이미 저장한 파일을 지운다.** 실패한 요청의 파일이 디스크에 남지 않게 하기 위함이다.

### GET `/api/images/thumb/{원본과 같은 경로}` — 목록용 축소본
업로드 때 만들어 둔 축소본(긴 변 640px)을 돌려준다. 원본 주소의 `/api/images/` 뒤에 `thumb/` 만 붙이면 된다.

```
원본   /api/images/2026/09/18/uuid.jpg
축소본 /api/images/thumb/2026/09/18/uuid.jpg
```

- 축소본이 없으면(webp · 원본이 이미 640px 이하 · 이 기능 도입 전 사진) **원본을 대신 내려준다.** 화면은 실패를 따로 다루지 않아도 된다.
- 없는 사진은 `404`. 캐시 정책은 원본과 같다(30일).

### GET `/api/images/{경로}` — 이미지 조회
비로그인도 볼 수 있다. 업로드 응답의 URL 을 그대로 쓴다. 없는 파일은 `404`.

업로드했지만 상품에 연결되지 않은 파일은 그대로 남는다(정리 배치는 미구현).
저장소는 **로컬 디스크**라 배포 시 인스턴스가 바뀌면 파일이 사라진다. 볼륨을 붙이거나 S3 로 옮겨야 한다.

**업로드와 상품 등록을 분리한 이유**: 사용자가 사진을 고르는 즉시 업로드해야 등록 버튼을 눌렀을 때 기다리지 않는다. 등록 API는 이미 올라간 URL만 받는다.

---

## 6. 카테고리 · 동네

세 API 모두 비로그인으로 호출할 수 있다. 단, 만료·위조된 Bearer 토큰을 보내면 공통 규약대로 `401`이다.
커서 페이징을 사용하지 않고 JSON 배열을 반환하며, 결과가 없으면 `200 OK`와 `[]`를 반환한다.

### GET `/api/categories`

전체 카테고리(자식 포함)를 평면 배열로 반환한다. `sortOrder ASC, id ASC` 순서다.

```json
[
  { "id": 1, "name": "디지털", "iconUrl": null, "sortOrder": 1 }
]
```

### GET `/api/regions?keyword=역삼` — 동네 검색

`keyword`는 필수다. 앞뒤 공백 제거 후 1~82자이며 공백만 있는 검색어는 허용하지 않는다.
누락·공백·길이 초과는 `400 INVALID_INPUT`, `errors`의 `field`는 `keyword`다.

`fullName`(시/도 + 공백 + 시/군/구 + 공백 + 읍/면/동)의 부분 일치 검색이다.
예: `역삼`, `강남구`, `강남구 역삼` 모두 검색할 수 있다.
`%`, `_`, `!`는 검색 패턴이 아닌 문자 그대로 취급한다.
일치하는 전체 결과를 `id ASC`로 반환한다.

```json
[
  { "id": 1, "sido": "서울특별시", "sigungu": "강남구", "dong": "역삼동", "fullName": "서울특별시 강남구 역삼동" }
]
```

### GET `/api/regions/nearby?lat=&lng=` — 근처 동네

| 파라미터 | 타입 | 필수 | 범위 |
|---|---|---|---|
| `lat` | double | O | -90 이상 90 이하 |
| `lng` | double | O | -180 이상 180 이하 |

누락·빈 값·숫자 변환 실패·범위 초과·`NaN`·무한대는 `400 INVALID_INPUT`이다.
`errors`의 `field`는 잘못된 파라미터(`lat` 또는 `lng`)다.

입력 좌표와 동네 중심 좌표 사이의 구면 거리(Haversine, 지구 반지름 6371.0088km)가 가까운 순으로 최대 10개를 반환한다.
거리 동률은 `id ASC`로 정렬한다. 고정 반경 제한은 없으며 전체 동네가 10개 미만이면 모두 반환한다.
응답 필드는 동네 검색과 동일하다(`id`, `sido`, `sigungu`, `dong`, `fullName`). 거리 필드는 포함하지 않는다.

후보 조회 반경을 넓히는 것은 내부 최적화이며 응답에 반경 제한을 추가하지 않는다.

---

## 7. 검색

### GET `/api/search/keywords/popular` — 인기 검색어
비로그인 가능. 최근 24시간 검색 횟수 TOP 5(배열 응답). 검색 기록이 없으면 `[]`.
```json
[
  { "rank": 1, "keyword": "에어팟", "count": 142 }
]
```

- 횟수가 같으면 검색어 순으로 순위를 고정한다(요청마다 순위가 뒤섞이지 않게).
- **결과를 서버가 1분 동안 기억한다.** 방금 한 검색은 최대 1분 뒤에 반영된다. 홈 방문마다 24시간치 로그를 집계하지 않기 위해서다.
- 전체 동네 기준이다(동네별 집계는 인덱스에 동네 컬럼이 없어 하지 않는다).

**검색 로그 적재**: `GET /api/products`에 `keyword`가 있고 **첫 페이지(`cursor` 없음)**일 때만 남긴다. "더 보기"까지 세면 결과를 많이 넘겨본 검색어가 부풀려진다.

- **비동기로** 저장한다. 검색 응답이 로그 저장을 기다리지 않는다. 조회가 끝난 뒤 전용 실행기(스레드 2개·대기열 1,000건)에 넘기고, 대기열이 넘치면 로그를 버린다(검색은 영향받지 않는다).
- 검색어는 앞뒤 공백 제거·연속 공백 하나로·영문 소문자로 맞춰 센다(`"iPad "` 와 `"ipad"` 는 같은 검색어). 상품 검색 결과에는 이 정규화를 쓰지 않는다.
- 로그인했으면 회원, 없는 동네 id 로 검색했으면 동네를 비워서 남긴다.
- **검색 로그는 7일이 지나면 매일 새벽 4시 정리 배치가 지운다.** 인기 검색어는 24시간치만 쓴다.

---

## 구현 순서 권장

1. 공통 (에러 핸들러, 응답 포맷, 커서 유틸)
2. 인증 (signup / login / reissue)
3. 카테고리 · 동네 조회
4. 상품 목록 · 상세 (읽기 전용)
5. 이미지 업로드 → 상품 등록/수정/삭제
6. 찜, 상태 변경, 끌어올리기
7. 검색 로그 · 인기 검색어

4번까지 되면 프론트 초안이 실제 데이터로 움직인다. **거기서 한 번 배포하는 것을 권장한다.**

---

## 8. 거래 (1단계 범위)

결제·환불은 범위에서 제외했다. `PAID`·`SHIPPING` 상태와 구매확정 API 는 결제 확장을 위해 남겨 두었고, 현재는 데모 시드 데이터로만 이 상태가 생긴다.
여기서는 마이페이지에 필요한 **구매내역 조회와 구매확정**만 정의한다.
**거래는 채팅방에서 만든다(11절 직거래).** 데모 데이터의 결제·발송 상태 거래는 `scripts/seed_demo_data.sql`로 넣는다.

### GET `/api/users/me/purchases` — 내 구매내역
인증 필요. 커서 페이징(정렬값: 거래 생성 시각). 최근 거래 순.

```json
{
  "content": [
    {
      "tradeId": 1,
      "status": "PAID",
      "amount": 145000,
      "canConfirm": true,
      "canReview": false,
      "product": {
        "id": 44,
        "title": "에어팟 프로 2세대",
        "thumbnailUrl": "https://.../airpods.jpg",
        "status": "RESERVED",
        "deleted": false
      },
      "seller": { "id": 10, "nickname": "역삼이웃" },
      "createdAt": "2026-09-14T11:52:00",
      "completedAt": null
    }
  ],
  "nextCursor": "2026-09-12T11:52:00_3",
  "hasNext": true
}
```

`status`: `REQUESTED` 예약중(직거래)·거래요청 / `PAID` 결제완료 / `SHIPPING` 배송중 / `CONFIRMED` 구매확정 / `CANCELED` 취소 / `REFUNDED` 환불.

`canConfirm`은 **지금 구매확정을 누를 수 있는지**를 서버가 계산해 준 값이다(`PAID`·`SHIPPING`에서만 `true`). 직거래 예약(`REQUESTED`)은 판매자가 채팅방에서 거래완료하므로 `false`다.
상태 전이 규칙이 프론트와 서버 두 곳으로 갈라지지 않게 하기 위한 것이므로, 프론트는 이 값만 보고 버튼을 노출한다.

**삭제된 상품의 거래도 목록에 남는다.** 구매내역은 기록이므로 판매자가 글을 내려도 사라지면 안 된다. 이때 `product.deleted`가 `true`이고 상세로 이동할 수 없다. (찜 목록은 반대로 삭제된 상품을 제외한다)

### PATCH `/api/trades/{id}/confirm` — 구매확정
인증 필요. **구매자만** 할 수 있다. 응답은 갱신된 구매내역 한 건(위 `content` 항목과 같은 형식).

거래는 `CONFIRMED`가 되고 `completedAt`이 채워지며, **상품은 판매완료(`SOLD`)가 된다.**

판매자가 먼저 상품을 `SOLD`로 표시했더라도 거래가 `PAID`·`SHIPPING`이면 구매확정은 가능하다.
상품 표시와 구매자의 수령 확인은 별개이므로 이미 판매완료인 상품은 그대로 유지한다.
상품 → 거래 순서로 잠금을 잡아 상품 쓰기·중복 구매확정과의 동시 충돌을 막는다.

| 상황 | 응답 |
|---|---|
| `PAID`·`SHIPPING`이 아닌 거래 | `400 INVALID_STATE` |
| 남의 거래 | `404 TRADE_NOT_FOUND` (존재 여부를 알려주지 않기 위해 403이 아니다) |
| 없는 거래 | `404 TRADE_NOT_FOUND` |

---

## 9. 채팅 (1단계: REST)

판매자-구매자 1:1 채팅. 모든 API는 인증 필요.
실시간 전달(WebSocket)은 이 API 위에 얹는다. 연결이 끊겼을 때 다시 불러오는 경로로 아래 API는 계속 쓴다.

**접근 규칙**: 방의 참여자이면서 **나가지 않은 사람**만 방 조회·메시지 조회/전송·읽음·나가기를 할 수 있다.
그 외(남의 방, 이미 나간 방, 없는 방)는 모두 `404 CHAT_ROOM_NOT_FOUND`다. 403을 주면 그 id의 방이 존재한다는 사실이 드러난다.

### POST `/api/products/{id}/chat-rooms` — 채팅하기
본문 없음. 같은 상품에 이미 방이 있으면 **새로 만들지 않고 그 방을 돌려준다.**
새로 만들면 `201 Created`, 기존 방이면 `200 OK`. 응답 본문은 같다.

```json
{
  "roomId": 7,
  "product": {
    "id": 44, "title": "원목 식탁", "price": 80000,
    "thumbnailUrl": "/api/images/2026/09/16/....jpg",
    "status": "ON_SALE", "deleted": false
  },
  "opponent": { "id": 10, "nickname": "판매자", "profileImageUrl": null, "mannerTemp": 36.5, "withdrawn": false },
  "myRole": "BUYER",
  "opponentLeft": false,
  "trade": null,
  "tradeActions": { "reserve": false, "cancel": false, "complete": false, "review": false },
  "createdAt": "2026-09-17T10:20:00"
}
```

`opponent`와 `myRole`은 **요청한 사람 기준**이다. 프론트가 자기 id와 비교해 상대를 계산하지 않아도 된다.
`myRole`: `BUYER` / `SELLER`. 판매자에게만 보일 버튼(예약·거래완료)을 가르는 데 쓴다.

| 상황 | 응답 |
|---|---|
| 자기 상품 | `400 CANNOT_CHAT_OWN_PRODUCT` |
| 판매완료(`SOLD`) 상품에 **새** 방 | `400 PRODUCT_NOT_CHATTABLE` (기존 방은 `200`으로 열린다) |
| 삭제됐거나 없는 상품 | `404 PRODUCT_NOT_FOUND` |

- 상품의 `chatCount`는 **방이 새로 생겼을 때만** 1 오른다. 같은 사람이 여러 번 눌러도 그대로다.
- 동시에 여러 번 눌러도 방은 하나다. 상품 행을 잠가 같은 상품의 방 만들기를 순서대로 처리한다.
- 나갔던 구매자가 다시 누르면 같은 방이 목록에 되살아난다.

### GET `/api/chat-rooms` — 내 채팅 목록
커서 페이징(정렬값: 마지막 메시지 시각). 최근 메시지 순.

```json
{
  "content": [
    {
      "roomId": 7,
      "product": { "id": 44, "title": "원목 식탁", "price": 80000, "thumbnailUrl": "...", "status": "ON_SALE", "deleted": false },
      "opponent": { "id": 12, "nickname": "구매자", "profileImageUrl": "/api/images/2026/09/17/....jpg", "mannerTemp": 36.5, "withdrawn": false },
      "lastMessage": "네고 가능할까요?",
      "lastMessageAt": "2026-09-17T10:21:30",
      "unreadCount": 2
    }
  ],
  "nextCursor": null,
  "hasNext": false
}
```

- **메시지가 한 번도 오가지 않은 방은 나오지 않는다.** "채팅하기"만 누르고 떠난 방이 판매자 목록에 쌓이지 않게 하기 위함이다.
- 내가 나간 방은 나오지 않는다.
- `lastMessage`는 최대 200자로 잘린 미리보기다.
- `unreadCount`는 **상대가 보낸** 메시지 중 내가 읽지 않은 수다.

### GET `/api/chat-rooms/{id}` — 채팅방 정보
응답은 채팅하기와 같다. 목록에서 방에 들어가거나 새로고침할 때 쓴다.
`opponentLeft`가 `true`면 상대가 나간 방이다(메시지를 보내면 상대 목록에 다시 나타난다).
`opponent.withdrawn`이 `true`면 상대가 탈퇴했다. 대화 기록은 볼 수 있지만 메시지 전송·예약은 `400 CHAT_OPPONENT_WITHDRAWN`이고, 상대 프로필은 404다.

### GET `/api/chat-rooms/unread-count` — 안 읽은 메시지 합계
```json
{ "count": 3 }
```
헤더·하단 탭의 채팅 뱃지용. **내가 나가지 않은 방**에서 **상대가 보낸** 안 읽은 메시지 수의 합이다(목록의 `unreadCount`와 같은 기준).
실시간으로 수를 보내지 않는다. 프론트는 로그인·소켓 재연결·새 메시지·내 읽음 이벤트·화면 이동 때 다시 부른다.

### GET `/api/chat-rooms/{id}/messages` — 메시지 목록
커서 페이징. **최신 메시지부터** 내려준다. 화면은 뒤집어서 아래부터 쌓고, 위로 스크롤할 때 `nextCursor`로 이전 메시지를 불러온다.

```json
{
  "content": [
    {
      "id": 31, "roomId": 7, "senderId": 12, "type": "TEXT",
      "content": "네고 가능할까요?", "read": false,
      "createdAt": "2026-09-17T10:21:30"
    }
  ],
  "nextCursor": "30_30",
  "hasNext": true
}
```

- 정렬 기준이 메시지 id 자체라 커서가 `{id}_{id}` 형태다. 다른 목록처럼 해석하지 말고 그대로 돌려보낸다.
- `senderId`만 준다. 상대 닉네임은 채팅방 정보에 있다.
- `read`는 **상대가 읽었는지**다. 내가 보낸 메시지에 "읽음" 표시를 할 때 쓴다.
- **조회만으로는 읽음 처리되지 않는다.** 아래 읽음 API를 따로 부른다.

`type`: `TEXT` / `IMAGE` / `SYSTEM`. 1단계에서는 `TEXT`만 만들어진다.

### POST `/api/chat-rooms/{id}/messages` — 메시지 전송
```json
{ "content": "안녕하세요, 아직 판매중인가요?" }
```
`201 Created`, 응답은 메시지 한 건(위 `content` 항목과 같은 형식).

- `content`는 공백만으로 채울 수 없고 1000자 이하다. 어기면 `400 INVALID_INPUT`(`errors[].field = "content"`).
- 상대가 나간 방이면 상대 목록에 방이 다시 나타난다.
- 판매완료·삭제된 상품의 방에서도 대화는 계속할 수 있다.
- 상대가 탈퇴했으면 `400 CHAT_OPPONENT_WITHDRAWN`.

### PATCH `/api/chat-rooms/{id}/read` — 읽음 처리
본문 없음. `204 No Content`. 이 방에서 **상대가 보낸** 안 읽은 메시지를 모두 읽음으로 바꾼다.
읽을 것이 없어도 `204`다(여러 번 불러도 결과가 같다).

### DELETE `/api/chat-rooms/{id}` — 나가기
`204 No Content`. 내 목록에서만 사라지고 **상대는 대화를 계속 본다.** 나간 뒤에는 이 방에 접근할 수 없다(`404`).

- 나갈 때 안 읽은 메시지를 읽음 처리한다. 상대가 새 메시지를 보내 방이 다시 나타나면 **새 메시지만** 안 읽은 수에 잡힌다.
- 방이 다시 나타나면 나가기 전의 대화도 함께 보인다(나간 시점을 저장하지 않는다).

---

## 10. 채팅 실시간 (WebSocket · STOMP)

WebSocket은 **서버 → 클라이언트 알림(푸시)만** 맡는다. 메시지 전송·읽음 처리는 9절의 REST API를 쓴다.
보낸 사람은 REST 응답(201·400 등)으로 결과를 바로 알고, 검증·에러 형식이 한 곳에만 있게 하기 위함이다.

### 연결

| 항목 | 값 |
|---|---|
| 주소 | `ws(s)://{호스트}/api/ws` (SockJS 없음, 순수 WebSocket) |
| 프로토콜 | STOMP 1.2 |
| 인증 | **CONNECT 프레임** 헤더 `Authorization: Bearer {accessToken}` |
| 구독 | `/user/queue/chat` 하나 |
| 하트비트 | 서버 10초 (클라이언트도 10초 권장) |

- 브라우저 WebSocket은 연결 요청에 헤더를 붙일 수 없어서, 연결 요청(HTTP)은 열어두고 **첫 STOMP 프레임에서 인증한다.** 토큰을 URL 쿼리에 넣지 않는다(접속 로그에 남는다).
- 허용 출처는 REST의 CORS 설정(`CORS_ALLOWED_ORIGINS`)과 같다. 다른 출처의 연결 요청은 거부된다.
- 개발: Vite 프록시의 `/api`에 `ws: true`가 있어야 한다.
- 배포: **Vercel rewrites는 WebSocket을 중계하지 못한다.** 프론트가 백엔드 도메인에 직접 `wss://`로 연결한다.

### 거부

거부되면 서버가 `ERROR` 프레임을 보내고 연결을 끊는다. `message` 헤더에 코드가 들어 있다.

| 상황 | `message` | 프론트 동작 |
|---|---|---|
| CONNECT에 토큰 없음 | `UNAUTHORIZED` | 로그인 화면으로 |
| 토큰 만료 | `EXPIRED_TOKEN` | `/api/auth/reissue` 후 다시 연결 |
| 위조·형식 오류 토큰 | `INVALID_TOKEN` | 세션 삭제 후 로그인 화면으로 |
| `/user/queue/chat` 이외의 구독, `SEND` 프레임 | `FORBIDDEN` | (정상 클라이언트에서는 발생하지 않음) |

연결 중에 access token이 만료돼도 **이미 맺은 연결은 유지된다.** 다시 연결할 때 새 토큰으로 검증한다.

### 이벤트

한 연결로 채팅방 화면과 채팅 목록을 함께 갱신할 수 있도록 모든 이벤트에 `roomId`가 있다. 쓰지 않는 필드는 내려가지 않는다.

**새 메시지** — 두 참여자 모두에게 간다(보낸 사람의 다른 탭·기기를 맞추기 위해).
```json
{
  "type": "MESSAGE",
  "roomId": 7,
  "message": {
    "id": 31, "roomId": 7, "senderId": 12, "type": "TEXT",
    "content": "네고 가능할까요?", "read": false,
    "createdAt": "2026-09-17T10:21:30"
  }
}
```
`message`는 9절 메시지 목록의 항목과 같은 형식이다. 보낸 사람 화면은 REST 응답으로 이미 그렸을 수 있으므로 **`message.id`로 중복을 거른다.**

**읽음** — 한 방에서 `readerId`가 상대 메시지를 읽음 처리했다. 두 참여자 모두에게 간다.
```json
{ "type": "READ", "roomId": 7, "readerId": 10 }
```
상대 화면은 자기가 보낸 메시지의 `read`를 `true`로 바꾸고, 읽은 사람의 다른 탭은 안 읽은 수를 0으로 맞춘다.
실제로 읽음으로 바뀐 메시지가 있을 때만 보낸다.

### 전달 보장

- **커밋이 끝난 뒤에만** 보낸다. 롤백된 메시지는 전달되지 않는다.
- **최선 노력(best effort)이다.** 연결이 없던 동안의 이벤트는 다시 보내지 않는다. 다시 연결하면 REST(채팅 목록·메시지 목록)로 불러와 화면을 맞춘다.
- 서버 한 대 기준(메모리 브로커)이다.

---

## 11. 채팅방 직거래 (예약 · 예약 취소 · 거래완료)

결제 없이 만나서 거래하는 흐름이다. 채팅방에서 진행한다.

```
판매자: 예약       거래 REQUESTED  · 상품 RESERVED
판매자: 거래완료   거래 CONFIRMED  · 상품 SOLD
양쪽:   예약 취소  거래 CANCELED   · 상품 ON_SALE
```
결제는 범위에서 제외했으므로 상품의 거래 방식이 택배거래(`DELIVERY`)여도 이 흐름으로 진행한다. `PAID → SHIPPING → 구매자 구매확정(8절)`은 결제를 붙일 때를 위한 경로로 남겨 두었다.

### 채팅방 정보의 거래 필드
9절의 채팅방 응답(채팅하기·방 조회)과 아래 세 API의 응답에 들어 있다.

```json
{
  "trade": { "id": 12, "status": "REQUESTED", "amount": 80000, "completedAt": null },
  "tradeActions": { "reserve": false, "cancel": true, "complete": true, "review": false }
}
```

- `trade`: 이 방의 거래(진행 중이거나 완료). **거래가 없거나 취소됐으면 `null`**이다. 취소 뒤에는 다시 예약할 수 있다.
- `tradeActions`: **지금 누를 수 있는 버튼**을 서버가 계산한 값이다. 프론트는 이 값만 보고 버튼을 보여준다.

| 값 | `true`인 조건 |
|---|---|
| `reserve` | 판매자 · 이 방에 거래 없음 · 상품이 판매중(다른 방에 예약이 있으면 상품이 예약중이라 `false`) |
| `cancel` | 판매자·구매자 · 이 방의 거래가 `REQUESTED` |
| `complete` | 판매자 · 이 방의 거래가 `REQUESTED` |
| `review` | 거래가 `CONFIRMED`이고 요청자가 아직 후기를 작성하지 않음 |

### POST `/api/chat-rooms/{id}/reservation` — 예약
판매자만. 이 방의 구매자와 예약한다. 응답은 갱신된 채팅방 정보(`200`).

### DELETE `/api/chat-rooms/{id}/reservation` — 예약 취소
판매자·구매자 모두. 상품은 판매중으로 돌아간다. 응답은 갱신된 채팅방 정보(`200`).

### POST `/api/chat-rooms/{id}/completion` — 거래완료
판매자만. 거래는 `CONFIRMED`(`completedAt` 기록), 상품은 `SOLD`가 된다. 응답은 갱신된 채팅방 정보(`200`).

결제가 없어 서버가 대금을 맡아두지 않으므로 판매자가 완료를 표시해도 구매자가 손해 보지 않는다(당근마켓과 같은 방식).

### 에러

| 상황 | 응답 |
|---|---|
| 구매자가 예약·거래완료 | `403 SELLER_ONLY` |
| 탈퇴한 구매자에게 예약(`tradeActions.reserve`도 `false`) | `400 CHAT_OPPONENT_WITHDRAWN` |
| 상품이 판매중이 아님(다른 방에 예약됨·판매완료) | `400 INVALID_STATE` |
| 예약 없이 취소·거래완료, 완료된 거래를 취소 | `400 NO_RESERVATION` |
| 참여자가 아님·나간 방·없는 방 | `404 CHAT_ROOM_NOT_FOUND` |
| 삭제된 상품 | `404 PRODUCT_NOT_FOUND` |

### 채팅 · 상품과의 연동

- 예약·취소·완료마다 채팅방에 **시스템 메시지**(`type: "SYSTEM"`)가 남는다: "판매자가 예약했어요." / "구매자가 예약을 취소했어요." / "거래가 완료됐어요." 일반 메시지와 같이 목록 미리보기와 실시간 전달(10절 `MESSAGE` 이벤트)에 반영된다. 상대 화면은 이 이벤트를 받으면 채팅방 정보를 다시 불러와 거래 상태를 맞춘다.
- **진행 중 거래(`REQUESTED`·`PAID`·`SHIPPING`)가 있으면 상품 상태 변경(`PATCH /api/products/{id}/status`)과 삭제는 `409 TRADE_IN_PROGRESS`다.** 막지 않으면 거래는 예약인데 상품만 판매중이 되는 식으로 어긋난다.
- 같은 상품을 두 채팅방에서 동시에 예약해도 한 건만 성공한다. 상품 → 채팅방 → 거래 순서로 잠근다.
- 예약된 거래는 구매내역(8절)에 `REQUESTED`로 보이며 `canConfirm`은 `false`다.

---

## 12. 리뷰 · 매너온도

### 작성 정책

- 거래가 `CONFIRMED`인 판매자·구매자만 상대방에게 각각 한 번 작성한다. 상품의 `SOLD` 표시만으로는 작성할 수 없다.
- 작성 기한 없음. 작성 즉시 공개하고 온도에 반영한다. 후기 수정·삭제 API는 제공하지 않는다.
- 상품이 삭제되거나 채팅방을 나갔어도 거래 당사자의 작성 자격은 유지된다.
- 점수 1·2·3·4·5점은 각각 매너온도 -0.4·-0.2·0·+0.2·+0.4℃. 범위는 0.0~99.9℃다.
- 리뷰 저장과 온도 갱신은 한 트랜잭션이다. 중복·실패·롤백 시 온도가 추가 반영되지 않는다.
- 후기로 시스템 메시지를 만들거나 나간 채팅방을 다시 열지 않는다. 후기의 실시간 이벤트도 없다.

### POST `/api/trades/{tradeId}/reviews` — 후기 작성

인증 필수. 평가받는 사람은 서버가 거래 상대방으로 결정하므로 요청에 상대 ID를 받지 않는다.

```json
{ "score": 5, "content": "친절하게 거래했어요." }
```

- `score`: 필수 정수, 1~5.
- `content`: 선택, 최대 500자. 앞뒤 공백 제거, 공백뿐이면 null. 최대 길이는 원본 입력 기준.

성공 `201`, 아래와 같은 공개 후기 한 건을 반환한다.

```json
{
  "id": 21,
  "reviewer": { "id": 10, "nickname": "골목이웃", "profileImageUrl": null },
  "score": 5,
  "content": "친절하게 거래했어요.",
  "createdAt": "2026-09-17T12:00:00"
}
```

| 상황 | 응답 |
|---|---|
| 미인증 | `401 UNAUTHORIZED` |
| 없는 거래·거래 당사자가 아님 | `404 TRADE_NOT_FOUND` |
| 완료 전·취소·환불 거래 | `409 REVIEW_NOT_ALLOWED` |
| 이미 작성한 거래 | `409 ALREADY_REVIEWED` |
| 잘못된 평점·500자 초과·잘못된 ID | `400 INVALID_INPUT` (본문 검증은 `errors[{field,reason}]`) |

### GET `/api/users/{id}/reviews` — 받은 후기

비로그인 공개. `cursor`, `size` 사용(기본 20, 최대 50, 범위 보정은 공통 규칙).
`createdAt DESC, id DESC` 정렬, 커서는 `{createdAt}_{id}`. `size + 1`건을 조회한다.
응답은 `CursorResponse`: `content`는 위 후기 응답의 배열, `nextCursor`, `hasNext`.
없는 사용자는 `404 USER_NOT_FOUND`, 잘못된 커서는 `400 INVALID_INPUT`.
공개 응답에 거래 ID·금액·채팅방·이메일은 포함하지 않는다.

### 프로필 · 기존 응답 확장

- 2절의 `GET /api/users/{id}` 공개 프로필을 구현했다. `productCount`는 삭제되지 않은 해당 사용자의 모든 상태 상품 수, `reviewCount`는 받은 후기 수다. 후기는 프로필에 넣지 않고 위 API로 페이지 조회한다.
- 8절의 구매내역·구매확정 응답에 `canReview` 추가: `CONFIRMED`이고 본인이 아직 작성하지 않았으면 true.
- 9·11절의 채팅방 응답에 `tradeActions.review` 추가. 작성 후 채팅방·구매내역·프로필을 재조회한다.
- 프론트는 채팅방 거래 줄(양쪽)과 구매내역 카드(구매자)에서 작성한다. 상대 프로필과 마이페이지의 받은 후기에서 조회한다.

---

## 13. 알림

찜·가격 인하·거래·후기 소식을 알림함에 모은다. 모든 API는 인증 필요.

### 무엇을 알리나

| `type` | 언제 | 받는 사람 | `title` | `content` | `targetUrl` |
|---|---|---|---|---|---|
| `FAVORITE` | 내 상품을 누가 찜함 | 판매자 | 누군가 내 상품을 찜했어요 | 상품 제목 | `/products/{id}` |
| `PRICE_DROP` | 가격을 내림 | 그 상품을 찜한 사람 전원 | 찜한 상품의 가격이 내려갔어요 | `제목 · 80,000원 → 70,000원` (0원은 `나눔`) | `/products/{id}` |
| `TRADE` | 예약 | 구매자 | 판매자가 예약했어요 | 상품 제목 | `/chat-rooms/{id}` |
| `TRADE` | 예약 취소 | 상대 | 구매자가·판매자가 예약을 취소했어요 | 상품 제목 | `/chat-rooms/{id}` |
| `TRADE` | 거래완료 | 구매자 | 거래가 완료됐어요. 후기를 남겨 주세요 | 상품 제목 | `/chat-rooms/{id}` |
| `TRADE` | 후기를 받음 | 후기 받은 사람 | 새 후기를 받았어요 | `닉네임님 · 5점` | `/my/reviews` |

- **버튼을 누른 본인에게는 알림이 없다.** 가격을 올리거나 그대로 두면 알림이 없다.
- **채팅 메시지는 알림을 만들지 않는다.** 채팅 목록의 안 읽은 수(9절)가 대신한다. `CHAT`·`SYSTEM` 타입은 스키마에만 있다.
- **찜 알림은 합친다.** 같은 상품에 대한 안 읽은 찜 알림이 이미 있으면 새로 만들지 않는다(찜을 눌렀다 풀었다 반복해도 쌓이지 않게). 읽은 뒤 새로 찜하면 다시 알린다. 누가 찜했는지는 담지 않는다.
- `targetUrl`은 화면 경로다. 프론트는 이 형식만 해석하고 모르는 경로면 이동하지 않는다.
- 제목·본문이 길면 100자·255자로 자른다.

### 언제 저장되나 (전달 보장)

- **원래 동작(찜·거래·후기)이 커밋된 뒤, 별도 트랜잭션에서 저장한다.** 원래 동작이 실패·롤백되면 알림도 없다.
- **알림 저장이 실패해도 원래 요청은 성공 응답을 받는다.** 대신 커밋 직후 서버가 죽으면 그 알림은 빠질 수 있다(최선 노력).

**보관 기간**: 매일 새벽 4시 정리 배치가 **읽은 알림은 30일, 안 읽은 알림은 90일**이 지나면 지운다(생성 시각 기준).

### GET `/api/notifications` — 내 알림 목록
커서 페이징(정렬값: 알림 생성 시각). 최신순.

```json
{
  "content": [
    {
      "id": 31,
      "type": "TRADE",
      "title": "판매자가 예약했어요",
      "content": "원목 식탁",
      "targetUrl": "/chat-rooms/7",
      "read": false,
      "createdAt": "2026-09-17T19:05:34"
    }
  ],
  "nextCursor": null,
  "hasNext": false
}
```

### GET `/api/notifications/unread-count` — 안 읽은 알림 수
```json
{ "count": 3 }
```

### PATCH `/api/notifications/{id}/read` — 읽음
`204`. 이미 읽은 알림도 `204`. 남의 알림·없는 알림은 `404 NOTIFICATION_NOT_FOUND`.

### PATCH `/api/notifications/read-all` — 모두 읽음
`204`. 읽을 것이 없어도 `204`.

### 실시간

10절과 **같은 연결·같은 구독 주소(`/user/queue/chat`)**로 온다. 구독을 따로 하지 않는다.
```json
{ "type": "NOTIFICATION", "notification": { "id": 31, "type": "TRADE", "title": "...", "content": "...", "targetUrl": "/chat-rooms/7", "read": false, "createdAt": "..." } }
```
- 알림 트랜잭션이 커밋된 뒤에 보낸다. 끊긴 동안의 알림은 다시 오지 않으므로 **재연결 시 `unread-count`를 다시 불러온다.**

---

## 14. 신고 · 차단

신고는 **접수만 한다.** 신고 수로 상품을 자동으로 숨기지 않는다. 경쟁 판매자가 몰아서 신고하면
멀쩡한 상품이 내려가기 때문이다. 관리자 화면은 아직 없고 `reports` 표를 직접 본다.
신고당한 사람·차단당한 사람에게는 **아무 것도 알리지 않는다.**

### POST `/api/reports` — 신고

요청
```json
{ "targetType": "PRODUCT", "targetId": 12, "reason": "FRAUD", "detail": "선입금을 요구해요" }
```
| 필드 | 값 |
|---|---|
| `targetType` | `USER` · `PRODUCT` |
| `reason` | `SPAM`(광고·도배) · `FRAUD`(사기 의심) · `PROHIBITED`(거래 금지 물품) · `ABUSE`(욕설·비방) · `OTHER`(기타) |
| `detail` | 최대 500자. `OTHER` 면 **필수**. 공백만 적은 것은 안 적은 것으로 본다 |

응답 `201`
```json
{ "id": 3, "targetType": "PRODUCT", "targetId": 12, "reason": "FRAUD", "createdAt": "2026-09-20T14:02:11" }
```

| 상황 | 응답 |
|---|---|
| 같은 대상을 다시 신고 | `409 ALREADY_REPORTED` |
| 자기 자신 · 자기 상품 | `400 CANNOT_REPORT_SELF` |
| `OTHER` 인데 내용 없음 | `400 REPORT_DETAIL_REQUIRED` |
| 없는 상품 · 삭제된 상품 | `404 PRODUCT_NOT_FOUND` |
| 없는 회원 · 탈퇴한 회원 | `404 USER_NOT_FOUND` |

대상 종류가 다르면 별개다. 같은 사람의 상품과 사람 자체를 각각 신고할 수 있다.

### POST `/api/users/{id}/block` — 차단

`201`(본문 없음).

| 상황 | 응답 |
|---|---|
| 이미 차단한 사람 | `409 ALREADY_BLOCKED` |
| 자기 자신 | `400 CANNOT_BLOCK_SELF` |
| 없는 회원 · 탈퇴한 회원 | `404 USER_NOT_FOUND` |

### DELETE `/api/users/{id}/block` — 차단 해제

`204`. 차단하지 않은 사람을 해제해도 `204`다(멱등).

### GET `/api/users/me/blocks` — 차단 목록

최근에 차단한 순. 커서 페이징(`cursor`, `size`)은 다른 목록과 같다.
```json
{ "content": [ { "userId": 8, "nickname": "이웃", "profileImageUrl": null, "blockedAt": "2026-09-20T14:02:11" } ],
  "nextCursor": null, "hasNext": false }
```

### 프로필의 `blockedByMe`

`GET /api/users/{id}` 응답에 `blockedByMe`(boolean)가 추가됐다. 내가 이 회원을 차단했는지다.
화면이 "차단하기"와 "차단 해제" 중 무엇을 보일지 정한다. 비로그인이면 항상 `false`.
**상대가 나를 차단했는지는 알려주지 않는다**(차단당한 사실은 드러나지 않아야 한다).

### 차단하면 달라지는 것

| | 동작 | 방향 |
|---|---|---|
| 상품 목록·검색 | 그 사람 상품이 빠진다 | 차단한 쪽 화면에서만 |
| 상품 상세 | **그대로 보인다** | — |
| 채팅방 열기 · 메시지 전송 | `403 BLOCKED_USER` | 양방향 |
| 기존 채팅방의 신규 예약 | `403 BLOCKED_USER`, `tradeActions.reserve: false` | 양방향 |
| 기존 예약 취소 · 거래완료 | 기존 권한·상태 규칙에 따라 허용 | 진행 거래 정리를 보장 |
| 채팅 목록 · 안 읽은 뱃지 | 그 방이 빠진다(방과 대화는 남는다) | 양방향 |
| 알림 | 그 사람이 일으킨 알림이 오지 않는다 | 양방향 |

가격 인하 알림도 판매자가 일으킨 알림으로 본다. 판매자와 수신자 사이에 어느 방향으로든 차단이 있으면 생성하지 않는다.

`BLOCKED_USER` 의 문구는 **누가 묻느냐에 따라 다르다.** 차단한 쪽에는 "차단한 사용자예요. 차단을 해제하면 다시 대화할 수 있어요.",
차단당한 쪽에는 "지금은 이 사용자와 대화할 수 없어요." — 이유를 밝히지 않는다("차단"이라는 말이 보이면 차단당한 사실이 드러난다).

상세를 감추지 않는 이유: 차단은 숨김이 아니라 관계를 끊는 일이다. 주소로 들어온 상품이 404 가 되면
"왜 안 보이지"가 된다. 새 대화와 예약을 막고 기존 거래를 정리할 수는 있게 한다.

회원이 탈퇴하면 그 회원이 낀 차단 관계는 모두 지운다. 신고 기록은 남는다.

---

## 15. 웹 푸시

앱을 닫아도 휴대폰 알림을 받는다. 서버 `.env` 에 VAPID 키가 없으면 푸시만 꺼지고(`enabled: false`) 나머지는 그대로다.
모두 로그인이 필요하다.

### GET `/api/push/public-key`
```json
{ "enabled": true, "publicKey": "BP4z9KsN6nGR…" }
```
`publicKey` 는 브라우저 `pushManager.subscribe({ applicationServerKey })` 에 넘길 VAPID 공개키(base64url)다.

### POST `/api/push/subscriptions` — 이 기기 구독
브라우저 `PushSubscription.toJSON()` 과 같은 모양이다. `204`.
```json
{ "endpoint": "https://fcm.googleapis.com/fcm/send/…", "keys": { "p256dh": "BCVx…", "auth": "BTBZ…" } }
```
- 같은 `endpoint`(같은 기기)로 다시 구독하면 새로 만들지 않고 **주인과 키를 바꾼다**(다른 계정으로 로그인한 경우).
- `endpoint` 는 실제 푸시 서비스만 받는다: `fcm.googleapis.com`, `updates.push.services.mozilla.com`, `web.push.apple.com`, `*.notify.windows.com` (https, 기본 포트). 그 밖은 `400 INVALID_PUSH_SUBSCRIPTION` — 서버가 아무 주소로나 요청을 보내게 되는 것(SSRF)을 막는다.
- 키 형식이 틀리면 `400 INVALID_PUSH_SUBSCRIPTION`, 서버 키가 없으면 `503 PUSH_DISABLED`.
- `p256dh`는 비압축 65바이트 형식뿐 아니라 좌표 범위와 P-256 곡선 방정식도 검사한다. 곡선 밖의 키도 `400 INVALID_PUSH_SUBSCRIPTION`이다.

### DELETE `/api/push/subscriptions` — 이 기기 구독 해제
본문 `{ "endpoint": "…" }`, `204`. 남의 구독이거나 없으면 아무 일도 하지 않는다(`204`).
구독 주소는 기기 식별자라 쿼리가 아니라 본문으로 받는다(접속 로그에 남지 않게).

클라이언트는 로그아웃 시 브라우저 구독 해제를 먼저 완료하고 서버 삭제를 요청한다. 서버 삭제 실패만으로 로그아웃을 막지는 않는다(해제된 endpoint는 다음 404·410 발송 응답에서 정리). 브라우저 해제 실패 시에는 로그아웃 성공으로 표시하지 않는다.
앱 시작·계정 변경·세션 만료 시 현재 계정과 소유자가 다르거나 불명인 브라우저 구독을 해제한다. 새 계정은 설정에서 다시 켜야 한다. 켜짐 표시는 브라우저 구독 존재만으로 확정하지 않고 공개키 확인과 서버 재등록 성공 후 확정한다.

### 무엇을 보내나
| 계기 | 제목 · 본문 | 누르면 |
|---|---|---|
| 알림(찜·거래·후기·가격 인하) | 알림 제목 · 내용 | 알림의 `targetUrl` |
| 채팅 메시지(시스템 메시지 제외) | 보낸 사람 닉네임 · 메시지(120자) | `/chat-rooms/{id}` |

- **앱을 보고 있는 사람(WebSocket 연결 중)에게는 보내지 않는다.** 화면이 이미 실시간으로 바뀐다.
- 시스템 메시지는 같은 사건의 거래 알림이 따로 푸시되므로 보내지 않는다.
- 같은 채팅방 알림은 기기에서 하나로 겹친다(`tag`). 차단 관계면 알림이 만들어지지 않으므로 푸시도 없다.
- 본문은 RFC 8291(aes128gcm)로 암호화하고 RFC 8292(VAPID)로 서명한다. 푸시 서비스가 `404`·`410` 을 주면 그 구독을 지운다.
- 기기별 암호화·발송 실패를 격리하여 나머지 기기의 발송을 계속한다. 알림 클릭은 서비스 워커에서도 동일 출처의 앱 경로 허용 목록으로 제한한다.
