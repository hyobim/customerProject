# 고객 주소록 API 서버

CSV 파일을 원본으로 사용하고, 메모리에 적재한 고객 정보를 조회·수정·삭제하는 Spring Boot REST API입니다.

별도의 RDBMS나 임베디드 DB 없이 전화번호 기반 메인 인덱스와 이메일 보조 인덱스를 사용합니다. 두 인덱스의 일관성을 동시 요청에서도 유지하고, 애플리케이션 정상 종료 시 원본 CSV를 백업한 뒤 메모리의 최종 상태를 저장합니다.

## 기술 스택

- Language: Java 17
- Framework: Spring Boot 3.0.2
- Build: Gradle 8.4, Groovy DSL
- Test: JUnit 5, AssertJ, Mockito, MockMvc
- Data Store: UTF-8 CSV + In-Memory `HashMap`

RDBMS와 H2 등의 임베디드 DB는 사용하지 않습니다.

## 빠른 시작

### 1. 실행 환경

JDK 17이 필요합니다.

```bash
java -version
```

### 2. 애플리케이션 실행

macOS/Linux:

```bash
./gradlew bootRun
```

Windows:

```powershell
.\gradlew.bat bootRun
```

기본 원본 파일은 프로젝트 루트의 `default_address.csv`입니다. 다른 CSV를 사용하려면 실행 인자로 경로를 지정합니다.

```bash
./gradlew bootRun --args="--address.csv.path=/path/to/address.csv"
```

```powershell
.\gradlew.bat bootRun --args="--address.csv.path=C:\data\address.csv"
```

서버는 기본적으로 `http://localhost:8080`에서 실행됩니다.


### 3. 테스트 실행

```bash
./gradlew clean test
```

Windows:

```powershell
.\gradlew.bat clean test
```

## API 엔드포인트

기본 경로는 `/api/customers`입니다.

| Method | Endpoint | 설명 |
|---|---|---|
| `GET` | `/api/customers` | 고객 조회 및 정렬 |
| `PUT` | `/api/customers/{phoneNumber}` | 기존 전화번호로 고객을 찾아 전체 정보 수정 |
| `DELETE` | `/api/customers` | 정확히 하나의 조건으로 고객 삭제 |

### 고객 조회

```http
GET /api/customers
```

| 파라미터 | 설명 |
|---|---|
| `phoneNumber` | 전화번호 정확 일치 |
| `email` | 이메일 정확 일치 |
| `address` | 대소문자를 구분하지 않는 부분 일치 |
| `name` | 대소문자를 구분하지 않는 부분 일치 |
| `sortBy` | `phoneNumber`, `email`, `address`, `name` |
| `direction` | `asc`, `desc` |

복수 필터는 AND로 결합합니다. 필터가 없으면 전체 고객을 조회합니다. 조회 파라미터가 전달된 경우 빈 문자열이나 공백만 있는 값은 `400 Bad Request`로 거부합니다. `sortBy`와 `direction`은 각각 생략할 수 있으며, 생략한 값에는 전화번호와 오름차순을 기본 적용합니다.

```bash
curl "http://localhost:8080/api/customers?address=서울&name=홍&sortBy=name&direction=asc"
```

조회 결과가 없으면 `200 OK`와 빈 배열을 반환합니다.

```json
[]
```

### 고객 수정

경로에는 현재 전화번호를, 본문에는 변경할 고객정보 전체를 전달합니다. 전화번호 자체도 변경할 수 있습니다.

```bash
curl -X PUT "http://localhost:8080/api/customers/01000000000" \
  -H "Content-Type: application/json" \
  -d '{
    "address": "서울시 중구",
    "phoneNumber": "01012345678",
    "email": "new@hyundai.com",
    "name": "홍길동"
  }'
```

성공 응답은 수정 전·후 데이터를 구분합니다.

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

### 고객 삭제

`phoneNumber`, `email`, `address`, `name` 중 비어 있지 않은 정확히 하나의 조건만 전달해야 합니다. 빈 문자열이나 공백 조건은 `400 Bad Request`로 거부되며 전체 고객 삭제로 이어지지 않습니다.

