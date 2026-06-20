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
- 테스트 33개, 실패 0개, 스킵 0개
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
