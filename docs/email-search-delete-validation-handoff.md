# 이메일 조회·삭제 조건 검증 개선 인수인계

## 1. 문서 목적

이 문서는 다음 세션에서 이메일 조회·삭제 조건의 검증 불일치를 바로 수정할 수 있도록 현재 문제, 목표 계약, 변경 범위, 테스트 시나리오와 완료 조건을 정리한다.

이번 인수인계의 범위는 다음 두 API다.

- `GET /api/customers?email=...`
- `DELETE /api/customers?email=...`

고객 수정 API, CSV 형식, 저장 정책, 이메일 대소문자 정책과 응답 JSON 구조는 변경하지 않는다.

## 2. 작업 시작 전 확인사항

현재 브랜치는 다음과 같다.

```text
improve/persistence-checkpoint
```

문서 작성 시점의 working tree에는 기존 사용자 작업이 있다.

```text
 M docs/test-and-qa-report.md
 M src/test/java/com/hyundai/test/address/controller/AddressBookControllerTest.java
?? src/test/java/com/hyundai/test/address/controller/AddressBookApiIntegrationTest.java
```

새 세션에서는 위 변경을 임의로 삭제하거나 덮어쓰지 않는다. 구현 전에 반드시 다시 확인한다.

```powershell
git status --short --branch
git diff -- docs/test-and-qa-report.md
git diff -- src/test/java/com/hyundai/test/address/controller/AddressBookControllerTest.java
```

`AddressBookApiIntegrationTest.java`는 untracked 파일이므로 일반 `git diff`에 표시되지 않는다. 파일 내용을 별도로 확인해야 한다.

## 3. 현재 문제

고객 수정 요청의 이메일은 DTO와 서비스 검증을 통해 형식을 확인한다.

```java
@NotBlank
@Pattern(regexp = EMAIL_REGEX)
String email
```

`CustomerValidator.validateAndNormalize()`도 이메일의 필수값과 형식을 다시 검증한다.

반면 조회와 삭제 조건은 `AddressBookService.condition()`에서 앞뒤 공백만 제거한다.

```java
String normalizedEmail = hasText(email) ? email.trim() : null;
```

관련 위치:

- `src/main/java/com/hyundai/test/address/service/AddressBookService.java`
- `src/main/java/com/hyundai/test/address/validation/CustomerValidator.java`

이로 인해 같은 잘못된 이메일이 API별로 다르게 처리된다.

| 요청 | 현재 결과 | 문제 |
|---|---:|---|
| 수정 본문에 `invalid-email` | 400 | 정상 |
| `GET ?email=invalid-email` | 200, 빈 배열 | 형식 오류를 정상 검색으로 처리 |
| `DELETE ?email=invalid-email` | 404 | 형식 오류를 데이터 미존재로 처리 |
| `GET ?email=   ` | 200, 전체 목록 | 명시한 필터가 조용히 제거됨 |

잘못된 입력과 유효하지만 존재하지 않는 데이터가 구분되지 않는 것이 핵심 문제다.

## 4. 목표 API 계약

이메일 파라미터의 상태를 다음과 같이 구분한다.

| 입력 상태 | 조회 | 삭제 |
|---|---:|---:|
| 이메일 파라미터 미전달 | 기존 동작 유지 | 다른 조건이 정확히 하나라면 기존 동작 유지 |
| 유효한 이메일, 대상 존재 | 200 | 200 |
| 유효한 이메일, 대상 없음 | 200과 `[]` | 404 |
| 빈 문자열 또는 공백 이메일 | 400 | 400 |
| 이메일 형식 오류 | 400 | 400 |

오류 메시지는 기존 상수를 재사용한다.

```text
이메일은 아이디@도메인 형식이어야 합니다.
```

공백 이메일은 기존 필수값 메시지를 사용한다.

```text
이메일은(는) 필수입니다.
```

응답은 기존 `ErrorResponse` 형식을 유지한다. `InvalidCustomerException`은 이미 `GlobalExceptionHandler`에서 400으로 변환되므로 새로운 HTTP 예외 타입은 필요하지 않다.

## 5. 반드시 유지할 정책

### 5.1 파라미터 미전달과 공백 전달을 구분한다

- `email == null`: 이메일 조건을 전달하지 않은 상태
- `email != null && email.isBlank()`: 잘못된 이메일 조건을 전달한 상태

기존 `hasText(email)`만 사용하면 두 상태가 모두 `null` 조건으로 합쳐지므로 목표 계약을 구현할 수 없다.

### 5.2 이메일 대소문자 정책은 변경하지 않는다

현재 이메일 중복 검사와 검색은 대소문자를 구분한다. 이번 작업에서 소문자 변환이나 대소문자 무시 검색을 추가하지 않는다.

```text
User@example.com != user@example.com
```

