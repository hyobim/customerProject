# 결함 수정 개발 계획서

## 1. 목표

QA에서 발견된 다음 결함을 수정하고 회귀 테스트를 보강한다.

| 우선순위 | 결함 | 목표 |
|---|---|---|
| Critical | 빈 삭제 조건으로 전체 고객 삭제 | 빈 값·공백 조건을 `400 Bad Request`로 차단 |
| Medium | 미지원 HTTP 요청이 500 반환 | HTTP 규약에 맞게 `405`, `415` 반환 |
| Low | 빈 조회 조건 처리 불일치 | 전화번호·이메일 빈 값 정책 통일 |

## 2. 수정 계획

### 2.1 삭제 조건 검증 강화

대상: `src/main/java/com/hyundai/test/address/service/AddressBookService.java`

현재 삭제 조건 개수 계산은 `null`만 제외한다. 이를 실제 값이 존재하는 조건, 즉 `null`·빈 문자열·공백 문자열이 아닌 값만 계산하도록 변경한다.

예상 정책:

```http
DELETE /api/customers?phoneNumber=%20%20%20
DELETE /api/customers?phoneNumber=%20%20%20
DELETE /api/customers?email=
```

모두 다음 결과를 반환한다.

```http
400 Bad Request
```

응답 메시지:

```text
삭제 조건은 비어 있지 않은 값으로 정확히 하나만 지정해야 합니다.
```

방어 계층:

- 서비스에서 유효한 조건 개수를 검증한다.
- Repository에는 필터 없는 삭제 요청이 전달되지 않도록 보장한다.
- 필요하다면 Repository에도 전체 삭제 방지 검증을 추가한다.

### 2.2 HTTP 예외 매핑 수정

대상: `src/main/java/com/hyundai/test/address/controller/GlobalExceptionHandler.java`

다음 Spring MVC 예외에 전용 핸들러를 추가한다.

| 예외 | HTTP 상태 |
|---|---:|
| `HttpRequestMethodNotSupportedException` | `405 Method Not Allowed` |
| `HttpMediaTypeNotSupportedException` | `415 Unsupported Media Type` |
| `HttpMediaTypeNotAcceptableException` | `406 Not Acceptable` |
| `MissingServletRequestParameterException` 등 요청 바인딩 오류 | `400 Bad Request` |

기존 JSON 오류 형식을 그대로 유지한다.

```json
{
  "timestamp": "...",
  "status": 405,
  "error": "Method Not Allowed",
  "message": "...",
  "path": "/api/customers"
}
```

범용 `Exception` 핸들러는 실제 예상 밖의 서버 오류에만 적용한다.

### 2.3 조회 조건 정책 통일

대상: `src/main/java/com/hyundai/test/address/service/AddressBookService.java`

권장 정책:

- 파라미터 미전달: 필터 없음
- 파라미터 전달 후 값이 빈 문자열 또는 공백: `400 Bad Request`
- 올바른 값 전달: 형식 검증 후 필터 적용

따라서 다음 요청은 모두 `400 Bad Request`로 통일한다.

```http
GET /api/customers?phoneNumber=
GET /api/customers?email=
GET /api/customers?address=
GET /api/customers?name=
```

README에도 이 정책을 명시한다.

## 3. 테스트 보강

### 3.1 서비스 단위 테스트

대상: `src/test/java/com/hyundai/test/address/service/AddressBookServiceTest.java`

추가 항목:

- 빈 전화번호 삭제 거부
- 공백 이메일 삭제 거부
- 빈 주소·이름 삭제 거부
- 유효 조건 하나와 빈 조건이 함께 전달된 경우 정책 검증
- 빈 조회 파라미터별 `InvalidSearchConditionException`

### 3.2 Controller 테스트

대상: `src/test/java/com/hyundai/test/address/controller/AddressBookControllerTest.java`

추가 항목:

- `POST /api/customers` → `405 Method Not Allowed`
- `Content-Type: text/plain` PUT → `415 Unsupported Media Type`
- 지원하지 않는 `Accept` → `406 Not Acceptable`
- 빈 삭제 조건 → `400 Bad Request`
- 공백 삭제 조건 → `400 Bad Request`
- 오류 응답 JSON 필드 검증

### 3.3 API 통합 테스트

대상: `src/test/java/com/hyundai/test/address/controller/AddressBookApiIntegrationTest.java`

가장 중요한 회귀 시나리오:

1. 고객 여러 건을 준비한다.
2. `DELETE /api/customers?phoneNumber=` 요청을 전송한다.
3. `400 Bad Request`를 확인한다.
4. 전체 고객을 다시 조회한다.
5. 모든 고객이 그대로 남아 있는지 확인한다.

## 4. 문서 수정

대상: `Readme.md`

다음을 반영한다.

- 빈 검색·삭제 조건의 처리 정책
- `405`, `406`, `415` 오류 상태
- 보강된 테스트 개수와 최종 실행 결과

## 5. 구현 순서

1. 삭제 API의 빈 조건 및 전체 삭제 방어
2. HTTP 예외별 상태 코드 매핑
3. 조회 API의 빈 파라미터 정책 통일
4. 서비스·Controller·통합 회귀 테스트 추가
5. 전체 테스트 및 bootJar 빌드
6. 실제 서버 기반 API 재검증
7. 종료 저장과 CSV 백업 회귀 검증
8. README 및 테스트 결과 갱신

## 6. 완료 기준

- 빈 삭제 조건으로 데이터가 삭제되지 않는다.
- 빈 값 또는 공백으로 구성된 삭제 조건은 모두 `400 Bad Request`를 반환한다.
- 미지원 HTTP 메서드는 `405 Method Not Allowed`를 반환한다.
- 미지원 Content-Type은 `415 Unsupported Media Type`을 반환한다.
- 허용되지 않는 응답 미디어 타입은 `406 Not Acceptable`을 반환한다.
- 빈 조회 파라미터가 정의된 정책에 따라 일관되게 `400 Bad Request`를 반환한다.
- 오류 응답은 기존 JSON 구조를 유지한다.
- 기존 65개 테스트와 신규 회귀 테스트가 모두 통과한다.
- 실제 서버에서 기존 결함 재현 요청을 다시 실행해 수정 여부를 확인한다.
- 종료 저장 및 CSV 백업 기능에 회귀가 없다.

## 7. 최종 산출물

- 결함이 수정된 애플리케이션 소스
- 서비스·Controller·API 통합 회귀 테스트
- 전체 테스트 및 빌드 결과
- 실제 HTTP API 재검증 결과
- 수정된 `Readme.md`

## 8. 최종 검증 결과

- 전체 테스트 스위트 9개, 테스트 76개 통과
- `clean test bootJar --warning-mode all --no-daemon` 성공
- 실제 HTTP 재검증에서 `400`, `405`, `406`, `415` 응답 및 JSON 오류 구조 확인
- 빈 삭제 조건 요청 후 고객 데이터 보존 확인
- 종료 저장 및 CSV 백업 생명주기 테스트 통과
