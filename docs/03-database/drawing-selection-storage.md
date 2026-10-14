# 추첨 실행·후보 스냅샷·결과 저장

- 상태: 응모 수량·이벤트 잠금·후보 확정 경계 합의 확인 — GD-85 결과 확정 구현은 검토 대기, GD-87 공통 실행 구현은 검토 대기, GD-138 연결 기준 전체 test/build 통과
- 구현 범위: GD-84 최초 실행과 후보·조건 스냅샷 확정, GD-130 추첨 제외 의존성 제거, GD-85 최초 결과 확정, GD-87 공통 실행·실패·동일 실행 재개(검토 대기, GD-138 연결 기준 전체 test/build 통과)
- 기준: [통합 ERD](schema.dbml), [공용 추첨 규칙](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/02-domain/drawing.md), [ADR-014](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/03-decisions/014-random-ticket-grades.md), [ADR-018](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/03-decisions/018-pre-issuance-abuse-blocking-and-no-draw-exclusion.md)
- GD-84에서 추가한 두 정상 종료 상태는 이력 호환성을 위해 유지한다. GD-130 이후 새 실행은 응모자 없음 종료만 생성한다.

이 문서는 백엔드 저장·트랜잭션 구현을 설명한다. 공용 정책은 링크한 스펙을 원본으로 사용한다. 과거 설명의 `ACCEPTED`, `weight = ticket_count`, `current_awards` 및 실행 행의 실패 횟수 집계는 현재 기준으로 사용하지 않는다.

## 입력과 수량 검증

`DrawSnapshotService.prepareInitial(eventId)`는 HTTP API가 아닌 내부 서비스다. `core`의 저장소 인터페이스를 통해 원본을 읽고 `storage:db`의 JPA 구현이 추첨 스냅샷 영속성을 담당한다. 원본 조회·이벤트 잠금 SQL은 JDBC로 재사용하며, 추첨 엔티티는 공통 UUID v7·UTC 시각 관례를 따른다. JSON 스냅샷은 BOM으로 관리되는 Jackson 3으로 직렬화한다.

- `event_entries`는 성공한 응모만 저장하며 상태 컬럼이 없다. 응모자별 전체 응모를 읽는다.
- 응모 ID에 연결된 `ticket_histories.operation_type = USE`를 `tickets`와 연결한다. 현재 티켓 상태로 필터링하지 않고 불변 `grade`와 사용 당시 이력을 읽는다.
- 응모별 사용 이력 수와 `deducted_ticket_count`를 대조한다. 티켓 소유자, 중복 이력·응모, 응모 내 중복 티켓 및 마감 이후 기록을 검증한다.
- 응모별 실제 차감 합계를 `event_participants.used_ticket_count`와 대조한다. 모든 응모자를 검증하며 불일치나 연산 오버플로가 있으면 전체 준비를 롤백한다. 원본을 보정하거나 해당 사용자만 빼고 진행하지 않는다.
- 최초 응모에서 추가 응모까지 합산한 실제 장수와 가중치를 별개 값으로 저장한다. 등급별 가중치는 스펙을 따른다. 미사용·가중치 미적용은 가중치 1이다.
- 원본 조회는 응모자·사용 이력·응모의 세 일괄 조회로 수행한다. 응모자별 반복 조회를 하지 않는다.

## 실행 식별과 잠금

- 이벤트 행을 먼저 `FOR UPDATE`로 잠근다. 잠금 획득 후 서버 시각과 마감 후 5분, OPEN/CLOSED 상태, 논리 삭제 여부를 검증한다. 취소 등 그 밖의 상태에서 새 최초 실행을 만들지 않는다.
- `(event_id, run_number = 0)`과 `draw_type = INITIAL`로 같은 최초 실행을 식별한다. UUID는 최초 저장에서 한 번 발급하며 `draw_runs`의 이벤트별 차수 UNIQUE가 중복을 막는다. 별도 멱등 키 컬럼을 추가하지 않는다.
- D 담당 응모 흐름도 기존 합의에 따라 이벤트부터 잠가야 한다. 이 작업에서 그 담당 코드를 대신 구현하거나 변경하지 않는다. 실제 응모·차감 서비스와의 경합 검증은 GD-131 범위다. 제외 확정 흐름과의 잠금 연계는 요구하지 않는다.

