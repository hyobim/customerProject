# CSV 문법 검증 강화 인수인계

## 1. 문서 목적

이 문서는 다음 세션에서 CSV 파서의 문법 검증 문제를 독립적으로 수정할 수 있도록 현재 동작, 목표 계약, 권장 구현 구조, 필수 테스트와 완료 조건을 정리한다.

이번 작업의 핵심은 다음 두 가지다.

1. 잘못된 CSV 문법을 정상 데이터로 조용히 변환하지 않는다.
2. CSV 문법 오류와 고객 데이터 검증 오류의 처리 정책을 명확히 구분한다.

CSV 저장·백업 정책, 고객 필드 검증 규칙, API 계약과 메모리 저장소 구조는 변경하지 않는다.

## 2. 작업 시작 전 확인사항

현재 브랜치는 다음과 같다.

```text
improve/persistence-checkpoint
```

문서 최종 검증 시점의 working tree에는 기존 컨트롤러 테스트 변경과 인수인계 문서가 존재한다.

```text
 M src/test/java/com/hyundai/test/address/controller/AddressBookControllerTest.java
?? docs/csv-syntax-validation-handoff.md
?? docs/email-search-delete-validation-handoff.md
```

작업 도중 다른 세션에서 이메일 검증 관련 변경이 정리되어 working tree 상태가 갱신되었다. 새 세션에서는 위 변경을 삭제하거나 덮어쓰지 않고 CSV 작업 전에 실제 상태를 다시 확인한다.

```powershell
git status --short --branch
git diff -- src/main/java/com/hyundai/test/address/persistence
git diff -- src/test/java/com/hyundai/test/address/persistence
```

untracked 파일은 일반 `git diff`에 표시되지 않으므로 `git status`와 파일 내용을 함께 확인한다.

## 3. 현재 CSV 구조

주요 파일:

```text
src/main/java/com/hyundai/test/address/persistence/CsvCodec.java
src/main/java/com/hyundai/test/address/persistence/CsvCustomerStore.java
src/main/java/com/hyundai/test/address/exception/CustomerDataFileException.java
```

역할:

- `CsvCodec`: CSV 레코드 읽기와 쓰기
- `CsvCustomerStore`: 파일 열기, 헤더 검증, 행 적재, 오류 행 로그와 저장
- `CustomerDataFileException`: CSV 파일 또는 문법 오류 표현

현재 코덱은 스트리밍 방식이며 다음 정상 입력을 지원한다.

- 필드 내부 쉼표
- `""`로 이스케이프된 이중 따옴표
- 따옴표 필드 내부 개행
- LF와 CRLF
- 빈 마지막 필드
- 매우 긴 필드
- UTF-8 BOM이 있는 헤더

이 지원 범위는 유지해야 한다.

## 4. 현재 문제

### 4.1 닫는 따옴표 뒤의 일반 문자를 허용한다

다음 입력은 정상 CSV 문법이 아니다.

```csv
"서울"x,01012345678,user@example.com,홍길동
```

따옴표 필드가 닫힌 뒤에는 쉼표, 레코드 끝 또는 파일 끝만 올 수 있어야 한다. 현재 구현은 닫는 따옴표를 만난 뒤 `quoted = false`로만 변경하고 다음 문자를 일반 필드 문자로 처리한다.

결과적으로 위 주소가 다음 값으로 조용히 변형된다.

```text
서울x
```

관련 코드:

```java
if (character == '"') {
    int next = reader.read();
    if (next == '"') {
        field.append('"');
    } else {
        quoted = false;
        if (next != -1) {
            reader.unread(next);
        }
    }
}
```

문법 오류를 거부하지 않고 다른 데이터로 받아들이므로 데이터 무결성 문제다.

### 4.2 따옴표가 필드 중간에 나타나는 입력을 허용한다

다음 입력도 엄격한 CSV 문법에서는 잘못된 값이다.

```csv
서"울,01012345678,user@example.com,홍길동
```

현재 구현은 따옴표가 빈 필드의 첫 문자일 때만 quoted 상태로 진입하고, 필드 중간의 따옴표는 일반 문자로 추가한다.

```java
if (character == '"' && field.isEmpty()) {
    quoted = true;
} else {
    field.append(character);
}
```

이 역시 잘못된 CSV를 정상 데이터처럼 적재하게 만든다.

### 4.3 닫히지 않은 따옴표는 전체 로딩을 중단한다

현재 코덱은 파일 끝까지 따옴표가 닫히지 않으면 다음 예외를 발생시킨다.

```java
throw new CustomerDataFileException("닫히지 않은 CSV 따옴표가 있습니다.");
```

이 동작 자체는 안전한 방향이다. 따옴표 내부 개행을 지원하므로 현재 개행이 정상 필드 내부 개행인지 손상된 행의 끝인지 확실히 판단할 수 없기 때문이다.

