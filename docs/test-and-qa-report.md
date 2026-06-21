# 테스트 및 QA 결과

## 최종 자동 검증

실행일: 2026-06-20

환경: Windows, OpenJDK 17.0.2, Gradle 8.4, Spring Boot 3.0.2

경로: `C:\Users\김효빈\OneDrive\Desktop\KIRO실습\customer\customerProject`

```powershell
$env:JAVA_HOME='C:\Users\김효빈\.jdks\openjdk-17.0.2'
.\gradlew.bat clean test bootJar --warning-mode all --no-daemon
```

결과:

- `BUILD SUCCESSFUL`
- 테스트 34개, 실패 0개, 오류 0개, 스킵 0개
- `be-address-0.0.1-SNAPSHOT.jar` 생성 성공
- Gradle `--warning-mode all`에서 제거 예정 API 경고 없음

## 검증 범위

- 전화번호·이메일·필수값 검증과 정규화
- HTTP DTO와 업무 검증의 전화번호·이메일 규칙 공유
- 복수 조건 조회와 정렬, 수정, 삭제 오류 계약
- 전화번호 Map과 이메일 보조 인덱스의 등록·수정·삭제 일관성
- 동시 중복 등록, 동일 고객 동시 수정, 수정·삭제 경쟁, snapshot·수정 경쟁
- CSV 쉼표, 이중 따옴표, 필드 내부 개행, CRLF/LF, 빈 마지막 필드
- UTF-8 BOM, 닫히지 않은 따옴표, 열 개수 부족·초과, 100,000자 필드
- 임시 파일 쓰기 실패와 원본 교체 실패 시 원본 보존 및 cause 유지
- 저장 성공·실패 후 임시 파일 정리
- 실제 Spring Context의 `@PostConstruct` 로딩과 `@PreDestroy` 종료 저장
- 종료 시 백업 내용, 최종 CSV 반영, 저장 결과 재로딩
- MockMvc 조회·수정 성공과 잘못된 정렬·삭제 요청 오류
- record DTO Builder 생성과 Lombok 생성자 주입

## 실행 JAR API QA

프로젝트 원본과 SHA-256이 같은 `build/qa/qa-address.csv` 복사본으로 JAR를 포트 18080에서 실행했다.

- 기동 시 정상 고객 5건 적재, 잘못된 행 2건 건너뜀
- `GET /api/customers?phoneNumber=01000000000`: 200, 1건
- `PUT /api/customers/01000000000`: 200, `010-1234-5678`로 정규화
- `GET /api/customers?email=qa@hyundai.com`: 200, 수정 고객 1건
- 프로젝트 루트 `default_address.csv` SHA-256 불변 확인

현재 실행 도구의 Windows PTY가 Java 프로세스에 `Ctrl+C`를 전달하지 못해 이 수동 실행은 강제 종료했다. 따라서 수동 실행의 종료 저장 결과는 합격 근거로 사용하지 않았고, 정상 종료 저장은 독립 임시 디렉터리를 사용하는 `CustomerDataLifecycleIntegrationTest`로 검증했다.

## 실행 환경 메모

초기 시스템 `JAVA_HOME`은 존재하지 않는 `C:\Program Files\Java\jdk-14.0.2`를 가리켜 Wrapper 실행이 실패했다. 명령 범위에서 실제 OpenJDK 17 경로로 교정한 후 현재 한글 경로에서 전체 테스트와 패키징이 성공했다. 과거 한글 경로 문제는 이번 환경에서 재현되지 않았다.

## DTO Builder 및 생성자 주입 추가 검증

### 변경 검증

- Lombok `@Builder`를 적용한 record DTO의 컴파일과 생성 검증
- `CustomerRequest` record의 기존 JSON 역직렬화 검증
- `@RequiredArgsConstructor`가 적용된 Controller와 Service의 Spring Context 생성 검증
- `CustomerSearchRequest`의 `service.dto` 이동 후 조회 회귀 검증
- 여섯 개 검색 쿼리 파라미터의 `CustomerSearchRequest` 직접 바인딩 검증

### 자동 테스트

```text
./gradlew.bat clean test --warning-mode all --no-daemon
BUILD SUCCESSFUL
테스트 34개
실패 0개
오류 0개
스킵 0개
```

### 실행 JAR QA

프로젝트 원본 `default_address.csv` 대신 `build/qa-builder/qa-address.csv` 복사본을 사용했다.