## 스냅샷과 불변성

### JPA 영속성 — GD-84 2026-10-10 보완

- `DrawRunEntity`, `DrawCandidateEntity`는 공통 `BaseEntity`의 UUID v7와 생성 시각 Auditing을 사용한다. 실행의 확정·시작·결과 확정 시각은 별도 `Instant` 필드다. `updated_at`이 없는 실제 테이블에 맞춰 `BaseUpdatableEntity`를 상속하지 않는다.
- 실행 종류·상태는 문자열 enum, UUID는 `BINARY(16)`, 스냅샷 문자열은 Hibernate JSON 타입으로 매핑한다. DB에는 문자열로 감싼 JSON이 아니라 JSON 객체를 저장하며 core 모델 변환은 `DrawSnapshotMapper`에서 수행한다.
- `DrawRunCandidateId`의 `(draw_run_id, candidate_id)` 복합 식별자와 `@MapsId` 연관관계로 실행·후보 FK를 매핑한다. 실행별 차수와 최초 실행별 참가자 UNIQUE는 Flyway 스키마와 같은 이름·컬럼으로 선언한다. 이벤트·참가자 등 다른 담당 영역의 참조는 기존 관례에 따라 UUID 값으로 두고 FK 검증은 실제 DB 제약이 담당한다.
- `DrawSnapshotRepositoryImpl`은 JPA 저장소를 통해 실행·최초 후보·실행별 연결을 저장하고 연결된 후보만 조회한다. PREPARING → 후보 → 연결 → 입력 확정 순서로 flush하며 같은 서비스 트랜잭션에서 커밋·롤백한다. 후보와 연결은 불변 엔티티이며, 실행 입력은 PREPARING에서 한 번만 확정한다.
- `DrawSnapshotSourceReader`는 기존 이벤트 `FOR UPDATE`와 응모자·응모·USE 이력 조회 SQL만 재사용한다. JDBC로 추첨 테이블을 저장하거나 조회하는 중복 경로는 없다. 다른 담당자의 엔티티·원본 데이터 모델·잠금 순서를 변경하지 않는다.
- GD-84의 JPA 매핑은 기존 V007·V016 구조를 재사용하며 매핑 자체를 위한 마이그레이션은 추가하지 않는다. GD-87 실행 제어 필드의 별도 V019는 아래 공통 실행 절을 따른다. `draw_results` 엔티티는 GD-85 기반을 재사용한다.

기존 `prepareInitial(eventId)` 호환 경로는 한 트랜잭션에서 실행 생성·최초 후보·실행별 연결·READY 전환을 처리하며 준비 실패 시 함께 롤백된다. GD-87 경로는 먼저 실행 등록을 커밋하고, 동일 실행의 후보·연결·입력 확정을 별도 트랜잭션에서 수행한다. 이 경로의 준비 실패는 입력 저장만 롤백하며 등록된 실행 ID는 유지한다.

- 후보의 `entry_snapshot`은 버전 1, 등급별 장수 및 응모별 사용 이력·실제 장수·접수 기록 시각을 보존한다.
- `eligibility_snapshot`은 버전 1, 사용자 ID·멤버십·역할·명단 확정 시각을 보존한다. 기존 JSON의 `excluded` 필드는 호환성을 위해 유지하며 새 후보에서는 항상 `false`다. 제외 판단 저장소를 조회한 결과를 의미하지 않는다. 과거 JSON은 다시 쓰지 않는다. 개인정보 이름·전화번호·이메일은 저장하지 않는다.
- 최초 실행의 `rules_snapshot`은 버전 1이며 이벤트 유형·가중치 적용 여부·최소 멤버십·등급별 가중치와 경품 조건을 보존한다. GD-87의 REPLACEMENT 버전 2 확장은 아래 공통 실행 절을 따른다.
- 알고리즘 버전은 `weighted-without-replacement-v1`로 고정한다. GD-56의 기존 선정 로직에 전달할 확정 가중치는 양의 BIGINT 범위에서 검증한다.
- READY/RUNNING/CONFIRMED 재요청은 원본 응모를 다시 읽지 않고 저장된 후보 연결·조건·가중치를 반환한다. 결과 선정·저장은 아래 GD-85 확정 서비스를 사용한다.
- 기존 prepareInitial(eventId)은 생성과 입력 준비를 함께 처리하며 영속 FAILED/PREPARING을 거절한다. GD-87 경로는 registerInitial(eventId)의 등록 커밋 후 execute(runId)를 호출한다. 등록 실행의 입력 준비는 prepareRegistered(runId)를 사용하며 실패해도 실행 ID를 유지한다.

