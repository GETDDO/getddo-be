# 관리자 감사 조회 구현·연동 안내

공용 계약 원본은 [spec AU01/AU02](https://github.com/Uplus-GETDDO/getddo-spec/blob/76d8364/05-api/audit.md)다. 이 문서는 GD-207의 BE 구현·검증과 FE 연결을 설명한다.

## 구현 흐름

`AdminAuditLogController → AuditLogQueryService → AuditLogQueryRepositoryImpl`

- 기존 CurrentUser의 DB 대조를 재사용하고 서비스에서 활성 관리자 권한을 검사한다. 선택 멤버십도 대조하도록 membershipRequired=false를 사용한다.
- 서비스는 읽기 전용 트랜잭션에서 목록 건수·행을 읽는다. JPA Criteria API가 같은 검색 조건을 count/select에 적용하며 기존 AuditLogEntity와 새 조회 MapStruct를 사용한다. 기존 추가 저장 Repository·Mapper·MANDATORY Service는 바꾸지 않는다.
- core의 AuditLogEntry.publicView와 AuditLogDataProjection이 작업/대상·필드·자료형을 확인한다. 새 결과를 만들어 원본 JSON을 변경하지 않으며 DTO에는 공개한 정보만 옮긴다. 알려지지 않은 정보가 null이어도 원본이 삭제됐다는 뜻은 아니다.
- DB 시각은 마이크로초 정밀도이므로 검색 양끝을 마이크로초로 올려 나노초 요청의 [from,to) 의미를 보존한다. 단순 절삭하면 시작·종료 경계에서 다른 행을 포함할 수 있어 실제 MySQL 경계 테스트로 보호한다.
- 새 의존성·마이그레이션·기존 기록·인증·FE 변경은 없다. 이 조회는 보존된 감사 행을 읽으며 롤백된 실패 시도의 전체 이력은 제공하지 않는다.

## FE 담당자 연결

기존 감사 목록과 상세 서랍을 재사용한다. 호출 경로는 `/api/v1/admin/audit-logs`와 상세 ID 경로다. 예시 baseURL=/api에서는 query 경로에 /v1이 필요하며, 실제 배포 설정을 확인해 접두사를 맞춘다.

FE의 과거 작업/대상 선택지를 실제 BE 기록 코드와 연결한다. 현재 UI는 알려지지 않은 코드도 원문으로 표시하는 fallback이 있다. KST 날짜 입력을 UTC 경계로 변환하는 기존 [from,to) 방식은 유지할 수 있다. 정확한 필드·오류·공개 허용 목록은 공용 원본을 따른다. 실제 FE 수정·브라우저·배포 연결은 별도 확인 대상이다.

## 검증

조건·권한·허용 데이터·null·원본 보존 단위 검증, 실제 MySQL/MVC의 AND 검색·페이지·정밀 시각 경계·공통 문맥 오류·비공개 JSON 제외·기존 기록 서비스 연결을 검증한다. 실행 결과는 PR과 일일 공용 워크로그에 실제 완료 후 기록한다.

변경 범위를 먼저 확인한 뒤 전체 기존 기능·빌드를 확인하는 순서다. 전체 검증은 앞의 테스트도 포함하며 변경 없는 작업은 Gradle이 up-to-date로 재사용할 수 있다.

```bash
./gradlew :core:test --tests '*AuditLogQuery*' --tests '*AuditLogEntryTest' \
  :api:integrationTest --tests '*AdminAuditLogIntegrationTest' \
  --init-script gradle/test-limits.init.gradle --max-workers=1 --no-parallel
./gradlew test build --init-script gradle/test-limits.init.gradle --max-workers=1 --no-parallel
```
