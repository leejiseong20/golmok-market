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

---

## 8. 거래 (1단계 범위)

거래 전체(요청·결제·취소·환불)는 2단계에서 채팅·결제와 함께 별도 문서로 다룬다.
여기서는 마이페이지에 필요한 **구매내역 조회와 구매확정**만 정의한다.
**거래를 생성하는 API는 아직 없다.** 데모 데이터는 `scripts/seed_demo_data.sql`로 넣는다.

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

`status`: `REQUESTED` 거래요청 / `PAID` 결제완료 / `SHIPPING` 배송중 / `CONFIRMED` 구매확정 / `CANCELED` 취소 / `REFUNDED` 환불.

`canConfirm`은 **지금 구매확정을 누를 수 있는지**를 서버가 계산해 준 값이다(`PAID`·`SHIPPING`에서만 `true`).
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
