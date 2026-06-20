# 고객 주소록 API 세션 인수인계

## 1. 문서 목적

이 문서는 2026-06-20 기준 고객 주소록 API의 현재 구현 상태, 이번 세션에서 완료한 개선, 검증 결과, Git 상태, 남은 과제를 다음 Codex 세션 또는 개발자가 바로 파악할 수 있도록 정리한 인수인계 문서다.

새 세션에서는 가장 먼저 이 문서와 아래 문서를 함께 확인한다.

- `docs/improvement-plan.md`: 최초 개선 계획과 우선순위
- `docs/test-and-qa-report.md`: 실제 테스트 및 QA 결과
- `docs/implementation-decisions.md`: 설계 선택과 제약
- `Readme.md`: 실행 방법과 외부 사용 계약

## 2. 현재 Git 상태

현재 브랜치:

```text
improve/persistence-checkpoint
```

인수인계 문서 작성 전 기능·검증 HEAD:

```text
84214a5 docs: record persistence verification results
bdbc292 test: cover csv boundaries and concurrent mutations
1273912 refactor: consolidate validation and search input
1da09c5 test: verify shutdown persistence recovery
dec8d7e feat: implement customer address book API
```

`dec8d7e`는 `main`과 `origin/main`의 기준 커밋이고, 그 위에 이번 세션의 개선 커밋 4개가 있다. 이 인수인계 문서 커밋은 `84214a5` 다음에 추가되므로 새 세션에서는 `git log`로 실제 HEAD를 확인한다.

인수인계 문서 작성 직전 working tree에는 다음 파일만 untracked 상태였다.

```text
?? docs/improvement-plan.md
```

`docs/improvement-plan.md`는 기존 사용자가 제공한 문서로 판단하여 이번 세션의 기능 커밋에 포함하지 않았다. 새 세션에서도 임의 삭제·덮어쓰기·커밋하지 말고 먼저 Git 상태를 다시 확인한다.

확인 명령:

```powershell
git status --short --branch
git log --oneline --decorate -8
```

저장소 소유권 경고가 발생하는 실행 환경에서는 전역 Git 설정을 바꾸지 않고 명령 단위로 다음 옵션을 사용했다.

```powershell
git -c safe.directory='C:/Users/김효빈/OneDrive/Desktop/KIRO실습/customer/customerProject' status
```

## 3. 프로젝트 개요

- Java 17
- Spring Boot 3.0.2
- Gradle Wrapper 8.4
- 별도 DB 없이 CSV와 메모리 저장소 사용
- 시작 시 CSV를 메모리에 적재
- 실행 중 조회·수정·삭제는 메모리에서 처리
- 정상적인 Spring Context 종료 시 원본 CSV 백업 후 최종 메모리 상태 저장

주요 API:

- `GET /api/customers`: 복수 조건 AND 조회와 정렬
- `PUT /api/customers/{phoneNumber}`: 기존 전화번호로 고객을 찾아 전체 정보 교체
- `DELETE /api/customers`: 정확히 하나의 조건으로 고객 삭제

현재 API 계약은 변경하지 않았다.

## 4. 현재 구조

```text
Controller
  AddressBookController
  GlobalExceptionHandler
  controller/dto/*

Service
  AddressBookService

Domain
  Customer
  CustomerChange
  CustomerSearchRequest
  CustomerSearchCondition
  SortField
  SortDirection

Repository
  CustomerRepository
  InMemoryCustomerRepository

Validation
  CustomerValidator

Persistence
  CustomerDataLifecycle
  CsvCustomerStore
  CsvCodec
```

핵심 흐름:

```text
Spring 시작
  → CustomerDataLifecycle.@PostConstruct
  → CsvCustomerStore.load()
  → AddressBookService.add()
  → InMemoryCustomerRepository 적재

API 요청
  → AddressBookController
  → AddressBookService
  → InMemoryCustomerRepository

Spring 정상 종료
  → CustomerDataLifecycle.@PreDestroy
  → CsvCustomerStore.save()
  → 원본 백업
  → 메모리 snapshot을 임시 파일에 기록
  → 임시 파일로 원본 교체
```

## 5. 이번 세션에서 완료한 개선

### 5.1 종료 저장 생명주기 통합 검증

