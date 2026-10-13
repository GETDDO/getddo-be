# ADR-0006: 응모권 지급·차감·반환·응모·만료의 동시성 제어

- 상태: 제안
- 작성자 / 날짜: AI 초안 / 2026-10-12
- 검토자: 윤태형 확인 대기
- 관련 작업 기록 / PR: GD-133 [PR #45](https://github.com/GETDDO/getddo-be/pull/45), GD-134 [PR #51](https://github.com/GETDDO/getddo-be/pull/51), GD-135 [PR #63](https://github.com/GETDDO/getddo-be/pull/63), 2026-10-12 작업 기록([공용 워크로그 PR #55](https://github.com/GETDDO/getddo-be/pull/55))
- 대체한 ADR / 대체된 ADR: 해당 없음

## 배경

응모권은 한 장이 `tickets` 한 행이고, 상태 변화는 `ticket_histories`에 추가로만 남긴다. 한 응모권을 지급, 차감, 반환, 만료가 서로 다른 요청과 워커에서 동시에 건드릴 수 있다. 같은 사용자의 응모가 겹치거나, 서로 다른 사용자의 요청이 겹치거나, 이벤트 취소와 만료 워커가 겹쳐도 다음이 지켜져야 한다.

- 한 장의 응모권이 두 번 사용되거나 두 번 만료되지 않는다.
- 같은 요청을 다시 보내면 새로 처리하지 않고 확정된 결과를 돌려준다.
- 서로 다른 사용자의 요청이 교착하지 않는다.

이 기능의 잠금 설계는 코드 곳곳의 Javadoc과 PR 본문에만 있어, 다음 담당자가 한곳에서 볼 수 없다. 설계 과정에서 MySQL InnoDB의 `REPEATABLE READ` 스냅샷과 gap lock 때문에 교착을 실제로 만났고(`TicketCrossUserConcurrencyTest`), 그 경위를 남길 필요가 있다.

## 결정과 이유

공통 원칙은 "서비스는 호출자의 트랜잭션에 참여하고, 행을 잠근 뒤 최신 값으로 판정한다"이다.

### 지급 (`TicketGrantService`)

- `@Transactional(propagation = MANDATORY)`로, 청구(출석·미션·게임 보상 청구)와 같은 트랜잭션에서만 호출할 수 있다. 호출자가 청구를 저장하고 지급을 호출해야 함께 커밋되거나 롤백된다.
- 지급 전에 청구 행을 `FOR UPDATE`로 잠그고 사용자·수량이 요청과 같은지 확인한다. 같은 청구의 동시 지급은 이 잠금에서 줄을 서고, 뒤 요청은 앞 요청이 커밋한 뒤 기존 응모권을 보고 같은 결과를 돌려받는다(`replayed=true`).

### 차감 (`TicketUseService`)

- `MANDATORY`이고 격리 수준은 호출자가 정한다. 응모는 `READ_COMMITTED`로 호출한다.
- 사용 가능한 후보는 **범위 잠금 없이 읽고**(만료가 이른 순), 후보를 PK로 한 장씩 `PESSIMISTIC_WRITE`로 잠그며 영속성 컨텍스트 값을 DB 최신 값으로 덮어쓴다(`lockAndRefresh`). 잠그는 사이 다른 처리가 바꾼 응모권은 최신 값으로 판정해 건너뛴다. 범위(`user_id, grade, expires_at`)에 `FOR UPDATE`를 걸지 않아 gap lock이 생기지 않는다.
- 등급이 여러 개면 등급 순서로 정렬해 모든 차감이 같은 순서로 잠근다.
- 잠금을 모두 쥔 뒤 처리 시각을 다시 구해 만료된 응모권이 섞였으면 실패시킨다(`TICKET-006`). 다른 응모권으로 바꿔 다시 잠그지 않는다. 앞선 잠금을 쥔 채 다른 응모권을 다시 잠그면 잠금 순서가 뒤집혀 교착할 수 있기 때문이다.
- 같은 응모 ID로 다시 호출하면 기존 사용 이력을 보고 결과를 돌려준다.

### 반환 (`TicketRefundService`)

- `MANDATORY`. 응모에 쓴 응모권을 PK 바이트 순서로 한 장씩 잠근다(`findAllForUpdate`).
- 이미 반환된 응모의 재호출은 잠금 뒤 응모권 상태로 판정한다. 상태가 "사용 직후"도 "이미 반환"도 아닌 경우(`CHANGED`)에만 반환 이력을 잠금 읽기로 다시 확인해 재생하고, 없으면 `TICKET-009`로 실패한다. 반환 중 다른 요청이 같은 응모를 반환하고 그 응모권을 다시 사용한 경우를 처리하기 위해서다.
- 반환은 이벤트 취소 때만 한다. 추첨 제외 반환은 없다(ADR-018·019).

### 응모 (`EntryService`, `EntryRecorder`)

- 응모 ID는 `Idempotency-Key` UUID이며 `event_entries`의 PK다. 별도 멱등 테이블 없이 PK가 같은 키의 동시 요청을 직렬화한다.
- 잠금 순서는 `events`(`FOR SHARE`) → `event_participants`(PK `FOR UPDATE`) → `tickets`로 고정한다. 응모끼리는 이벤트 행을 공유 잠금으로 읽어 서로 막지 않고, 추첨 준비의 `FOR UPDATE`는 진행 중인 응모의 커밋을 기다린다. 참여자 행이 없으면 빈 키에 `FOR UPDATE`를 걸지 않고 삽입한다(gap lock 회피).
- `EntryRecorder`는 `READ_COMMITTED`이고, 바깥 `EntryService`는 트랜잭션 없이(`NOT_SUPPORTED`) 잠금 실패와 중복 키 위반(MySQL 1062)에 한해 새 트랜잭션에서 한 번 다시 처리한다. FK·NOT NULL·CHECK 위반은 다시 처리하지 않는다.

### 만료 (`TicketExpiryService`, `TicketExpiryWorker`)

- 호출마다 독립 트랜잭션(`REQUIRES_NEW`, `READ_COMMITTED`)이다. 만료 시각이 지난 사용 가능(`AVAILABLE`·`RETURNED`) 응모권을 `FOR UPDATE SKIP LOCKED`로 잠가 읽는다. 다른 처리가 잠근 응모권은 기다리지 않고 건너뛰며 다음 폴링에서 처리한다. 서버 두 대의 워커가 겹쳐도 같은 응모권을 두 번 만료하지 않는다. 사용 완료(`SPENT`) 응모권은 만료하지 않는다.
- 후보를 읽는 네이티브 쿼리는 Entity를 바로 반환하므로 같은 트랜잭션에 오래된 Entity가 있으면 낡은 값을 줄 수 있다. 새 영속성 컨텍스트를 쓰는 `REQUIRES_NEW`로 이를 막는다.
- 워커는 `@Scheduled`의 공용 스케줄러(스레드 1개, 알림·추첨 워커와 공유)가 아니라 전용 스레드 1개(`ticket-expiry-`)에서 돌린다. 한 번에 최대 약 5천 장(100장×50묶음)을 처리해도 다른 워커가 밀리지 않게 하려는 것이다. `TaskScheduler` 빈을 등록하면 Spring Boot 기본 스케줄러가 꺼지고 그 빈이 다른 워커의 스케줄러가 되므로, 워커 클래스가 직접 생성하고 종료한다.

## 검토한 대안

- **반환 때 반환 이력을 항상 잠금 읽기**: 서로 다른 사용자의 동시 반환에서 교착이 났다. 반환 이력이 없는 구간에 공유 gap lock을 서로 잡은 뒤 이력 INSERT가 서로 막히기 때문이다(`TicketCrossUserConcurrencyTest`). `CHANGED` 상태에만 확인하도록 좁혔다.
- **후보 범위에 `FOR UPDATE`**: `REPEATABLE READ`에서 `(user_id, grade, expires_at)`나 `(status, expires_at)` 범위의 gap lock이 지급 INSERT 등을 막을 수 있어 쓰지 않았다.
- **만료된 응모권을 다른 것으로 바꿔 다시 잠금**: 잠금 순서가 뒤집혀 교착할 수 있어 하지 않았다. 같은 달 지급분은 모두 같은 시각에 만료하므로 대체할 수 있는 경우도 드물다.
- **별도 멱등 테이블**: 응모 PK가 같은 역할을 하므로 두지 않았다(사용자 결정).
- **만료 워커의 스레드 분리 방식**: `spring.task.scheduling.pool.size`를 늘리는 방법은 공용 설정을 바꾸고 만료 워커 전용이 아니라서, `TaskScheduler` 빈 등록은 기본 스케줄러를 대체해서 쓰지 않았다.

## 영향과 재검토 조건

- 서비스가 `MANDATORY`라서 호출자가 트랜잭션을 열어야 한다. 이벤트 취소에서 `TicketRefundService`를 호출하는 연결은 아직 없다(이벤트 담당 영역). 연결할 때의 제안: 이벤트 상태를 취소로 바꾸는 일과 대상 응모별 `TicketRefundService.refund` 호출을 한 트랜잭션에서 하고, 이벤트 행 잠금으로 진행 중인 응모와의 확정 순서를 보장하며(응모는 이벤트 행을 `FOR SHARE`로 읽는다), 반환이 실패하면 취소도 롤백하고, 재시도하면 기존 반환 이력을 재생한다. 연결한 뒤에는 반환 실패 시 취소가 롤백되는지와 취소 중 동시 응모의 확정 순서를 통합 테스트로 검증한다.
- 성능은 측정하지 않았다. 사용자당 보유량이 많거나 동시 응모가 많을 때 PK 한 장씩 잠그는 비용을 다시 확인해야 한다.
- 만료 후보 조회의 `ORDER BY`는 두지 않았다. `status in (...)` 범위에서는 인덱스 정렬을 쓰지 못해 정렬 작업이 생길 수 있으므로 `EXPLAIN` 없이 추가하지 않는다.
- MySQL 격리 수준이나 인덱스가 바뀌면 gap lock 가정을 다시 확인한다.
- 지급·차감·반환 동시성 테스트는 반복 실행(`@RepeatedTest`)과 잠금 대기 감지(`LockWaitProbe`)에 의존하므로, 서비스 순서를 바꾸면 해당 테스트를 먼저 갱신한다.