## 정상 종료

응모자가 없으면 `NO_ENTRIES`로 저장하며 규칙·알고리즘·확정 시각을 보존하고 후보·당첨 결과를 만들지 않는다. 유효 응모자가 있으면 전원을 후보로 저장하고 `READY`로 확정한다. 새 실행은 `NO_CANDIDATES`를 생성하지 않으며 `DrawRunEntity.fixInput`에서도 이 상태로의 입력 확정을 거절한다.

과거 `NO_CANDIDATES` 실행은 enum·DB 상태와 저장된 규칙·알고리즘·확정 시각을 그대로 읽고 재요청에도 같은 실행을 반환한다. 새 응모나 원본 변경이 있어도 과거 실행을 재계산하지 않는다. GD-85·86 연동에서는 과거 정상 종료 상태를 실행·재선정 대상에서 제외해야 한다.

[V016](../../storage/db/src/main/resources/db/migration/drawing/V016__add_initial_draw_terminal_states.sql)는 두 상태와 정상 종료 입력 CHECK를 추가한다. 기존 V007을 수정하지 않는다. V015를 사용하는 이벤트 조회 PR #16과 충돌을 피하기 위해 V016을 사용한다. 두 PR의 병합·DB 적용 순서는 확인해야 한다. 이미 V016을 적용한 DB에 V015를 나중에 추가하면 기본 outOfOrder=false 설정에서 문제가 생길 수 있다. 이벤트 상태 갱신·사용자 종료 표시·알림은 이 내부 준비 서비스에서 처리하지 않는다.

## 추첨 제외 의존성 제거 — GD-130

`DrawSnapshotService`는 제외 저장소 없이 응모·차감 근거를 검증해 후보를 확정한다. `DrawExclusionRepository`, 기본 실패 빈을 제공하던 `DrawExclusionConfig`, `EXCLUSION_UNAVAILABLE` 경로를 제거한다. 내부 오류 번호 `DRAW-004`는 다른 오류로 재사용하지 않는다. 공개 HTTP API는 변경하지 않는다.

`abuse_cases` 등 기존 어뷰징 기록이나 지급 전 차단 기록을 후보 필터로 사용하지 않는다. 같은 사용자의 다른 정상 응모와 최초 후보를 소급 제거하지 않는다. 새 제외 테이블·차단 저장소·지급 차단 기능·알림은 이 작업에서 구현하지 않는다. GD-143은 선행 조건이 아니며, GD-85·86에는 후보 준비 계약 변경의 연동 영향이 있다.

과거 실행·스냅샷·결과와 기존 Flyway는 유지한다. 지급 전 차단의 적용 범위·판정 기준은 별도 후속 확인 사항이며, 기존 사후 당첨 취소·재추첨과 마감 후 5분 일정은 변경하지 않는다.

## 후속 범위와 검증

