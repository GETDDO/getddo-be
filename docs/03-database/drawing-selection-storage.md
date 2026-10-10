# 추첨 실행·후보 스냅샷 저장

- 상태: 응모 수량·이벤트 잠금·후보 확정 경계 합의 확인 — JPA 보완 구현은 검토 대기
- 구현 범위: GD-84 최초 실행과 후보·조건 스냅샷 확정
- 기준: [통합 ERD](schema.dbml), [공용 추첨 규칙](https://github.com/GETDDO/getddo-spec/blob/main/02-domain/drawing.md), [ADR-014](https://github.com/GETDDO/getddo-spec/blob/main/03-decisions/014-random-ticket-grades.md)
- 사용자 확인: 사용 이력에서 등급별 장수 집계, 응모별 검증 후 누적 대조, D 구현을 기다리지 않고 추첨 측 구현, 대상 없는 두 정상 종료 상태 추가.

이 문서는 백엔드 저장·트랜잭션 구현을 설명한다. 공용 정책은 링크한 스펙을 원본으로 사용한다. 과거 설명의 `ACCEPTED`, `weight = ticket_count`, `current_awards` 및 실행 행의 실패 횟수 집계는 현재 기준으로 사용하지 않는다.

## 입력과 수량 검증

`DrawSnapshotService.prepareInitial(eventId)`는 HTTP API가 아닌 내부 서비스다. `core`의 저장소 인터페이스를 통해 원본을 읽고 `storage:db`의 JDBC 구현이 영속성을 담당한다. 기존 JDBC·UUID v7·UTC 변환 관례를 따른다. JSON 스냅샷은 BOM으로 관리되는 Jackson 3으로 직렬화한다.

- `event_entries`는 성공한 응모만 저장하며 상태 컬럼이 없다. 응모자별 전체 응모를 읽는다.
- 응모 ID에 연결된 `ticket_histories.operation_type = USE`를 `tickets`와 연결한다. 현재 티켓 상태로 필터링하지 않고 불변 `grade`와 사용 당시 이력을 읽는다.
- 응모별 사용 이력 수와 `deducted_ticket_count`를 대조한다. 티켓 소유자, 중복 이력·응모, 응모 내 중복 티켓 및 마감 이후 기록을 검증한다.
- 응모별 실제 차감 합계를 `event_participants.used_ticket_count`와 대조한다. 제외된 응모자도 검증하며 불일치나 연산 오버플로가 있으면 전체 준비를 롤백한다. 원본을 보정하거나 해당 사용자만 빼고 진행하지 않는다.
- 최초 응모에서 추가 응모까지 합산한 실제 장수와 가중치를 별개 값으로 저장한다. 등급별 가중치는 스펙을 따른다. 미사용·가중치 미적용은 가중치 1이다.
- 원본 조회는 응모자·사용 이력·응모의 세 일괄 조회로 수행한다. 응모자별 반복 조회를 하지 않는다.

## 실행 식별과 잠금

- 이벤트 행을 먼저 `FOR UPDATE`로 잠근다. 잠금 획득 후 서버 시각과 마감 후 5분, OPEN/CLOSED 상태, 논리 삭제 여부를 검증한다. 취소 등 그 밖의 상태에서 새 최초 실행을 만들지 않는다.
- `(event_id, run_number = 0)`과 `draw_type = INITIAL`로 같은 최초 실행을 식별한다. UUID는 최초 저장에서 한 번 발급하며 `draw_runs`의 이벤트별 차수 UNIQUE가 중복을 막는다. 별도 멱등 키 컬럼을 추가하지 않는다.
- D 담당 응모 흐름·제외 확정 흐름도 기존 합의에 따라 이벤트부터 잠가야 한다. 이 작업에서 그 담당 코드를 대신 구현하거나 변경하지 않는다. 실제 응모·차감 서비스와의 경합 검증은 해당 구현 반영 후 추가한다.

## 스냅샷과 불변성

### JPA 영속성 — GD-84 2026-10-10 보완

- `DrawRunEntity`, `DrawCandidateEntity`는 공통 `BaseEntity`의 UUID v7와 생성 시각 Auditing을 사용한다. 실행의 확정·시작·결과 확정 시각은 별도 `Instant` 필드다. `updated_at`이 없는 실제 테이블에 맞춰 `BaseUpdatableEntity`를 상속하지 않는다.
- 실행 종류·상태는 문자열 enum, UUID는 `BINARY(16)`, 스냅샷 문자열은 Hibernate JSON 타입으로 매핑한다. DB에는 문자열로 감싼 JSON이 아니라 JSON 객체를 저장하며 core 모델 변환은 `DrawSnapshotMapper`에서 수행한다.
- `DrawRunCandidateId`의 `(draw_run_id, candidate_id)` 복합 식별자와 `@MapsId` 연관관계로 실행·후보 FK를 매핑한다. 실행별 차수와 최초 실행별 참가자 UNIQUE는 Flyway 스키마와 같은 이름·컬럼으로 선언한다. 이벤트·참가자 등 다른 담당 영역의 참조는 기존 관례에 따라 UUID 값으로 두고 FK 검증은 실제 DB 제약이 담당한다.
- `DrawSnapshotRepositoryImpl`은 JPA 저장소를 통해 실행·최초 후보·실행별 연결을 저장하고 연결된 후보만 조회한다. PREPARING → 후보 → 연결 → 입력 확정 순서로 flush하며 같은 서비스 트랜잭션에서 커밋·롤백한다. 후보와 연결은 불변 엔티티이며, 실행 입력은 PREPARING에서 한 번만 확정한다.
- `DrawSnapshotSourceReader`는 기존 이벤트 `FOR UPDATE`와 응모자·응모·USE 이력 조회 SQL만 재사용한다. JDBC로 추첨 테이블을 저장하거나 조회하는 중복 경로는 없다. 다른 담당자의 엔티티·원본 데이터 모델·잠금 순서를 변경하지 않는다.
- 기존 V007·V016과 DB 구조를 유지한다. JPA 매핑 자체를 위한 새 마이그레이션은 필요하지 않다. `draw_results` 엔티티는 GD-85 범위다.

한 트랜잭션에서 PREPARING 실행, `draw_candidates`의 최초 후보 상세 정보, `draw_run_candidates`의 최초 실행별 후보 연결을 저장하고 READY로 전환한다. 중간 저장 실패 시 실행과 후보·연결이 모두 롤백된다.

- 후보의 `entry_snapshot`은 버전 1, 등급별 장수 및 응모별 사용 이력·실제 장수·접수 기록 시각을 보존한다.
- `eligibility_snapshot`은 버전 1, 사용자 ID·멤버십·역할·명단 확정 시 제외되지 않았다는 판단과 판단 시각을 보존한다. 개인정보 이름·전화번호·이메일은 저장하지 않는다.
- 실행의 `rules_snapshot`은 버전 1, 이벤트 유형·가중치 적용 여부·최소 멤버십·등급별 적용 가중치 및 경품 ID·등수·당첨 인원을 보존한다.
- 알고리즘 버전은 `weighted-without-replacement-v1`로 고정한다. GD-56의 기존 선정 로직에 전달할 확정 가중치는 양의 BIGINT 범위에서 검증한다.
- READY/RUNNING/CONFIRMED 재요청은 원본 응모나 제외 판단을 다시 읽지 않고 저장된 후보 연결·조건·가중치를 반환한다. 결과 선정·저장은 GD-85 범위다.
- 이 구현에서는 롤백된 준비를 같은 이벤트 최초 실행 식별자로 다시 준비한다. 영속 FAILED/PREPARING의 운영자 재개·실패 이력·예약은 GD-87에서 상태 계약을 연결하며 현재는 INVALID_RUN으로 거절한다.

## 정상 종료

응모자가 없으면 `NO_ENTRIES`, 검증한 응모자 전원이 확정 제외됐으면 `NO_CANDIDATES`로 저장한다. 둘 다 규칙·알고리즘·확정 시각을 보존하고 후보·당첨 결과를 만들지 않는다. 재요청에는 같은 실행을 반환한다. 후속 GD-86에서도 이 상태를 자동 실행·재선정 대상에서 제외해야 한다.

[V016](../../storage/db/src/main/resources/db/migration/drawing/V016__add_initial_draw_terminal_states.sql)는 두 상태와 정상 종료 입력 CHECK를 추가한다. 기존 V007을 수정하지 않는다. V015를 사용하는 이벤트 조회 PR #16과 충돌을 피하기 위해 V016을 사용한다. 두 PR의 병합·DB 적용 순서는 확인해야 한다. 이미 V016을 적용한 DB에 V015를 나중에 추가하면 기본 outOfOrder=false 설정에서 문제가 생길 수 있다. 이벤트 상태 갱신·사용자 종료 표시·알림은 이 내부 준비 서비스에서 처리하지 않는다.

## 확정 제외 연결 — 후속

현재 ERD의 응모자 제외 저장 근거는 미연결이다. `abuse_cases.CONFIRMED`와 `award_cancellations`를 추첨 전 제외로 임의 해석하지 않는다.

`DrawExclusionRepository`는 이벤트 잠금을 보유한 같은 트랜잭션에서 확정 제외 응모자 ID를 반환한다. 저장 근거를 구현한 빈이 없으면 기본 구현은 `EXCLUSION_UNAVAILABLE`로 중단한다. 빈 집합을 반환하는 임시 운영 구현을 넣지 않는다. 응모자가 없는 경우는 제외 조회 없이 정상 종료할 수 있다.

테스트에서는 제외 저장소를 대체해 후보 제외·전원 제외·원본 검증을 확인한다. 저장 근거와 실제 제외 확정 흐름을 연결하고 경합을 검증하기 전에는 GD-84 연동 완료로 간주하지 않는다.

## 후속 범위와 검증

- GD-85: 저장된 후보·경품 입력으로 선정 후 결과와 CONFIRMED 원자적 확정. 현재 유효 당첨은 결과·취소 이력으로 판단한다.
- GD-86/87: 자동 실행, 프로세스 중단 복구, 실패 이력·자동 재시도·운영자 재개. 정상 종료와 운영자 대기 상태를 우회하지 않는다.
- 결과 발표·공개·사용자 조회·당첨 취소·재추첨은 GD-84 범위에서 제외한다.
- 단위 테스트는 등급별·추가 응모 집계, 개별·누적 불일치, 티켓 소유자, 마감 경계·취소·삭제, 미사용·미적용, 제외·정상 종료·고정 실행 재사용을 검증한다.
- MySQL 통합 테스트는 Flyway 적용, 실제 테이블의 사용 이력 집계, JSON 왕복·불변 재요청, 중간 저장 롤백, 동시 준비, 이벤트 잠금을 가진 진행 중 응모 트랜잭션과의 경합을 검증한다. 경합 테스트의 응모는 테스트 SQL이며 아직 없는 D 서비스의 검증 결과가 아니다.
- 실제 실행 결과는 [9일자 워크로그 PR #39](https://github.com/GETDDO/getddo-be/pull/39)에 기록한다.
