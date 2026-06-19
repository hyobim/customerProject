# 고객 주소록 API

CSV 파일을 원본으로 사용하고 메모리에서 고객 주소록을 조회·수정·삭제하는 Spring Boot REST API입니다. 별도 RDBMS나 임베디드 DB를 사용하지 않습니다.

## 실행

요구 환경은 Java 17 이상입니다.

```bash
./gradlew bootRun
```

Windows에서 현재 프로젝트 경로처럼 한글 정규화가 섞인 경로를 사용하면 Gradle Wrapper나 JUnit 클래스 로딩이 실패할 수 있습니다. 이 경우 영문 경로에 프로젝트를 복사해서 실행합니다.

기본 원본 파일은 프로젝트 루트의 `default_address.csv`입니다. 다른 파일을 사용하려면 다음과 같이 설정합니다.

```bash
./gradlew bootRun --args="--address.csv.path=/path/to/address.csv"
```

## 데이터 규칙

- 고객 필드: 주소, 전화번호, 이메일, 이름
- 모든 필드는 필수이며 빈 문자열과 공백 문자열을 허용하지 않습니다.
- 전화번호는 `0101231234`, `010-123-1234`, `010-1234-1234` 형식을 허용합니다.
- 전화번호는 숫자 기준으로 중복을 검사하고 표준 하이픈 형식으로 저장합니다.
- 이메일은 공백 없는 `아이디@도메인` 형식이며 대소문자를 구분하여 중복을 검사합니다.
- 이름과 주소 검색은 대소문자를 구분하지 않는 부분 일치 검색입니다.

## API

- `GET /api/customers`: 복수 필터를 AND로 조회합니다. 결과가 없으면 `200 OK`와 `[]`를 반환합니다.
- `PUT /api/customers/{phoneNumber}`: 경로의 기존 전화번호로 대상을 찾아 요청 본문의 전체 고객정보로 교체합니다.
- `DELETE /api/customers`: 전화번호, 이메일, 주소, 이름 중 정확히 하나의 조건으로 삭제합니다.

조회 파라미터:

- 필터: `phoneNumber`, `email`, `address`, `name`
- 정렬: `sortBy` (`phoneNumber`, `email`, `address`, `name`)
- 방향: `direction` (`asc`, `desc`)
- 생략한 정렬값은 전화번호 오름차순을 사용합니다.

수정 예시:

```http
PUT /api/customers/01000000000
Content-Type: application/json

{
  "address": "서울시 중구",
  "phoneNumber": "01012345678",
  "email": "new@hyundai.com",
  "name": "홍길동"
}
```

오류 상태는 잘못된 입력 `400`, 대상 없음 `404`, 전화번호·이메일 중복 `409`, 예상하지 못한 서버 오류 `500`으로 응답합니다.

## 구조와 설계

- Controller: HTTP 요청·응답 변환
- Service: 조회·수정·삭제 유스케이스와 입력 정책
- Repository: 전화번호 Map과 이메일 보조 인덱스
- Validation: CSV와 API가 공유하는 고객 검증 및 전화번호 정규화
- Persistence: UTF-8 CSV 로딩, 백업, 임시 파일 저장, 원본 교체

저장소는 `ReentrantReadWriteLock`으로 두 Map의 일관성을 보장합니다. 전화번호와 이메일 정확 검색은 평균 O(1), 이름과 주소 부분 검색은 O(n), 정렬은 O(n log n)입니다.

## CSV 시작·종료 정책

- 시작 시 `주소,연락처,이메일,이름` 헤더를 검증합니다.
- 잘못된 데이터 행은 Warning 로그 후 건너뛰고 다음 행을 계속 처리합니다.
- 파일이 없거나 읽을 수 없거나 헤더가 잘못되면 기동에 실패합니다.
- 0바이트 또는 헤더만 있는 파일은 빈 주소록으로 기동합니다.
- 정상 종료 시 원본을 `default_address_yyyyMMddHHmmss.bak.csv` 형식으로 백업합니다.
- 메모리 스냅샷은 임시 파일에 쓴 뒤 원본으로 교체합니다.
- 강제 종료나 프로세스 장애에서는 종료 저장을 완전히 보장할 수 없습니다.

## 테스트

```bash
./gradlew clean test
```

도메인 검증, 저장소와 동시성, 서비스, CSV 로딩·저장, MockMvc API 테스트를 포함합니다. 현재 한글 경로에서는 JUnit 클래스 로딩 문제가 발생하므로 영문 임시 경로 교차 검증 결과를 `docs/test-and-qa-report.md`에 기록했습니다.

상세 계약과 판단 근거는 다음 문서를 참고합니다.

- `docs/api-specification.md`
- `docs/implementation-decisions.md`
- `docs/test-and-qa-report.md`