추가 파일:

```text
src/test/java/com/hyundai/test/address/persistence/CustomerDataLifecycleIntegrationTest.java
```

실제 Spring Context를 시작하고 닫는 흐름으로 다음을 검증한다.

- `@PostConstruct`가 테스트용 임시 CSV를 메모리에 적재
- Service를 통한 수정과 삭제
- `context.close()` 시 `@PreDestroy` 저장 실행
- 변경 전 원본과 같은 백업 생성
- 종료 직전 메모리 상태가 원본 CSV에 반영
- 저장된 CSV를 새 Store로 다시 로딩할 수 있음

프로젝트 루트의 `default_address.csv`는 테스트 데이터로 사용하거나 수정하지 않는다.

### 5.2 CSV 저장 실패 복구 보강

변경 파일:

```text
src/main/java/com/hyundai/test/address/persistence/CsvCustomerStore.java
src/test/java/com/hyundai/test/address/persistence/CsvCustomerStoreTest.java
```

현재 저장 실패 계약:

- 원본이 없거나 읽을 수 없으면 `CustomerDataFileException`
- 임시 파일 쓰기 실패 시 기존 원본 유지
- 원본 교체 실패 시 방금 생성한 백업으로 원본 복구 시도
- 최초 I/O 실패는 `CustomerDataFileException`의 cause로 유지
- 복구까지 실패하면 복구 예외를 최초 교체 예외의 suppressed exception으로 보존
- 성공·실패 여부와 관계없이 임시 파일 삭제 시도

테스트에서 Windows 파일 권한에 의존하지 않도록 `writeSnapshot`과 `replaceSource`를 package-private 메서드로 두고 실패를 주입한다.

### 5.3 검증 규칙 단일화

변경 파일:

```text
src/main/java/com/hyundai/test/address/validation/CustomerValidator.java
src/main/java/com/hyundai/test/address/controller/dto/CustomerRequest.java
```

전화번호·이메일 정규식과 오류 메시지는 `CustomerValidator`의 public 상수로 공유한다.

- DTO: 필수값과 HTTP 입력 경계 검증
- `CustomerValidator`: CSV와 API가 공통으로 사용하는 최종 업무 검증 및 정규화

전화번호는 저장 전 표준 하이픈 형식으로 정규화한다. 이메일은 소문자로 강제 변환하지 않으며 대소문자를 구분한다.

### 5.4 검색 인자 객체화

추가 파일:

```text
src/main/java/com/hyundai/test/address/domain/CustomerSearchRequest.java
```

Controller의 HTTP 쿼리 파라미터는 기존과 동일하다. Controller에서 Service로 전달할 때만 여섯 개 문자열을 `CustomerSearchRequest`로 묶었다.

```java
service.search(new CustomerSearchRequest(
        phoneNumber, email, address, name, sortBy, direction
));
```

외부 API 변경 없이 내부 인자 순서 실수와 확장 비용을 줄이는 목적이다.

### 5.5 Gradle Java 17 Toolchain 적용

`build.gradle`의 기존 `sourceCompatibility`를 Toolchain으로 교체했다.

```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
```

Gradle 9에서 제거 예정인 암묵적 테스트 프레임워크 로딩 경고를 없애기 위해 다음 의존성도 명시했다.

```groovy
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```

### 5.6 CSV 경계값 보강

추가 파일:

```text
src/test/java/com/hyundai/test/address/persistence/CsvCodecTest.java
```

검증 항목:

- UTF-8 BOM
- 필드 내부 쉼표
- 이중 따옴표 이스케이프
- 필드 내부 개행
- CRLF와 LF
- 빈 마지막 필드
- 닫히지 않은 따옴표
- 열 개수 부족·초과
- 100,000자 필드 왕복

테스트에서 발견된 BOM 호환성 문제를 수정했다. BOM은 CSV 파일 시작의 헤더 첫 필드에서만 제거하며 일반 데이터 필드의 문자는 바꾸지 않는다.

### 5.7 동시성 테스트 보강

변경 파일:

```text
src/test/java/com/hyundai/test/address/repository/InMemoryCustomerRepositoryTest.java
```

추가 시나리오:

- 동일 고객에 대한 동시 수정
- 수정과 삭제 경쟁
- snapshot 획득과 수정의 동시 실행

테스트는 특정 스레드의 승리 순서를 가정하지 않고 최종 불변식을 검증한다.

- 전화번호 중복 없음
- 이메일 중복 없음
- 이메일 조회 결과가 실제 고객과 일치
- 전화번호 조회 결과가 실제 고객과 일치
- 삭제되거나 교체된 이전 이메일 인덱스가 남지 않음

## 6. 자동 테스트 및 QA 결과

최종 검증일:

```text
2026-06-20
```

환경:

```text
Windows
OpenJDK 17.0.2
Gradle 8.4
Spring Boot 3.0.2
```

실행 명령:

```powershell
$env:JAVA_HOME='C:\Users\김효빈\.jdks\openjdk-17.0.2'
.\gradlew.bat clean test bootJar --warning-mode all --no-daemon
```

결과:

```text
BUILD SUCCESSFUL
테스트 33개
실패 0개
스킵 0개
bootJar 성공
Gradle 제거 예정 API 경고 없음
```

생성 JAR:

```text
build/libs/be-address-0.0.1-SNAPSHOT.jar
```

집중 재검증:

- 종료 저장 및 생명주기 테스트 재실행 성공
- CSV·동시성 테스트를 `--rerun-tasks`로 강제 재실행하여 13개 성공
- `git diff --check` 통과

### 실행 JAR API QA

`default_address.csv`를 직접 변경하지 않고 SHA-256이 같은 `build/qa/qa-address.csv` 복사본을 사용했다.

검증 결과:

- 기동 시 정상 데이터 5건 적재
- 잘못된 CSV 행 2건 건너뜀
- 전화번호 조회 200 응답
- 고객 전체 수정 200 응답
- 수정된 이메일 조회 200 응답
- 전화번호가 표준 하이픈 형식으로 정규화됨
- 프로젝트 루트 `default_address.csv` 해시 불변

Windows PTY에서 Java 프로세스에 `Ctrl+C`가 전달되지 않아 수동 JAR 실행은 PID와 명령줄을 확인한 뒤 강제 종료했다. 따라서 이 수동 실행은 종료 저장 합격 근거로 사용하지 않았다. 정상 종료 저장 계약은 `CustomerDataLifecycleIntegrationTest` 결과만 근거로 삼는다.

## 7. 실행 환경 주의사항

시스템 `JAVA_HOME`은 작업 당시 존재하지 않는 다음 경로를 가리켰다.

```text
C:\Program Files\Java\jdk-14.0.2
```

이 상태에서는 Gradle Wrapper가 시작되지 않는다. 시스템 환경 변수를 영구 변경하지 않고 명령 범위에서 다음처럼 Java 17 경로를 지정했다.

```powershell
$env:JAVA_HOME='C:\Users\김효빈\.jdks\openjdk-17.0.2'
```

과거 문서에 있던 한글 경로의 Gradle/JUnit 문제는 이번 환경에서 재현되지 않았다. 현재 실제 한글 경로에서 전체 테스트와 패키징이 성공했다.

샌드박스 내부 Gradle 캐시와 승인된 실제 사용자 환경의 Gradle 캐시가 다를 수 있다. Wrapper 배포본 또는 의존성 다운로드가 네트워크 제한으로 실패하면 허용된 실행 환경에서 다시 실행해야 한다.

## 8. 변경하지 않은 계약과 보류 항목

다음 항목은 의도적으로 구현하지 않았다.

### 8.1 실행 중 주기적 체크포인트

기존 요구사항은 정상 종료 시 백업과 최종 저장이다. 실행 중 원본 CSV를 주기적으로 교체하면 백업의 복구 시점 의미와 저장 정책이 달라진다.

도입 전에 최소한 다음 정책을 결정해야 한다.

- 체크포인트 파일 위치와 이름
- 원본과 체크포인트 중 복구 우선순위
- 체크포인트 손상 처리
- 실패 재시도
- 보존 개수와 삭제 기준
- 저장 중 추가 변경 발생 시 dirty 상태 처리

### 8.2 페이지네이션

현재 `GET /api/customers`는 전체 결과를 JSON 배열로 반환한다. 페이지네이션은 HTTP API 계약 변경이므로 구현하지 않았다.