```bash
curl -X DELETE "http://localhost:8080/api/customers?email=new@hyundai.com"
```

주소와 이름은 부분 일치이므로 여러 고객이 함께 삭제될 수 있습니다. 응답은 삭제된 고객 배열입니다.

## 데이터 규칙

- 고객 필드는 주소, 전화번호, 이메일, 이름입니다.
- 모든 필드는 필수이며 빈 문자열과 공백 문자열을 허용하지 않습니다.
- 전화번호는 `0101231234`, `010-123-1234`, `010-1234-1234` 형식을 허용합니다.
- 전화번호는 숫자 기준으로 동일성을 판단하고 표준 하이픈 형식으로 저장·응답합니다.
  - 10자리: `010-XXX-XXXX`
  - 11자리: `010-XXXX-XXXX`
- 전화번호는 고객 데이터의 키이며 중복될 수 없습니다.
- 이메일은 공백 없는 `아이디@도메인` 형식이며 중복될 수 없습니다.
- 이메일 원문과 대소문자를 보존하며, 중복 검사와 정확 조회도 대소문자를 구분합니다.
- 이름과 주소의 “양방향 like 검색”은 검색어가 어느 위치에든 포함되는 부분 일치로 구현했습니다.

등록용 API는 별도로 제공하지 않으며, 초기 고객은 CSV에서 적재합니다.

## 오류 응답

사용자에게는 내부 구현이나 스택 트레이스를 노출하지 않고 일관된 JSON으로 응답합니다.

```json
{
  "timestamp": "2026-06-21T07:52:44.411Z",
  "status": 400,
  "error": "Bad Request",
  "message": "이메일은 아이디@도메인 형식이어야 합니다.",
  "path": "/api/customers"
}
```

| 상황 | HTTP Status |
|---|---:|
| 필수값, 전화번호·이메일 형식 또는 검색·정렬 조건 오류 | `400 Bad Request` |
| 지원하지 않는 HTTP 메서드 | `405 Method Not Allowed` |
| 지원하지 않는 응답 미디어 타입 | `406 Not Acceptable` |
| 지원하지 않는 요청 Content-Type | `415 Unsupported Media Type` |
| 수정 또는 삭제 대상 없음 | `404 Not Found` |
| 전화번호 또는 이메일 중복 | `409 Conflict` |
| 예상하지 못한 서버 오류 | `500 Internal Server Error` |


## 메모리 저장소와 동시성

저장소는 다음 두 인덱스를 함께 관리합니다.

```text
customersByPhone: 전화번호 -> 고객
phoneByEmail:     이메일   -> 전화번호
```

`ReentrantReadWriteLock`으로 두 인덱스의 복합 변경을 하나의 임계 구역에서 처리합니다.

- 조회·스냅샷: read lock
- 등록·수정·삭제: write lock
- 조회 필터링·정렬과 파일 I/O는 잠금 밖에서 수행해 잠금 점유 시간을 최소화
- 전화번호·이메일의 유일성과 두 인덱스의 일관성을 동시 요청에서도 유지

단일 `ConcurrentHashMap`만으로는 두 인덱스의 원자적 변경을 보장할 수 없어 읽기·쓰기 잠금을 선택했습니다.

### 동시성 테스트

`CountDownLatch`로 요청 시작 시점을 맞추고 실제 스레드 경쟁 상황을 검증했습니다.

| 테스트 상황 | 검증 결과 |
|---|---|
| 동일 고객 동시 등록 | 한 건만 성공 |
| 동일 고객 동시 수정 | 인덱스 일관성 유지 |
| 수정과 삭제의 경쟁 | 실행 순서와 관계없이 유효한 최종 상태 유지 |
| 반복 수정 중 스냅샷 조회 | 변경 중간 상태가 노출되지 않음 |

### 성능 및 메모리