문제는 README의 “잘못된 데이터 행은 건너뛴다”는 문장이 CSV 문법 오류까지 건너뛰는 것으로 읽힐 수 있다는 점이다. 구현과 문서에서 오류 범주를 구분해야 한다.

## 5. 목표 오류 정책

권장 계약은 다음과 같다.

| 오류 종류 | 예시 | 처리 |
|---|---|---|
| 파일 오류 | 파일 없음, 읽기 불가 | 기동 실패 |
| 헤더 오류 | 순서·필드명 불일치 | 기동 실패 |
| CSV 문법 오류 | 닫히지 않은 따옴표, 닫는 따옴표 뒤 문자, 필드 중간 따옴표 | 기동 실패 |
| 열 개수 오류 | 3열 또는 5열 | 해당 레코드 Warning 후 건너뜀 |
| 고객 데이터 오류 | 전화번호·이메일 형식 오류, 필수값 누락 | 해당 레코드 Warning 후 건너뜀 |
| 중복 오류 | 전화번호 또는 이메일 중복 | 해당 레코드 Warning 후 건너뜀 |

즉, **레코드의 구조 자체를 신뢰할 수 없는 문법 오류는 fail-fast**, 구조는 읽혔지만 업무 데이터가 유효하지 않은 경우만 행 단위로 격리한다.

### 문법 오류를 행 단위로 건너뛰지 않는 이유

CSV는 따옴표 안의 개행을 허용한다.

```csv
"서울시
광진구",01012345678,user@example.com,홍길동
```

닫히지 않은 따옴표가 나타났을 때 단순히 다음 물리적 줄로 이동하면 정상적인 멀티라인 레코드를 손상된 행으로 오인할 수 있다. 반대로 다음 따옴표까지 계속 읽으면 여러 레코드를 하나로 합칠 수 있다.

명확한 복구 경계가 없으므로 문법 오류 후 임의 복구를 시도하지 않는 것이 안전하다.

## 6. 권장 구현 방향

### 6.1 boolean 대신 명시적인 파서 상태 사용

현재의 `quoted` boolean만으로는 다음 상태를 구분하기 어렵다.

- 새 필드 시작
- 따옴표 없는 필드 읽는 중
- 따옴표 필드 읽는 중
- 따옴표 필드가 닫힌 직후

예를 들어 내부 enum을 사용할 수 있다.

```java
private enum State {
    FIELD_START,
    UNQUOTED,
    QUOTED,
    AFTER_QUOTE
}
```

상태별 허용 입력은 다음과 같이 제한한다.

| 상태 | 허용 입력 |
|---|---|
| `FIELD_START` | `"`로 quoted 시작, `,`로 빈 필드 종료, 개행으로 레코드 종료, 일반 문자로 unquoted 시작 |
| `UNQUOTED` | 일반 문자, `,`, 레코드 끝 |
| `QUOTED` | 모든 문자, `""` 이스케이프, 닫는 `"` |
| `AFTER_QUOTE` | `"` 이스케이프 계속, `,`, 레코드 끝, 파일 끝 |

중요한 금지 규칙:

- `UNQUOTED` 상태의 `"`는 문법 오류
- `AFTER_QUOTE` 상태의 일반 문자는 문법 오류

구체적인 코드 형태는 구현자가 선택할 수 있지만 상태와 허용 전이를 코드에서 읽을 수 있어야 한다.

### 6.2 CRLF를 정상 레코드 끝으로 유지

현재 구현은 unquoted 필드의 끝에 붙은 `\r`을 제거하는 방식으로 CRLF를 지원한다. 상태 기반 구현에서도 다음 입력이 모두 동일하게 처리되어야 한다.

```text
a,b\n
a,b\r\n
```

특히 `AFTER_QUOTE` 상태에서 CRLF가 들어오는 경우를 별도로 테스트한다.

```csv
"a","b"\r\n
```

`\r`을 레코드 데이터로 추가한 뒤 제거할 수도 있고, `\r\n`을 하나의 레코드 구분자로 직접 처리할 수도 있다. 어느 쪽이든 quoted 필드 내부의 `\r`과 `\n`은 보존해야 한다.

### 6.3 문법 예외에 진단 정보를 포함

최소한 어떤 종류의 문법 오류인지 메시지로 구분한다.

예:

```text
CSV 따옴표 필드가 닫히지 않았습니다.
CSV 닫는 따옴표 뒤에 허용되지 않은 문자가 있습니다.
CSV 따옴표는 필드의 첫 문자에만 사용할 수 있습니다.
```

가능하면 레코드 번호도 포함한다. 다만 `CsvCodec`에 파일 경로나 업무 행 번호 책임까지 과하게 넣지 않는다.