이 정책을 변경하려면 기존 데이터, 중복 판단과 CSV 저장값까지 함께 검토해야 하므로 별도 작업으로 다룬다.

### 5.3 이메일 정규식은 강화하지 않는다

현재 계약은 공백과 `@` 위치를 확인하는 최소 형식이다.

```java
^[^\s@]+@[^\s@]+$
```

도메인의 점(`.`), 국제화 이메일 또는 RFC 전체 규칙을 이번 범위에서 추가하지 않는다. 등록·수정과 조회·삭제가 같은 규칙을 사용하는 것이 목표다.

### 5.4 삭제 조건 개수 정책을 유지한다

삭제는 전화번호, 이메일, 주소, 이름 중 정확히 하나만 허용한다. 이메일 검증을 추가하더라도 이 계약은 변경하지 않는다.

## 6. 권장 구현 방향

### 6.1 `CustomerValidator`에 이메일 검증·정규화 메서드 제공

이메일 검증을 DTO나 서비스에 다시 작성하지 말고 `CustomerValidator`에서 재사용한다.

권장 형태:

```java
public String normalizeEmail(String email) {
    String value = requireText(email, "이메일");
    if (!EMAIL_PATTERN.matcher(value).matches()) {
        throw new InvalidCustomerException(EMAIL_FORMAT_MESSAGE);
    }
    return value;
}
```

메서드 이름은 구현자가 조정할 수 있지만 다음 책임은 유지한다.

1. `null`, 빈 문자열과 공백 문자열 거부
2. 앞뒤 공백 제거
3. 기존 `EMAIL_PATTERN`으로 형식 검증
4. 대소문자는 원본 그대로 유지

`validateAndNormalize()`도 이 메서드를 사용하도록 정리하면 등록·수정·CSV·검색·삭제가 동일한 규칙을 공유한다.

예상 형태:

```java
String email = normalizeEmail(customer.email());
```

### 6.2 검색 조건 생성 시 “전달 여부”를 기준으로 검증

`AddressBookService.condition()`의 이메일 처리는 다음 의미가 되어야 한다.

```java
String normalizedEmail = email == null
        ? null
        : validator.normalizeEmail(email);
```

`hasText(email)`를 기준으로 분기하면 공백 파라미터가 필터 미전달로 처리되므로 사용하지 않는다.

이 변경은 조회와 삭제가 공통으로 호출하는 `condition()`에 적용되어 두 API의 동작을 동시에 일치시킨다.

### 6.3 삭제 조건 개수 검증 순서

현재 삭제는 `hasText()`로 조건 수를 먼저 계산한다. 공백 이메일만 전달하면 조건 수가 0이 되어 기존의 “삭제 조건은 정확히 하나” 오류가 발생한다.

목표 계약상 공백 이메일도 400이므로 상태 코드는 이미 맞지만, 이메일 필드 오류 메시지를 일관되게 제공하려면 다음 순서를 권장한다.

1. 파라미터 전달 개수를 `value != null` 기준으로 확인
2. 정확히 하나가 아니면 `InvalidSearchConditionException`
3. 선택된 조건을 `condition()`에서 필드별 검증

다만 Spring이 실제로 빈 쿼리 파라미터를 `null`이 아닌 빈 문자열로 바인딩하는지 MockMvc 테스트로 고정해야 한다.

주소와 이름의 공백 처리까지 함께 변경하면 범위가 넓어지므로 이번 작업에서는 이메일 계약에 필요한 최소 변경을 우선한다. 조건 개수 계산 방식을 바꿀 경우 기존 주소·이름 공백 동작에 회귀가 없는지 확인한다.

## 7. 예상 변경 파일

필수:

```text
src/main/java/com/hyundai/test/address/validation/CustomerValidator.java
src/main/java/com/hyundai/test/address/service/AddressBookService.java
src/test/java/com/hyundai/test/address/validation/CustomerValidatorTest.java
src/test/java/com/hyundai/test/address/service/AddressBookServiceTest.java
```

HTTP 계약 검증:

```text
src/test/java/com/hyundai/test/address/controller/AddressBookApiIntegrationTest.java
```

필요한 경우:

```text
src/test/java/com/hyundai/test/address/controller/AddressBookControllerTest.java
docs/api-specification.md
Readme.md
docs/test-and-qa-report.md
```

`AddressBookControllerTest`는 서비스를 mock으로 대체하므로 서비스의 실제 이메일 검증을 자동으로 검증하지 않는다. HTTP 상태까지 확인하려면 실제 `AddressBookService`와 `InMemoryCustomerRepository`가 연결된 `AddressBookApiIntegrationTest`가 더 적합하다.

## 8. 필수 테스트 시나리오

### 8.1 `CustomerValidatorTest`

- 정상 이메일의 앞뒤 공백을 제거한다.
- 형식이 잘못된 이메일을 거부한다.
- 빈 문자열 또는 공백 이메일을 거부한다.
- 이메일 대소문자를 변경하지 않는다.

