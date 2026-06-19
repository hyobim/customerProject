# 고객 주소록 API 명세

기본 경로는 `/api/customers`이며 JSON의 고객 필드 순서는 계약에 영향을 주지 않는다.

## 공통 고객 JSON

```json
{
  "address": "서울시 광진구",
  "phoneNumber": "010-1234-5678",
  "email": "hong@hyundai.com",
  "name": "홍길동"
}
```

## 조회

`GET /api/customers`

선택 파라미터는 `phoneNumber`, `email`, `address`, `name`, `sortBy`, `direction`이다. 복수 필터는 AND로 결합한다. 이름과 주소는 대소문자 구분 없는 부분 일치이며 전화번호와 이메일은 정확 일치다.

`sortBy`는 `phoneNumber`, `email`, `address`, `name`, `direction`은 `asc`, `desc`를 허용한다. 각 값을 생략하면 전화번호와 오름차순을 독립적으로 기본 적용한다.

성공은 고객 배열과 `200 OK`다. 결과가 없으면 `[]`를 반환한다.

## 수정

`PUT /api/customers/{phoneNumber}`

경로에는 수정 전 전화번호를 전달하고 본문에는 변경 후 고객정보 전체를 전달한다. 전화번호 변경을 허용하며 전화번호 Map과 이메일 인덱스를 하나의 쓰기 잠금 안에서 교체한다.

성공 응답은 `200 OK`다.

```json
{
  "before": {
    "address": "서울시 광진구",
    "phoneNumber": "010-0000-0000",
    "email": "hong@hyundai.com",
    "name": "홍길동"
  },
  "after": {
    "address": "서울시 중구",
    "phoneNumber": "010-1234-5678",
    "email": "new@hyundai.com",
    "name": "홍길동"
  }
}
```

## 삭제

`DELETE /api/customers`

`phoneNumber`, `email`, `address`, `name` 중 정확히 하나의 파라미터만 허용한다. 주소와 이름은 부분 일치하므로 여러 고객이 삭제될 수 있으며 삭제된 고객 배열을 `200 OK`로 반환한다.

## 오류 응답

```json
{
  "timestamp": "2026-06-19T00:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "삭제 조건은 정확히 하나만 지정해야 합니다.",
  "path": "/api/customers"
}
```

| 상황 | 상태 |
|---|---:|
| 잘못된 필수값, 전화번호·이메일·정렬 형식 | 400 |
| 수정 또는 삭제 대상 없음 | 404 |
| 전화번호 또는 이메일 중복 | 409 |
| 예상하지 못한 서버 오류 | 500 |