- 이름 조회: 200, 전화번호 `010-0000-0000`
- 고객 수정: 200
- 수정 전 전화번호: `010-0000-0000`
- 수정 후 전화번호: `010-1234-5678`
- 수정 후 이메일: `builder@hyundai.com`
- 잘못된 정렬 필드: 400
- 여섯 개 검색 쿼리 파라미터 직접 바인딩: 200, 결과 1건
- 원본 `default_address.csv` SHA-256 불변

QA 프로세스는 확인 후 종료했으며 백그라운드 서버를 남기지 않았다.

## Controller 슬라이스 테스트 분리 및 재검증

실행일: 2026-06-21

기존 `AddressBookControllerTest`는 `@SpringBootTest`와 실제
`AddressBookService`, Repository를 함께 사용해 Controller 테스트라기보다 API
통합 테스트에 가까웠다. 다음과 같이 테스트 책임을 분리했다.

- `AddressBookControllerTest`
  - `@WebMvcTest(AddressBookController.class)` 사용
  - `AddressBookService`를 `@MockBean`으로 대체
  - 요청 파라미터와 `CustomerSearchRequest` 바인딩
  - 요청 JSON과 도메인 객체 변환
  - Bean Validation
  - 응답 DTO 직렬화
  - 400, 404, 409, 500 예외 응답 매핑
  - 긍정 6건, 부정 6건
- `AddressBookApiIntegrationTest`
  - `@SpringBootTest`와 `@AutoConfigureMockMvc` 사용
  - 실제 Service, Validator, Repository Bean 연결
  - 조회 → 수정 → 재조회 → 삭제 → 빈 결과 흐름 검증
- `CustomerDataLifecycleIntegrationTest`
  - 실제 Spring Context 시작·종료와 CSV 로딩·백업·저장을 계속 검증

현재 작업 경로에서는 Gradle 테스트 실행기가 새 테스트 클래스를 찾지 못하는
한글 경로 정규화 문제가 다시 발생했다. 소스 컴파일은 성공했으며 같은 소스를
영문 임시 경로로 복사해 다음 검증을 수행했다.

```text
clean test bootJar --warning-mode all --no-daemon
BUILD SUCCESSFUL
테스트 43개
실패 0개
오류 0개
스킵 0개
```

첫 전체 검증 후 새 영문 임시 복사본에서 `clean test`를 다시 수행해 동일한
43개 테스트가 모두 성공하는 것을 재확인했다.
## Email search/delete validation handoff follow-up
Executed on 2026-06-21.

Implemented:
- Added `CustomerValidator.normalizeEmail()` so add, update, CSV load, search, and delete can share the same email validation and trimming rule.
- Updated `AddressBookService.condition()` to validate `email` when the query parameter is present, while still treating an absent email parameter as `null`.
- Updated delete filter counting so `email=   ` is treated as a provided filter and fails with `400 Bad Request` instead of being ignored.
- Added unit and integration coverage for invalid email, blank email, missing-but-valid email, and case preservation.

Verification attempts:
- `.\gradlew.bat test --tests com.hyundai.test.address.validation.CustomerValidatorTest --tests com.hyundai.test.address.service.AddressBookServiceTest --tests com.hyundai.test.address.controller.AddressBookApiIntegrationTest --no-daemon`
- In the original workspace this failed before Gradle startup because the composed Hangul path prevented `gradle-wrapper.jar` from being resolved.
- Re-ran from an ASCII temp copy at `C:\Users\hyobi\AppData\Local\Temp\be-address-mvc-main-7-qa\be-address-mvc-main 7`.
- Gradle 8.4 download required network access and succeeded after approval.
- Test execution still could not complete because this machine only has JDK 21 installed while the project requires Java 17 toolchain.
- A QA-only retry in the temp copy with toolchain 21 failed during `:compileJava` with Lombok/JDK 21 incompatibility:
  `java.lang.NoSuchFieldError: Class com.sun.tools.javac.tree.JCTree$JCImport does not have member field 'com.sun.tools.javac.tree.JCTree qualid'`

QA conclusion:
- Code changes are in place and aligned with the handoff contract.
- Automated `unit test -> integration test -> QA -> reverification` could not be fully completed in this environment because a local JDK 17 installation is missing.
- Reverification should be rerun on a machine with Java 17 available, using the ASCII-path workaround described in `docs/email-search-delete-validation-handoff.md`.