### 8.2 `AddressBookServiceTest`

- 잘못된 이메일 조회 시 `InvalidCustomerException`
- 공백 이메일 조회 시 `InvalidCustomerException`
- 잘못된 이메일 삭제 시 `InvalidCustomerException`
- 유효하지만 존재하지 않는 이메일 조회 시 빈 목록
- 유효하지만 존재하지 않는 이메일 삭제 시 `CustomerNotFoundException`
- 이메일을 전달하지 않은 일반 조회는 기존처럼 동작

### 8.3 HTTP 통합 테스트

실제 서비스와 저장소를 연결한 MockMvc 통합 테스트로 다음을 검증한다.

```http
GET /api/customers?email=invalid-email
```

예상:

```text
400 Bad Request
message = "이메일은 아이디@도메인 형식이어야 합니다."
```

```http
GET /api/customers?email=%20%20%20
```

예상:

```text
400 Bad Request
message = "이메일은(는) 필수입니다."
```

```http
DELETE /api/customers?email=invalid-email
```

예상:

```text
400 Bad Request
```

```http
GET /api/customers?email=missing@example.com
```

예상:

```text
200 OK
[]
```

```http
DELETE /api/customers?email=missing@example.com
```

예상:

```text
404 Not Found
```

기존의 유효한 이메일 조회·삭제 성공 테스트도 계속 통과해야 한다.

## 9. 회귀 위험

### 9.1 CSV 적재

`CsvCustomerStore`는 `AddressBookService.add()`를 사용하므로 `validateAndNormalize()`가 새 이메일 메서드를 사용해도 기존 CSV 규칙과 메시지가 유지되어야 한다.

### 9.2 수정 API

수정 본문은 DTO 검증과 서비스 검증을 모두 통과한다. 공백 제거 후 검증 순서가 달라져 기존 정상 입력이나 오류 메시지가 바뀌지 않는지 확인한다.

### 9.3 삭제 조건 계산

조건 개수 계산을 `hasText()`에서 `null` 여부로 바꾸면 주소 또는 이름의 빈 문자열도 “전달된 조건”으로 계산될 수 있다. 이번 범위에서 그 동작을 의도하지 않았다면 이메일을 먼저 별도로 검증하고 기존 조건 개수 정책을 최대한 유지한다.

### 9.4 Mock 기반 Controller 테스트

Mock 서비스는 실제 검증을 수행하지 않는다. 잘못된 이메일 요청에 대해 mock이 빈 목록을 반환하도록 설정되어 있다면 여전히 200이 나올 수 있다. 이를 실제 동작의 근거로 사용하지 않는다.

## 10. 구현하지 않을 항목

- 이메일 소문자 강제 변환
- 대소문자 무시 중복 검사 및 검색
- 이메일 RFC 전체 검증
- 이메일 도메인의 점(`.`) 필수화
- 요청 DTO 전체 재설계
- 조회·삭제 API 경로 또는 응답 JSON 변경
- 주소와 이름 검색 규칙의 전면 변경
- CSV 파서 개선

## 11. 검증 명령

요구 환경은 Java 17이다.

```powershell
.\gradlew.bat clean test --no-daemon
```

현재 작업 경로의 한글 조합 문자 때문에 Windows Java classpath 문제가 발생할 수 있다. 이 경우 코드 실패로 단정하지 말고 ASCII 경로의 임시 복사본과 JDK 17에서 다시 검증한다. 임시 복사본에서 검증하더라도 원본 working tree의 코드를 수정하거나 덮어쓰지 않는다.

문서 작성 직전 현재 작업 트리를 ASCII 임시 경로와 JDK 17에서 검증한 결과는 다음과 같다.

```text
테스트 43개
실패 0개
스킵 0개
```

구현 후에는 테스트 수가 증가해야 하며, 전체 테스트 실패가 없어야 한다.

## 12. 완료 조건

다음을 모두 만족해야 작업 완료로 판단한다.

- 등록·수정·CSV·조회·삭제가 동일한 이메일 형식 규칙을 사용한다.
- 이메일 파라미터 미전달과 공백 전달을 구분한다.
- 잘못된 이메일 조회와 삭제가 모두 400을 반환한다.
- 유효하지만 존재하지 않는 이메일 조회는 200과 빈 배열을 반환한다.
- 유효하지만 존재하지 않는 이메일 삭제는 404를 반환한다.
- 이메일 대소문자 정책은 변경하지 않는다.
- 기존 유효한 조회·수정·삭제와 CSV 테스트가 모두 통과한다.
- 사용자 작업 중인 파일을 임의로 삭제하거나 덮어쓰지 않는다.
- API 문서와 QA 문서의 테스트 개수 및 계약 설명을 실제 결과에 맞게 갱신한다.