| 작업 | 시간 복잡도 |
|---|---:|
| 전화번호·이메일 정확 조회 | 평균 `O(1)` |
| 이름·주소 부분 조회 및 삭제 | `O(n)` |
| 정렬 조회 및 종료 저장 | `O(n log n)` |
| 등록·수정 | 평균 `O(1)` |

정확 조회는 인덱스로 최적화하고, 이름·주소 부분 검색은 별도 검색 인덱스의 메모리와 갱신 비용을 피하기 위해 전체 순회를 선택했습니다. CSV는 레코드 단위로 읽어 불필요한 전체 파일 복사를 줄였습니다. 데이터가 커지면 부분 검색, 전체 정렬과 페이지네이션 없는 응답이 주요 확장 지점입니다.

## CSV 로딩과 종료 저장

### CSV 형식

```csv
주소,연락처,이메일,이름
서울시 광진구,01000000000,hong@hyundai.com,홍길동
```

UTF-8을 사용하며 BOM이 있는 헤더도 지원합니다. 필드 내부 쉼표, 이스케이프된 이중 따옴표, 필드 내부 개행, LF/CRLF와 마지막 빈 필드를 처리합니다.

### 시작 시 로딩

오류는 복구 가능한 레코드 오류와 레코드 경계를 신뢰할 수 없는 구조 오류로 구분합니다.

| 오류 | 처리 |
|---|---|
| 열 개수 오류 | Warning 로그 후 해당 레코드 skip |
| 필수값·전화번호·이메일 형식 오류 | Warning 로그 후 해당 레코드 skip |
| 전화번호·이메일 중복 | Warning 로그 후 해당 레코드 skip |
| 파일 없음 또는 읽기 불가 | 기동 실패 |
| 헤더 불일치 | 기동 실패 |
| 닫히지 않은 따옴표 등 CSV 문법 오류 | 기동 실패 |

CSV 문법 오류는 따옴표 안의 개행 때문에 다음 레코드의 시작 위치를 안전하게 판단할 수 없습니다. 잘못 복구해 여러 고객 데이터를 합치거나 손상시키는 것보다 fail-fast가 안전하다고 판단했습니다.

0바이트 파일과 헤더만 있는 파일은 빈 주소록으로 기동합니다.

### 정상 종료 시 저장

```text
원본 백업
   ↓
메모리 스냅샷을 같은 디렉터리의 임시 파일에 기록
   ↓
임시 파일을 원본 경로로 교체
   ↓
임시 파일 정리
```

- 백업 이름: `{원본명}_yyyyMMddHHmmss.bak.csv`
- 저장 순서: 전화번호 오름차순
- 원자적 이동을 지원하지 않는 파일 시스템에서는 일반 교체 이동 사용
- 원본 교체 실패 시 방금 생성한 백업으로 원본 복구 시도
- 성공·실패와 관계없이 저장용 임시 파일 정리

## 프로젝트 구조

```text
src/main/java/com/hyundai/test/address
├── controller
│   ├── AddressBookController
│   ├── GlobalExceptionHandler
│   └── dto
├── domain
│   ├── Customer
│   ├── CustomerChange
│   ├── CustomerSearchCondition
│   ├── SortField
│   └── SortDirection
├── exception
├── persistence
│   ├── CsvCodec
│   ├── CsvCustomerStore
│   └── CustomerDataLifecycle
├── repository
│   ├── CustomerRepository
│   └── InMemoryCustomerRepository
├── service
│   ├── AddressBookService
│   └── dto/CustomerSearchRequest
└── validation
    └── CustomerValidator
```

- Controller: HTTP 요청 바인딩과 응답 DTO 변환
- Service: 조회·수정·삭제 유스케이스와 입력 정책
- Repository: 인메모리 인덱스, 검색, 정렬과 동시성 제어
- Validation: API와 CSV가 공유하는 검증·정규화
- Persistence: CSV 파싱, 초기 적재, 백업과 종료 저장

## 테스트

최종 검증 결과:

```text
clean test bootJar --warning-mode all --no-daemon
BUILD SUCCESSFUL
테스트 스위트 9개
테스트 76개
실패 0개
오류 0개
스킵 0개
```