- GD-85: 아래 결과 확정 서비스·JPA 영속성 및 회귀 테스트. 현재 유효 당첨은 결과·취소 이력으로 판단한다.
- GD-86/87: 자동 실행, 프로세스 중단 복구, 실패 이력·자동 재시도·운영자 재개. 정상 종료와 운영자 대기 상태를 우회하지 않는다.
- 결과 발표·공개·사용자 조회·당첨 취소·재추첨은 GD-84 범위에서 제외한다.
- 단위 테스트는 유효 응모 전체 후보화, 등급별·추가 응모 집계, 개별·누적 불일치, 티켓 소유자, 마감 경계·취소·삭제, 미사용·미적용, 응모자 없음·과거 정상 종료·고정 실행 재사용을 검증한다.
- MySQL 통합 테스트는 Flyway 적용, 실제 테이블의 사용 이력 집계, JSON 왕복·불변 재요청, 중간 저장 롤백, 동시 준비, 이벤트 잠금을 가진 진행 중 응모 트랜잭션과의 경합을 검증한다. 경합 테스트의 응모는 테스트 SQL이며 아직 없는 D 서비스의 검증 결과가 아니다.
- GD-84 검증 이력은 [9일자 워크로그 PR #39](https://github.com/Uplus-GETDDO/getddo-be/pull/39)를 참고한다.

## 결과 원자적 확정 — GD-85

`DrawConfirmationService`는 GD-85 최초 실행 확정 서비스를 기반으로 한다. 기존 `confirm(runId)`와 GD-87의 토큰을 받는 `confirm(runId, attemptId)` 모두 내부 서비스다. 재추첨 실행 생성·대상 자리·유지 결과 입력 계약은 GD-138에서 연결하며, GD-87 확장 경로는 버전 2 REPLACEMENT 입력의 실제 대상 자리·최초 후보·유지 결과를 검증해 확정한다. GD-138의 `DrawReplacementInputPreparation`과 실제 취소·등록 서비스·AD05/AD09를 연결했다. 해당 범위의 실제 MySQL·HTTP 검증은 [GD-138 저장 설계](award-cancellation-storage.md)를 참고한다.

- 실행 ID로 이벤트 ID를 확인한 뒤 기존 이벤트 `FOR UPDATE` → 실행 `PESSIMISTIC_WRITE` 순서로 잠근다. 동일 이벤트의 확정을 직렬화하여 실행 간 유효 배정 충돌도 검증한다. 응모·취소 흐름의 이벤트 선잠금 합의는 유지하며 이 구현에서 해당 흐름을 변경하지 않는다.
- 서비스 트랜잭션은 `READ_COMMITTED`를 사용한다. 이벤트 ID 사전 조회가 MySQL의 오래된 일관 읽기 스냅샷을 고정하여, 잠금 대기 후 이전 요청이 저장한 결과를 놓치는 것을 방지한다. Spring Bean을 통해 하나의 업무 트랜잭션으로 호출한다.
- READY의 저장된 규칙·알고리즘 버전·양의 가중치·실행별 후보 연결을 사용한다. 참가자·최초 후보 실행·경품이 같은 이벤트인지, 후보의 실제 사용자와 스냅샷 사용자가 같은지 확인한다. 현재 응모 수량이나 가중치를 다시 계산하지 않으며 현재 경품의 인원·등수로 확정 입력을 교체하지 않는다.
- 유효 배정은 `CONFIRMED` 실행의 `SELECTED` 결과 중 `award_cancellations`에 연결되지 않은 결과다. 공개 명단에 포함되었는지는 내부 유효 배정 기준과 별개다. 후보 사용자 또는 최초 실행 대상 경품이 기존 유효 배정과 충돌하면 저장 전에 거절한다.
- GD-56 `DrawingSelectionService`를 재사용한다. 상위 등수부터 선정한 결과의 자리·후보·사용자·선정 순서와 후보 소진 이후 `UNFILLED`를 저장 전에 다시 검증한다.
- `DrawResultEntity`는 `BaseEntity`의 UUID v7·생성 시각을 사용하고 불변 이력으로 저장한다. 실행 FK, `(draw_run_id, candidate_id)` 후보 연결 복합 FK, 자리·후보·선정 순서 UNIQUE는 V007과 같은 컬럼·이름으로 매핑한다. 미충원의 후보·선정 순서는 NULL이다. 기존 스키마가 지원하므로 신규 마이그레이션은 필요하지 않다.
- 기존 토큰 없는 READY 확정은 결과·CONFIRMED·시작/확정 시각을 함께 저장한다. GD-87 경로는 소유권과 최초 시작 시각을 먼저 커밋하고 자신의 토큰으로 READY/RUNNING의 결과·CONFIRMED·확정 시각을 함께 저장한다. 결과 저장 실패 시 고정 입력과 소유권은 남고 부분 결과는 롤백된다. 이후 실패 이력·FAILED·예약을 별도 트랜잭션에서 확정한다.
- CONFIRMED 재요청은 선정기·현재 유효 배정·원본 응모 조회 없이 저장된 결과 ID·생성 시각·확정 시각·선정 순서를 반환한다. 경품별 상위 등수·자리 순서로 반환하며 그 등수도 규칙 스냅샷을 사용한다. 최초 응답부터 실제 DB 마이크로초 정밀도를 사용한다. 저장된 결과의 정합성을 검증하지만 난수를 재생하지 않는다.
- 결과 확정은 공개 명단·알림·이벤트 공개 상태를 변경하지 않는다. 취소 엔티티·취소 처리·공개 버전은 이 작업에서 구현하지 않는다.

GD-85 테스트는 단위 검증과 실제 MySQL의 JPA 왕복·FK/UNIQUE·롤백·재요청·잠금 경합·이벤트 관계·취소 이력에 따른 유효 배정 조회를 포함한다. 실제 실행 결과는 작업 완료 보고에 기록한다.


## GD-87 공통 실행·실패·재개 — 구현과 검증

상태: 검토 대기. 이 절은 GD-87 공통 실행 구현과 GD-138 실제 연결 설명이다. GD-138 PR #56의 `e0e28bf` 기준 `./gradlew test build --offline`는 단위 600건·API 통합 106건·DB 통합 338건(총 1,044건, 실패·오류·건너뜀 0건)으로 통과했다. 아래 GD-87 단독 검증 기록은 당시 기준의 결과다. dev `a98240f` 기준 전체 검증은 통과했다(단위 496건, 통합 375건). 최신 dev `0a32422` 병합 후 단위 546건과 API 통합 103건이 통과했다. 전체 build는 사용자 요청으로 DB 통합 테스트 진행 중 중단했으며 최신 기준 전체 성공으로 표시하지 않는다. 실제 결과는 [작업 기록](https://github.com/Uplus-GETDDO/getddo-be/blob/238a3c63b00e2af273570b6e63b49ec96b03b863/docs/05-worklogs/%EC%84%9D%EC%A2%85%EC%88%98/2026-10-12.md)에 기록했다. 운영 DB에는 적용하지 않았다.

문서 재확인 기준: getddo-spec 원격 main `7978d25`, 백엔드 원격 dev `0a32422`(2026-10-12). 기능별 커밋으로 변경을 보존한 뒤 최신 dev를 병합했다. 이번 원격 spec 갱신은 GD-72 관리자 알림 계약 확정이며 추첨 규칙·scope·추첨 미결정 사항과 추첨 API는 변경되지 않았다.

확정 도메인 정책, Jira의 공통 실행 구현 기준, 이 대화에서 승인한 최초 재개 HTTP 계약을 구분한다. `05-api/drawing.md`의 AD01~AD09는 계속 상세 계약 검토 대기이며, 승인된 최초 `/admin/draws/{runId}/resume` 경로·권한·상태 코드만 공용 spec에 별도 절로 반영했고 커밋 `7978d25`를 사용자 명시적 승인 후 origin/main에 push했다. 다른 API 상세 계약은 검토 대기로 유지한다. GD-87 재시도 수치와 저장 표현을 공용 장애 복구·취소 전환 정책 전체의 확정으로 해석하지 않는다.

- `DrawExecutionService.execute(UUID runId)`는 등록·커밋된 실행만 처리한다. 외부 트랜잭션 안에서는 호출을 거절한다. 취소·새 재추첨 생성·공개는 수행하지 않는다.
- 관리자 시작은 `execute(runId, actorId)`로 실제 처리자를 전달한다. 이 호출도 운영자 대기를 우회하지 않는다.
- 실행 가능 이벤트 상태는 최초 OPEN/CLOSED, 재추첨 REDRAWING이며 소유권 획득·입력 준비·확정에서 같은 검사 함수를 사용한다. GD-138은 취소·등록과 함께 REDRAWING 전이를 담당한다.
- `resume(runId, actorId)`는 활성 관리자 확인 후 실패한 동일 실행을 재개한다. 최초 HTTP 경로는 `resumeInitial`로 INITIAL만 허용한다. 실제 시도를 얻었을 때만 공용 감사 서비스로 `DRAW_EXECUTE` 또는 `DRAW_RESUME`을 기록한다. 진행 중·확정 재요청은 감사 기록을 중복 생성하지 않는다.
- 이벤트 → 실행 잠금을 유지하며 시도 소유권, 입력 준비, 결과 확정, 실패 저장은 별도 서비스 Bean의 트랜잭션이다. 입력 확정 커밋 뒤 선정하므로 선정 실패에도 스냅샷을 보존한다. CONFIRMED는 저장 결과만 반환한다.
- PREPARING은 입력 확정 전 상태이며 `attempt_id`가 있으면 준비 시도를 처리 중이다. READY/RUNNING의 고정 입력은 원본에서 재계산하지 않는다. 실패는 FAILED로 저장하되 입력·후보·기존 결과 이력을 수정하지 않는다.
- 별도 제어 테이블 없이 [V019](../../storage/db/src/main/resources/db/migration/drawing/V019__add_draw_execution_control.sql)로 `draw_runs`에 `attempt_id`, `lease_until`, `retry_at`, `retry_cycle_started_at`을 추가한다. 기존 Flyway와 상태 enum은 변경하지 않는다. 실행·예약 조회 인덱스와 실패 이력 인덱스를 추가한다.
- `FAILED + retry_at NULL`은 운영자 대기, 예약 값이 있으면 예약된 일시적 실패다. 실패 횟수·최근 실패는 `draw_failures`에서 조회하며 실행 행에 집계를 저장하지 않는다.
- 일시적 저장 오류·중단 시도만 최초 시도 후 10/30/60초로 최대 3회 재시도한다. 네 번째 실패, 정합성 오류, 미분류 오류는 운영자 대기로 간다. 재개는 새 복구 구간을 시작하되 전체 이력은 보존한다. DATETIME(6)에서 구간 경계와 이전 실패 시각이 같으면 1마이크로초 이후를 새 경계로 사용하고 새 실패 시각도 그 경계 이상으로 저장한다.
- 현재 시도 ID는 UUID v7이며 실패 ID로 재사용한다. 같은 실패 저장의 재요청은 기존 행을 확인해 중복 생성하지 않는다. 예외 원문·개인정보는 기록하거나 응답하지 않는다. 실패 저장 자체의 DB 오류는 호출자에 전파하고, 남은 소유권의 만료 복구가 같은 ID로 INTERRUPTED를 기록한다.
- 임대는 5분이다. 만료 복구도 이벤트 잠금을 얻은 뒤 상태·토큰을 재확인한다. 실제 선정이 이벤트 잠금을 보유하면 복구 요청은 기다린다. 늦게 도착한 이전 시도의 실패는 새 소유권·확정 상태를 덮어쓰지 않는다. 제어 정보 없는 과거 RUNNING은 운영자 대기로 보내고 자동 재선정하지 않는다.
- `DrawRetryWorker`는 1초 간격으로 등록 뒤 시작하지 못한 PREPARING/READY·만기 예약·만료 소유권을 최대 20건 조회한다. `getddo.drawing.retry-worker.enabled=false`로 중지할 수 있다. 신규 최초 이벤트 탐색·등록은 GD-86 책임이다. GD-86/GD-138은 등록 커밋 후 즉시 시작하며, 그 사이 프로세스가 중단된 경우 워커가 등록된 같은 실행을 회수한다. 신규 이벤트를 탐색하거나 새 실행을 등록하지 않는다.

### GD-138 입력 연결

`DrawInputPreparation`의 `supports(DrawType)`와 `prepare(UUID runId, UUID attemptId)`를 구현하는 Spring Bean을 제공한다. 공통 준비 서비스가 이벤트·실행 잠금과 소유권을 검증한 같은 트랜잭션에서 호출한다. 준비 구현은 별도 커밋 없이 같은 실행을 READY로 고정해 반환한다. 준비 Bean이 없거나 중복 등록되면 운영자 대기로 전환한다.

- `DrawRunEntity(eventId, runNumber, REPLACEMENT)`는 등록에 필요한 공용 생성자이며 등록·취소 원자성은 GD-138 서비스가 담당한다.
- `DrawSnapshot`에 `drawType`을 포함한다. 기존 7인자 생성자는 INITIAL로 유지한다.
- `Rules` 버전 1은 최초 입력과 기존 JSON 호환용이다. 버전 2는 원래 전체 경품·가중치 조건에 `originalRunId`, `targetSlots`, `retainedAwards`를 추가한다.
- `Slot(prizeId, rank, slotNumber)`는 실제 취소 자리 번호다. `RetainedAward(resultId, userId, prizeId, slotNumber)`는 유지 결과다. GD-138의 입력 구성 결과를 이 공용 표현으로 변환해 `saveRegisteredReplacement(snapshot)`에 전달한다.
- 재추첨 저장은 최초 후보 행을 새로 만들지 않고 기존 후보 ID만 실행별로 연결한다. 후보가 0명이어도 READY 입력이며 대상 자리를 UNFILLED로 확정한다.
- 공통 확정은 최초 조건 일치·역대 당첨 후보 제외·연결된 취소 자리·유지 결과 ID·사용자·자리와 현재 유효 배정을 검증한다. 선정기의 가상 자리 번호를 실제 대상 번호로 바꾸며 유지 당첨자·경품을 수정하지 않는다.

GD-138 취소·등록·입력 준비·AD05/AD09는 실제 서비스로 연결·검증했다. GD-86 등록·자동 시작은 별도 후속 작업이며 GD-137 조회 연결은 GD-169에서 통합했다. GD-87 MySQL 통합 테스트 9건은 실패 별도 커밋·예약/횟수 제한·운영자 재개·중복 실패/동시 실행·입력 보존·재추첨 자리 확정을 검증한다. GD-87 단독 테스트의 재추첨 등록·취소는 SQL 대역이었다. GD-138 통합 테스트는 실제 서비스·준비 Bean을 연결하며, 초기 응모·보상 데이터만 SQL fixture다. V019는 Testcontainers 임시 MySQL에 적용했으며 운영 DB에는 적용하지 않았다.

## GD-169 관리자 연동 — 검토 대기

아래 `execution` 중첩 DTO, 실패·감사 조회 경로 및 NO_ENTRIES 종료 응답 설명은 원격 공용 spec 반영 전의 백엔드 제안/구현 설명이다. 인접 spec의 사용자 승인 기록은 미커밋 상태이며 원격 main(`00f0d743`)의 계약 동기화는 아직 완료되지 않았다. 확정된 공용 정책을 이 절에서 변경하지 않는다.

- 재추첨 검증은 입력 고정 시각 이전의 실행별 최신 자리 결과와 취소 시각을 대조한다. 고정 후 발생한 취소를 과거 유지 배정에 소급 반영하지 않는다. 대상·유지 자리의 중복·누락과 최신 결과 소유권을 확인하며 이전 미충원 자리는 새 취소 대상에 강제로 넣지 않는다.
- 저장된 버전 2 조건을 읽을 때 대상 자리·유지 배정을 포함한 전체 규칙을 복원한다. 최초 후보와 재추첨 후보의 반환 순서는 재조회와 동일하게 맞춘다.
- 실행 상세의 `execution`은 `failureCount`, `latestFailure{id,runId,failureCode,message,failedAt,traceId}`, `retryAt`, `waitingForOperator`, `cancellations`를 제공한다. 실패 원문은 저장·응답하지 않으며 미분류 과거 코드는 안전한 일반 안내 문구로 표시한다.
- `GET /api/v1/admin/draws/{drawId}/failures`와 `/execution-audits`는 활성 관리자 전용 전체 이력 조회다. page 기본 1, size 기본 20·허용 1~100이며 시각·ID 최신순으로 페이징한다. 감사는 기존 `audit_logs`의 `DRAW_RUN` 근거를 읽고 원본 감사 데이터를 변경하지 않는다. 상세 요약과 전체 실패 이력을 중복 API로 제공하지 않는다.
- 최초 재개 중 NO_ENTRIES가 되면 HTTP 200이다. 이미 정상 종료한 실행은 재개하지 않는다. ATTEMPT_LOST는 INTERRUPTED로 분류하며 기존 최대 3회 자동 재시도와 운영자 대기 규칙을 유지한다.
- 공개 연결과 신규 V024 저장 근거는 [결과 공개 저장](draw-publication-storage.md), 등록·발송 장애 대조는 [공개 알림 복구](../06-runbook/draw-publication-delivery-plan.md)를 참고한다.