선택지는 다음과 같다.

1. `CsvCodec`이 읽은 물리적 줄 또는 레코드 위치를 추적한다.
2. 별도의 문법 예외에 위치 정보를 담고 `CsvCustomerStore`가 파일 문맥을 추가한다.
3. 이번 범위에서는 오류 종류만 명확히 하고 위치 정보는 후속 개선으로 남긴다.

최소 완료 조건은 오류 종류가 명확한 것이다.

### 6.4 `CustomerDataFileException` 전파 유지

CSV 문법 오류는 `CustomerDataFileException`으로 전파하여 Spring의 `@PostConstruct` 로딩을 실패시킨다.

`CsvCustomerStore.load()`에서 문법 예외를 다음처럼 행 단위 오류와 함께 잡아 건너뛰면 안 된다.

```java
catch (InvalidCustomerException
       | DuplicateCustomerException
       | CustomerDataFileException exception) {
    // 금지: 문법 오류까지 skip
}
```

현재처럼 다음 예외만 레코드 단위로 건너뛴다.

```java
InvalidCustomerException
DuplicateCustomerException
```

### 6.5 외부 CSV 라이브러리 도입은 선택사항

검증된 라이브러리를 사용하면 CSV 문법 처리의 유지보수 위험을 줄일 수 있다. 그러나 현재 프로젝트는 작은 자체 스트리밍 코덱을 사용하고 외부 의존성을 최소화한다는 설계 결정을 문서화했다.

이번 작업에서는 상태 기반 파서를 보강하는 방향이 변경 범위가 가장 작다. 외부 라이브러리를 도입한다면 다음을 먼저 확인한다.

- 기존 BOM 처리
- 필드 내부 개행
- 빈 마지막 필드
- 엄격 모드의 실제 문법 오류 동작
- 저장 결과의 호환성
- 추가 의존성 선택 근거

단순히 라이브러리를 추가하고 기존 경계 테스트를 삭제해서는 안 된다.

## 7. 부분 적재에 대한 판단

파일 중간에서 문법 오류가 발생하면 그 앞의 정상 고객은 이미 메모리 저장소에 추가되었을 수 있다.

Spring 기동 시에는 `@PostConstruct`가 예외로 실패하여 애플리케이션 컨텍스트가 정상 제공되지 않으므로, 부분 적재 상태가 API에 노출되지는 않는다. 따라서 이번 작업에서 전체 파일을 먼저 메모리에 복제하는 트랜잭션형 로딩까지 구현할 필요는 없다.

다만 같은 `AddressBookService` 인스턴스로 `store.load()` 실패 후 재시도하는 테스트나 도구를 만들면 앞서 적재된 데이터와 중복될 수 있다. 이 문제는 별도 “로딩 원자성” 과제로 다루며 이번 범위에 포함하지 않는다.

## 8. 예상 변경 파일

필수:

```text
src/main/java/com/hyundai/test/address/persistence/CsvCodec.java
src/test/java/com/hyundai/test/address/persistence/CsvCodecTest.java
```

정책·통합 검증에 따라 필요:

```text
src/main/java/com/hyundai/test/address/persistence/CsvCustomerStore.java
src/test/java/com/hyundai/test/address/persistence/CsvCustomerStoreTest.java
Readme.md
docs/implementation-decisions.md
docs/test-and-qa-report.md
```

다음 파일은 이번 작업의 직접 변경 대상이 아니다.

```text
src/main/java/com/hyundai/test/address/service/AddressBookService.java
src/main/java/com/hyundai/test/address/validation/CustomerValidator.java
src/main/java/com/hyundai/test/address/repository/InMemoryCustomerRepository.java
```

## 9. 필수 테스트 시나리오

### 9.1 기존 정상 동작 회귀 테스트

다음 기존 테스트는 모두 유지한다.

- 쉼표가 포함된 quoted 필드
- `""`로 이스케이프된 따옴표
- quoted 필드 내부 LF 개행
- CRLF와 LF 레코드 경계
- 빈 마지막 필드
- 파일 끝에 개행이 없는 레코드
- 100,000자 필드 왕복
- UTF-8 BOM 헤더
- 정상 데이터 저장 후 다시 로딩

### 9.2 새 문법 오류 테스트

다음 입력은 `CustomerDataFileException`을 발생시켜야 한다.

닫는 따옴표 뒤 일반 문자:

```csv
"서울"x,01012345678,user@example.com,홍길동
```

unquoted 필드 중간 따옴표:

```csv
서"울,01012345678,user@example.com,홍길동
```

quoted 필드 뒤 공백:

```csv
"서울" ,01012345678,user@example.com,홍길동
```