| 구분 | 주요 검증 |
|---|---|
| 검증 단위 테스트 | 전화번호·이메일·필수값 검증과 정규화 |
| 저장소 단위 테스트 | 검색, 중복, 인덱스 일관성과 동시성 경쟁 |
| 서비스 단위 테스트 | 복수 조건 조회, 수정·삭제와 모든 빈 검색·삭제 조건 검증 |
| CSV 단위 테스트 | 문법 경계값, 오류 정책, 저장·복구 실패 처리 |
| Controller 슬라이스 테스트 | MockMvc 요청 바인딩, 직렬화와 400·404·405·406·409·415·500 응답 |
| API 통합 테스트 | 실제 Service·Validator·Repository를 연결한 HTTP 계약과 빈 삭제 조건의 데이터 보존 |
| 생명주기 통합 테스트 | Spring Context 시작·종료, 백업, 저장과 재로딩 |

### 테스트 요구사항 충족

#### 1. 각 클래스의 단위 테스트

프로덕션 클래스의 책임을 전용 단위 테스트 또는 해당 계약을 실행하는 가장 가까운 계층 테스트에서 검증했습니다.

| 프로덕션 클래스·책임 | 검증 테스트 | 주요 검증 내용 |
|---|---|---|
| `CustomerValidator` | `CustomerValidatorTest` 6개 | 필수값, 전화번호·이메일 형식, 정규화와 이메일 대소문자 보존 |
| `AddressBookService` | `AddressBookServiceTest` 15개 | 복수 조건 조회, 수정, 삭제 조건, 빈 전화번호·이메일·주소·이름 검증 |
| `InMemoryCustomerRepository` | `InMemoryCustomerRepositoryTest` 7개 | 정확 검색, 중복 방지, 수정 시 보조 인덱스 갱신, 동시성 경쟁 |
| `CsvCodec` | `CsvCodecTest` 13개 | 쉼표·따옴표·개행·CRLF, BOM, 긴 필드와 엄격한 CSV 문법 검증 |
| `CsvCustomerStore` | `CsvCustomerStoreTest` 9개 | 초기 로딩, 잘못된 행 skip, 문법 오류 전파, 백업·저장·복구 |
| DTO·record·enum | `DtoBuilderTest` 및 Controller·Service 테스트 | Builder, 도메인 변환, JSON 직렬화, 정렬 값 매핑 |
| 예외와 `GlobalExceptionHandler` | `AddressBookControllerTest` | 예외별 400·404·405·406·409·415·500 상태와 사용자용 JSON 오류 응답 |

요구사항에 명시된 핵심 프로세스는 다음과 같이 검증했습니다.

| 요구사항 | 검증 테스트 | 검증 내용 |
|---|---|---|
| 초기 로딩 검증 | `CsvCodecTest`, `CsvCustomerStoreTest` | UTF-8 BOM과 헤더, 정상·오류 레코드 적재, 중복·형식 오류 skip, CSV 문법 오류 시 기동 실패 |
| 기본 기능 검증 | `AddressBookServiceTest`, `InMemoryCustomerRepositoryTest` | 조회·정렬·수정·삭제, 부분 일치 검색, 키·이메일 유일성과 동시성 |
| 종료 시 최종 파일 반영 | `CustomerDataLifecycleIntegrationTest` | 실제 Spring Context의 `@PostConstruct` 로딩과 `@PreDestroy` 저장, 원본 백업, 최종 CSV 반영 및 재로딩 |

단순 DTO·record·enum·예외는 동작 없는 클래스마다 형식적인 테스트 파일을 만들기보다 생성, 변환, 직렬화, 값 매핑과 예외 응답을 담당하는 가장 가까운 계층 테스트에서 계약을 검증했습니다.

#### 2. MockMvc 기반 Controller 단위 테스트

`AddressBookControllerTest`는 `@WebMvcTest(AddressBookController.class)`와 `MockMvc`를 사용합니다. 실제 Service는 `@MockBean`으로 대체하여 Controller의 HTTP 책임만 격리했습니다.
