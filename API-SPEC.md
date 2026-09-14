# 골목마켓 API 명세

1단계 범위: 인증 · 회원 · 상품 · 찜 · 카테고리 · 동네 · 검색
채팅 · 거래 · 결제는 2단계에서 별도 문서로 작성한다.

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
  "email": "user@example.com",
  "nickname": "골목이",
  "profileImageUrl": null,
  "mannerTemp": 36.5,
  "regions": [
    { "id": 1, "name": "역삼동", "isPrimary": true, "verifyCount": 3 }
  ]
}
```

### PATCH `/api/users/me` — 프로필 수정
```json
{ "nickname": "새닉네임", "profileImageUrl": "https://..." }
```

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
```json
{ "lat": 37.5006, "lng": 127.0366 }
```
좌표로 가장 가까운 `region`을 찾아 등록. 이미 등록된 동네면 `verifyCount`만 증가.
최대 2개. 3개째 시도하면 `409`.

### DELETE `/api/users/me/regions/{regionId}` — 동네 삭제
### PATCH `/api/users/me/regions/{regionId}/primary` — 대표 동네 지정

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
현재 LIKE 검색으로 구현하며 MySQL FULLTEXT 인덱스는 사용하지 않는다. 검색 로그 적재는 구현 순서 7번에서 추가한다.

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
  "imageUrls": ["https://.../1.jpg", "https://.../2.jpg"]
}
```
→ `201 Created`, 상세 응답과 동일한 본문.

검증: 제목 2~100자, 본문 10자 이상, 가격 0 이상, 이미지 1~10장.
`regionId`는 **본인이 인증한 동네여야 한다.** 아니면 `403`.

---

### PUT `/api/products/{id}` — 상품 수정
본인만 가능. 등록과 동일한 본문. `SOLD` 상태면 `400`.

### DELETE `/api/products/{id}` — 상품 삭제
본인만 가능. soft delete. → `204 No Content`

### PATCH `/api/products/{id}/status` — 상태 변경
```json
{ "status": "RESERVED" }
```
허용 전이: `ON_SALE ↔ RESERVED`, `→ SOLD`.
역방향(`SOLD → ON_SALE`)은 `400`.

### POST `/api/products/{id}/bump` — 끌어올리기
본인만 가능. 마지막 끌어올리기로부터 24시간 미경과 시 `400`.
```json
{ "bumpedAt": "2026-09-14T20:10:00" }
```

### GET `/api/products/me?status=` — 내 판매내역
인증 필요. `status`로 필터(`ON_SALE`/`RESERVED`/`SOLD`). 커서 페이징.

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
커서 페이징. 응답은 상품 목록과 동일한 형식.

---

## 5. 이미지 `/api/images`

### POST `/api/images` — 이미지 업로드
`multipart/form-data`, 필드명 `files` (복수 허용).
```json
// 201 Created
{ "imageUrls": ["https://.../abc.jpg", "https://.../def.jpg"] }
```

제약: 장당 5MB 이하, `jpg`/`jpeg`/`png`/`webp`만.

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
최근 24시간 집계 TOP 5.
```json
[
  { "rank": 1, "keyword": "에어팟", "count": 142 }
]
```

검색 로그 적재는 `GET /api/products`에 `keyword`가 있을 때 **비동기로** 처리한다. 검색 응답이 로그 저장을 기다릴 이유가 없다.

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