엄격한 정책에서는 닫는 따옴표 뒤 공백도 일반 문자이므로 오류로 처리한다. 공백 허용 정책을 선택하려면 문서와 테스트를 명시적으로 바꿔야 하며, 암묵적으로 허용하지 않는다.

닫히지 않은 quoted 필드:

```csv
"서울,01012345678,user@example.com,홍길동
```

quoted 필드가 여러 물리적 줄을 삼킨 뒤 EOF에 도달하는 경우도 실패해야 한다.

### 9.3 정상 경계 테스트

다음은 오류가 아니어야 한다.

```csv
"서울",01012345678,user@example.com,홍길동
```

```csv
"서""울",01012345678,user@example.com,홍길동
```

```csv
"서울
광진구",01012345678,user@example.com,홍길동
```

```csv
"서울","01012345678","user@example.com","홍길동"\r\n
```

quoted 필드가 파일 끝에서 바로 닫히고 마지막 개행이 없는 경우도 정상이어야 한다.

### 9.4 Store 수준 정책 테스트

- 열 개수 오류는 계속 Warning 후 건너뛴다.
- 고객 데이터 형식 오류는 계속 Warning 후 건너뛴다.
- 중복 데이터는 계속 Warning 후 건너뛴다.
- CSV 문법 오류는 `LoadResult`를 반환하지 않고 예외로 종료한다.
- 문법 오류 뒤에 정상 행이 있어도 로딩을 계속하지 않는다.

로그 자체를 과도하게 결합해 검증하기보다는 반환값, 예외와 최종 snapshot을 우선 검증한다.

## 10. 구현하지 않을 항목

- CSV 문법 오류 후 다음 물리적 줄부터 강제 복구
- 전체 파일 사전 적재를 통한 로딩 원자성
- CSV 파일 인코딩 자동 감지
- 구분자 자동 감지
- 탭 구분 파일 지원
- 주석 행 지원
- 느슨한 따옴표 또는 닫는 따옴표 뒤 공백 허용
- CSV 저장·백업 이름 정책 변경
- 고객 필드의 유효성 규칙 변경
- 이메일 조회·삭제 검증 작업

## 11. 문서 갱신 기준

README의 다음 문장을 더 명확히 해야 한다.

현재:

```text
잘못된 데이터 행은 Warning 로그 후 건너뛰고 다음 행을 계속 처리합니다.
```

권장:

```text
열 개수, 고객 필드 형식 또는 중복이 잘못된 레코드는 Warning 로그 후 건너뜁니다.
CSV 따옴표 등 문법이 손상되어 레코드 경계를 신뢰할 수 없으면 기동에 실패합니다.
```

`docs/implementation-decisions.md`에도 자체 코덱이 엄격한 따옴표 상태 검증을 수행하며, 문법 오류는 fail-fast한다는 결정을 추가한다.

테스트 개수가 변경되면 `docs/test-and-qa-report.md`의 실행 결과를 실제 값으로 갱신한다. 기존 숫자를 추정해서 작성하지 않는다.

## 12. 검증 명령

요구 환경은 Java 17이다.

```powershell
.\gradlew.bat clean test --no-daemon
```

현재 작업 경로의 한글 조합 문자 때문에 Windows Java classpath 문제가 발생할 수 있다. 이 경우 코드 실패로 단정하지 말고 ASCII 임시 경로의 복사본과 JDK 17에서 다시 검증한다.

임시 경로에서 검증할 때도 다음 원칙을 지킨다.

- 원본 working tree를 수정하거나 덮어쓰지 않는다.
- `.git`, `build`, `.gradle`은 복사 대상에서 제외할 수 있다.
- 최종 변경은 원본 프로젝트에만 적용한다.
- 테스트 결과와 실행 환경을 QA 문서에 정확히 기록한다.

## 13. 완료 조건

다음을 모두 만족해야 완료로 판단한다.

- 닫는 따옴표 뒤 일반 문자를 정상 데이터로 받아들이지 않는다.
- unquoted 필드 중간의 따옴표를 정상 문자로 받아들이지 않는다.
- 닫히지 않은 따옴표가 명확한 `CustomerDataFileException`을 발생시킨다.
- 문법 오류는 애플리케이션 로딩 실패로 전파된다.
- 열 개수·고객 데이터·중복 오류의 행 단위 skip 정책은 유지된다.
- quoted 필드 내부 쉼표, 이스케이프 따옴표와 개행 지원이 유지된다.
- LF, CRLF, 빈 마지막 필드와 마지막 개행 없는 파일을 계속 지원한다.
- 저장된 CSV를 다시 정상 로딩할 수 있다.
- 전체 테스트가 Java 17에서 통과한다.
- README와 설계·QA 문서가 실제 정책 및 테스트 결과와 일치한다.
- 기존 이메일 검증 작업과 사용자 변경을 삭제하거나 덮어쓰지 않는다.
