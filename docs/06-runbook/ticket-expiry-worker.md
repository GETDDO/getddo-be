# 응모권 만료 워커 확인

GD-135의 만료 시각이 지난 응모권을 만료됨(`EXPIRED`)으로 바꾸는 워커의 실행 방식과 확인 절차다. 상태와 이력 규칙의 원본은 공용 [응모권 규칙](https://github.com/GETDDO/getddo-spec/blob/main/02-domain/ticket.md)이다. 설계 근거는 [ADR-0006](../04-decisions/0006-ticket-concurrency-control.md)에 있다.

## 동작

- `TicketExpiryWorker`가 기본 60초(이전 폴링이 끝난 뒤)마다 `TicketExpiryService.expireDue`를 호출한다. 한 번에 100장씩 최대 50묶음을 처리하고, 한 묶음을 채우지 못하면 그 폴링을 끝낸다.
- 만료 시각이 지난 사용 가능(`AVAILABLE`·`RETURNED`) 응모권만 대상이다. 사용 완료(`SPENT`)와 이미 만료된 응모권은 처리하지 않는다. 처리하면 응모권 상태를 만료됨으로 바꾸고 버전을 1 올리며 `EXPIRE` 이력을 남긴다. 만료 시각은 바꾸지 않는다.
- 묶음 하나가 하나의 트랜잭션(`REQUIRES_NEW`, `READ_COMMITTED`)이다. 다른 처리가 잠근 응모권은 기다리지 않고 건너뛰고 다음 폴링에서 처리한다. 서버가 여러 대여도 같은 응모권을 두 번 만료하지 않는다.
- 사용자에게 보이는 보유 수량은 이 워커에 의존하지 않는다. 보유 조회와 차감이 만료 시각을 직접 비교하므로, 워커가 멈추거나 늦어도 만료된 응모권은 쓸 수 없고 보이지 않는다. 워커는 저장된 상태와 이력을 시각과 맞춘다.
- 워커는 알림·추첨 워커가 쓰는 공용 스케줄러가 아니라 전용 스레드 1개(`ticket-expiry-`)에서 돈다.

## 설정

| 속성 | 기본값 | 설명 |
| --- | --- | --- |
| `getddo.ticket.expiry.worker.enabled` | `true` | `false`로 이 워커만 끈다 |
| `getddo.ticket.expiry.worker.poll-delay` | `60000` | 폴링 간격(밀리초). 1 미만이면 기동 때 실패한다 |
| `getddo.ticket.expiry.worker.batch-size` | `100` | 한 묶음에 처리할 장수. 1 미만이면 기동 때 실패한다 |

- 통합 테스트 프로필(`application-test.properties`)에서는 워커를 끈다.
- 이 워커가 쓰는 조회 인덱스는 `tickets(status, expires_at)`이며 앱 시작 때 Flyway가 적용한다. 기존 마이그레이션은 수정하지 않는다.

## 종료할 때

- 종료가 시작되면 진행 중인 폴링은 지금 처리하는 묶음까지만 끝내고 새 묶음을 시작하지 않는다. 최대 30초 기다린다.
- 묶음은 하나의 트랜잭션이라 응모권 갱신과 이력이 일부만 반영되지 않는다. 대기 시간이 지나도 묶음을 강제로 중단하지 않는다. 남은 응모권은 다음 기동 이후의 폴링이 나누어 이어서 만료한다.

## 확인 방법

- 실패 로그: `Ticket expiry failed: exceptionType=... causeType=...`. 예외 클래스와 원인 예외의 클래스만 남기고 메시지와 스택 트레이스는 남기지 않는다(응모권·사용자 정보 보호). 원인 파악이 필요하면 같은 시각의 DB 로그와 트랜잭션 오류를 함께 본다.
- 밀림 확인: 만료 시각이 지났지만 아직 사용 가능 상태인 응모권이 계속 남아 있으면 워커가 꺼져 있거나 실패 중이거나 폴링 한 번에 처리할 양(`batch-size × 50`)보다 밀린 양이 많은 것이다.

  ```sql
  select count(*) from tickets
  where status in ('AVAILABLE', 'RETURNED') and expires_at <= utc_timestamp(6);
  ```

- 시각은 UTC로 저장한다. 위 쿼리는 읽기 전용이며 데이터를 바꾸지 않는다.
- 이 문서는 만료된 응모권을 직접 고치거나 되돌리는 절차를 정하지 않는다.
