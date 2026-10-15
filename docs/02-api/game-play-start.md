# 색깔 게임 시작 구현 — GD-156

공용 경로·필드·계정 자격은 [색깔 게임 G03 계약](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/05-api/game-color-word-draft.md#2-시작--g03)을 원본으로 사용한다. 정책을 이 문서에서 다시 확정하지 않는다. 아래는 백엔드 내부 처리·검증과 연동 준비 상태다.

## 내부 처리

1. `GamePlayController`는 기존 `@CurrentUser(membershipRequired=false)`로 DB와 대조한 사용자와 게임 UUID를 `GamePlayStartService`에 전달한다. 공통 사용자 문맥·헤더 오류는 변경하지 않았다.
2. Service는 활성 계정과 시작 가능한 `COLOR_WORD`/`color-word-v1` 설정을 검사한다. 사용자 선택에 따라 활성 ADMIN을 별도로 차단하지 않는다.
3. `ColorWordRules`가 DB 규칙 JSON의 채택된 공개 필드를 엄격히 복원한다. 누락·형식·버전 불일치는 기본값으로 바꾸지 않는다. 지원되지 않는 게임/버전과 손상된 지원 버전 설정은 구분한다.
4. `GamePlayCredentials`는 `SecureRandom`으로 32바이트 토큰과 0이 아닌 unsigned 32-bit seed를 각각 발급한다. 토큰 원문의 SHA-256을 기존 `GamePlay.start`와 `GamePlayRepository.insert`에 전달한다. seed는 공개 재현 값이며 인증 수단이 아니다.
5. `GamePlayCredentials.matches`는 저장된 플레이의 ID·시각·사용자·게임과 토큰 해시를 확인하고 고정 시간 해시 비교를 사용한다. 다른 사용자·게임·플레이·과거/미저장 플레이의 재사용은 거절한다. 공개 오류·만료 적용과 결과 HTTP 진입점 연결은 GD-159 후속이다.
6. 기존 공통 Entity의 UUID v7·`createdAt`을 사용한다. 시각은 JPA Auditing의 공통 Clock 기준 UTC·마이크로초 값이며 서비스에서 별도 현재 시각을 만들어 응답과 저장을 어긋나게 하지 않는다.
7. Service 트랜잭션 안에서 저장하고, 저장 결과와 이번 토큰 원문만 응답 변환 모델에 묶는다. `record` API DTO가 공개 필드만 선택해 201 봉투로 반환한다. core 모델이나 Entity를 직접 JSON 응답으로 사용하지 않는다.

토큰 원문은 DB/로그에 기록하지 않고 Controller·Service도 모델 전체를 로그에 쓰지 않는다. 규칙 JSON의 내부 필드는 공개 DTO에 포함하지 않는다. 시작은 미완료 플레이만 저장하며 점수·통계·보상 청구·응모권 지급을 호출하지 않는다. DB 컬럼·Mapper·기존 Flyway 파일은 변경하지 않았다.

## 실제 적용 항목과 검증 상태

| 기준 | 구현·검증 증거 | 상태 |
| --- | --- | --- |
| 확정 G03 경로·본문 없음·201·6개 공개 필드 | Controller/record DTO, GamePlayControllerTest | 관련 테스트 사용자 성공 확인 |
| 활성 USER/ADMIN 허용, 비활성 거절 | Service, ServiceTest의 역할 매개변수 테스트, API 통합 테스트 | 관련 테스트 사용자 성공 확인 |
| 기존 사용자 문맥 재사용·헤더와 DB 대조 | CurrentUser 사용, ControllerTest·API 통합 역할 불일치 테스트 | 공통 코드 유지, 신규 테스트 미실행 |
| 저장 규칙·ruleVersion·generatorVersion·seed 보존 | ColorWordRules, GamePlay.start/Repository 재사용, API 저장·변경 후 스냅샷 테스트 | 관련 테스트 사용자 성공 확인 |
| 토큰 소유자·게임·플레이 연결 검증 | Credentials.matches, core 회귀·API 저장값의 교차 사용자/게임/플레이 검사 | 관련 테스트 사용자 성공 확인. 결과 API 연결 후속 |
| 토큰 원문/해시 분리와 seed 범위 | CredentialsTest 고정 해시·0 재발급·최소/최대 seed, API 저장 일치 테스트 | 관련 테스트 사용자 성공 확인 |
| 시작만으로 결과·통계·보상 없음 | Service 저장 입력 단언, API 실제 테이블 건수 단언 | 관련 테스트 사용자 성공 확인 |
| 시작 재요청은 새 플레이 | API distinct ID/token 테스트 | 관련 테스트 사용자 성공 확인 |
| UUID v7·UTC 마이크로초와 공통 시계 | 고정 Clock API 통합 테스트와 저장·응답 시각 단언 | 기존 기반 재사용, 관련 테스트 사용자 성공 확인 |
| 외부 입력으로 사용자·seed·규칙·점수 덮어쓰기 불가 | 본문 값을 사용하지 않는 Controller/Service 및 API 테스트 | 관련 테스트 사용자 성공 확인 |
| 예상 업무 오류/설정·저장 장애 분리 | GameErrorCode·BusinessException·기존 GlobalExceptionHandler, Service/Controller 오류 테스트 | 관련 테스트 사용자 성공 확인 |
| FE 시작 버튼·실제 문제 생성 연결 | 공용 색깔 계약 원본 참조 | FE 수정·실제 연동 미확인 |
| 운영 한도·토큰 수명·결과 API 및 지급 연결 | 공용 계약의 후속 확인 | 미결정/후속 범위, 구현 완료 아님 |

## 실행

사용자가 기존 게임 작업 폴더에서 GD-156 관련 테스트 성공을 확인했다. Codex는 로컬 테스트를 재실행하지 않았다. 전체 test/build 성공은 별도 확인 전이며 최신 dev로 분리한 PR은 GitHub CI로 검증한다.

```bash
./gradlew :core:test --tests 'com.getddo.core.game.domain.*' --tests 'com.getddo.core.game.service.GamePlayStartServiceTest'
./gradlew :api:test --tests 'com.getddo.api.game.controller.GamePlayControllerTest'
./gradlew :api:integrationTest --tests 'com.getddo.api.game.GamePlayApiIntegrationTest'
./gradlew test build
```

통합 테스트/전체 build에는 기존 Testcontainers 실행 환경인 Docker가 필요하다. `./gradlew test`에는 MySQL 통합 테스트가 포함되지 않으며 `build`가 integrationTest를 포함한다. 이번 테스트는 시작 기능을 검증하며 GD-157 점수 판정·GD-159 결과 제출·GD-160 지급이나 실제 FE 연동 성공을 의미하지 않는다.

## 연동과 운영 준비

- 실제 시작에 사용할 게임 행은 `code=COLOR_WORD`, `ruleVersion=color-word-v1` 및 채택된 다섯 공개 규칙 필드를 갖춰야 한다. 자동 게임 등록·기존 행 보정이나 관리자 게임 API를 이번 작업에 추가하지 않았다. 테스트는 전용 게임 행을 준비하며 운영 DB의 등록·V020 적용 여부는 미확인이다.
- FE는 게임 UUID를 사용해야 한다. 임시 `game-color` 문자열을 그대로 보내면 UUID 형식 오류다. 목록·상세 G01/G02(GD-155)는 별도 후속이다.
- 승인된 관리자 시작 자격과 시작 오류 계약은 [spec 커밋 `792c2ca`](https://github.com/Uplus-GETDDO/getddo-spec/commit/792c2ca83f5386847749c345532e6cbf5604970a)으로 원격 main에 반영했다.
- 시작 성공 후 화면 규칙·seed 적용과 실패 시 플레이 금지, 응답 유실 시 사용자 다시 시작은 FE에서 연결한다. 자동 무한 발급을 추가하지 않는다.
- GD-159의 소유권·토큰 확인·오류 계약, 결과/통계/지급 원자성은 후속이다. 이번 저장 해시 발급만으로 토큰 검증·요청 제한이 완성됐다고 보지 않는다.

Jira GD-156은 발급 외 토큰 재사용 방지·미완료·만료 경계까지 완료 조건으로 적고 있다. 이번에 시작과 검증 함수를 작성했으나 만료 계약·결과 API 연결·사용자 테스트 증거가 남아 있어 Jira 전체 완료로 처리하지 않았다. 조회 당시 담당 전민규, 상태 할 일이며 Jira 필드는 변경하지 않았다.

## 2026-10-15 PR 준비 상태

사용자가 시작 API 테스트 성공을 확인한 뒤 커밋·push·PR을 요청했다. 최신 dev 9646207에서 시작 API 변경만 분리했으며, 이미 병합된 GD-157 판정기·규칙 파서·Jackson 의존성은 다시 포함하지 않았다. 이전 표의 미실행은 최초 작성 시점 기록이고, 현재 사용자 확인은 GD-156 관련 테스트 성공이며 전체 빌드/FE/운영 DB 검증은 미확인이다.

최신 dev 분리 후 core/API 단위·API 통합 테스트 소스의 Gradle 컴파일이 성공했다(19초). 로컬 전체 test/build 성공과 실제 FE/DB 배포는 별도 확인 전이다. 사용자의 명시 승인 후 spec의 시작 계정 자격·오류 계약을 원격 main의 `792c2ca`에 반영했고 PR #95에 연결했다.