진행 전 결정할 내용:

- 예상 최대 고객 수
- 기본 페이지 크기와 최대 크기
- 기존 무페이지 요청의 호환성
- 안정적인 보조 정렬 기준

### 8.3 백업 파일명 충돌 정책

현재 형식:

```text
default_address_yyyyMMddHHmmss.bak.csv
```

같은 초에 여러 번 저장하면 기존 백업을 교체할 수 있다. 다음 중 하나를 사용자와 합의해야 한다.

- 같은 초 중복 저장을 허용하지 않음
- `_1`, `_2` 접미사 추가
- 밀리초를 파일명에 추가

기존 요구사항의 파일명 계약과 관련되므로 임의 변경하지 않았다.

### 8.4 비정상 종료 저장

`@PreDestroy`는 정상적인 Spring Context 종료를 검증한다. 프로세스 강제 종료, 운영체제 장애, `kill -9`와 같은 상황의 저장을 보장하지 않는다. 문서에서도 “강제 종료 저장 완전 보장”이라고 표현하면 안 된다.

## 9. 다음 세션 권장 시작 순서

```text
1. docs/session-handoff.md 읽기
2. git status --short --branch 확인
3. docs/improvement-plan.md의 untracked 상태와 사용자 소유 여부 확인
4. JAVA_HOME을 실제 Java 17 경로로 설정
5. clean test와 bootJar로 기준선 재검증
6. 진행할 보류 항목의 계약을 사용자와 합의
7. 테스트를 먼저 추가하거나 변경 계약을 테스트로 고정
8. 최소 구현
9. 집중 테스트
10. 전체 clean test + bootJar
11. 임시 CSV 기반 QA
12. 문서 갱신
13. 기능 단위 커밋
```

기준선 재검증 명령:

```powershell
$env:JAVA_HOME='C:\Users\김효빈\.jdks\openjdk-17.0.2'
.\gradlew.bat clean test bootJar --warning-mode all --no-daemon
```

## 10. 작업 시 지켜야 할 사항

- 프로젝트 루트의 `default_address.csv`를 테스트 데이터로 직접 수정하지 않는다.
- 테스트마다 `@TempDir` 또는 별도 QA 복사본을 사용한다.
- 기존 사용자 변경과 untracked 파일을 덮어쓰지 않는다.
- API 또는 저장 정책을 변경하는 항목은 먼저 사용자 승인을 받는다.
- 테스트 → QA → 재검증 순서를 유지한다.
- 검증이 끝난 독립 작업 단위로 커밋한다.
- 강제 종료 저장을 완전히 보장한다고 표현하지 않는다.
- 수동 QA 후 서버 프로세스와 파일 핸들이 남아 있지 않은지 확인한다.

## 11. 관련 문서 역할

| 문서 | 역할 |
|---|---|
| `Readme.md` | 사용자 관점 실행 방법, API, 저장 정책 |
| `docs/api-specification.md` | HTTP API 상세 계약 |
| `docs/requirements.md` | 원 요구사항 |
| `docs/constraints.md` | 구현 제약 |
| `docs/implementation-decisions.md` | 설계 판단과 알려진 제약 |
| `docs/test-and-qa-report.md` | 실행한 검증과 실제 결과 |
| `docs/improvement-plan.md` | 최초 개선 계획과 우선순위 |
| `docs/session-handoff.md` | 현재 진행 상태와 다음 세션 인수인계 |

## 12. 현재 완료 판단

이번 개선 범위는 다음 근거로 완료 상태다.

- 실제 Spring 정상 종료 저장 자동 검증 완료
- CSV 저장 실패 시 원본 보존과 복구 시도 검증 완료
- 기존 API 회귀 없음
- 테스트 33개 전체 통과
- 실행 JAR 패키징 성공
- CSV 경계값과 동시성 검증 보강
- README, QA 보고서, 설계 결정 문서 최신화
- 개선 사항을 4개 기능 단위 커밋으로 기록

남은 작업은 버그 수정의 연장이 아니라 페이지네이션, 체크포인트, 백업 충돌 정책처럼 별도 계약 결정이 필요한 후속 개선이다.
