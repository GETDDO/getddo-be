# 이벤트 취소 — GD-202

- 상태: 검토 대기 — 담당자 확인 전
- 담당: E(취소 업무), D(응모·반환), C(취소 안내), A(감사 저장 기반)
- 기준: [공용 이벤트 규칙](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/02-domain/event.md), [AE08](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/05-api/event.md)
- 연결 계약: [GD-133](https://ureca4.atlassian.net/browse/GD-133), [GD-70](https://ureca4.atlassian.net/browse/GD-70)

## 요청과 응답

`POST /api/v1/admin/events/{eventId}/cancel`에 `{"reason":"운영 사유"}`를 보낸다. 사용자 문맥은 기존 `@CurrentUser User`를 사용하며 서비스가 활성 관리자 여부를 다시 확인한다. 사유는 필수·공백 불가이며 `ticket_histories.reason`·`audit_logs.reason`의 TEXT 범위에 맞춰 UTF-8 65,535바이트까지 허용한다. 잘라 저장하지 않는다.

성공 응답은 `ResponseEnvelope<EventCancellationResponse>`다. `eventId`, 요청 처리 전 `previousStatus`, 변경 후 `status=CANCELED`, 실제 반환 서비스 결과를 합산한 `refundedTicketCount`, 최초 취소 시각 `processedAt`을 반환한다. 취소·전체 반환이 완료되어야 200이며 비동기 반환 접수 202는 제공하지 않는다.

2026-10-15 사용자 결정에 따라 이미 취소된 이벤트의 재요청은 409 `EVENT-017`로 거절한다. 최초 `canceled_at`·수정 시각을 바꾸거나 반환·알림·감사를 추가하지 않는다. 앞 요청의 응답을 받지 못했다면 이벤트 상세의 상태·취소 시각으로 결과를 확인한다. 반환 서비스 자체의 원본 USE 기준 중복 방지는 그대로 유지한다.

## 저장과 트랜잭션

`EventCancellationService.cancel(eventId, actorId, reason)`는 관리자 HTTP 요청의 업무 트랜잭션 경계다. `READ COMMITTED`로 새 트랜잭션을 열고 다음을 한 번에 처리한다. 다른 격리 수준의 기존 트랜잭션에 합류시키는 내부 호출용 서비스가 아니다.

1. 관리자·사유를 검증하고 미삭제 이벤트를 쓰기 잠금으로 조회한다. 기존 상태 잠금 조회와 같이 flush·존재 확인·해당 Entity 분리·잠금 재조회로 이전 캐시를 사용하지 않는다.
2. 기존 `events.status`를 CANCELED로 바꾸고 기존 `canceled_at`에 마이크로초 단위 UTC 시각을 저장한다. JPA Auditing으로 `updated_at`도 함께 반영하고 flush한다. 경품·설정·응모 원본은 보존한다.
3. 해당 이벤트의 `ACCEPTED`이며 `deducted_ticket_count > 0`인 응모 ID를 최신 잠금 읽기로 조회한다. 거절·무료 응모는 반환 서비스를 호출하지 않는다.
4. 응모별로 `TicketRefundService.refund(new RefundCommand(entryId, reason))`를 호출한다. 반환 대상 선택·등급·만료·버전·원본 USE 멱등성은 D의 기존 서비스가 맡는다.
5. GD-70의 `EventChangeNotificationService.register(EventNotificationChange.canceled(...))`로 남은 시작 알림 생성을 중지하고 기존 시작 알림 수신자 대상의 취소 안내 작업을 등록한다. 사용자별 실제 알림 생성·모의 발송은 기존 워커가 커밋 후 처리한다. 전체 응모자에게 수신 대상을 확대하거나 별도 반환 안내 유형을 추가하지 않는다.
6. `AuditLogService.record`로 `EVENT_CANCEL`/`EVENT` 감사 행을 추가한다. 처리자·취소 사유·변경 전 상태·변경 후 상태/취소 시각/반환 수량을 기록한다.

반환·알림 작업 등록·감사 저장 중 실패하면 모두 롤백된다. 예외를 삼키거나 다른 트랜잭션으로 반환을 분리하지 않는다. 실패 뒤 재요청은 원래 상태를 읽고 다시 처리할 수 있다. 완료된 취소는 재요청을 거절하므로 취소 알림 발생 ID도 중복 발급하지 않는다.

응모는 이벤트 공유 잠금, 취소는 이벤트 쓰기 잠금을 먼저 확보한다. 응모가 먼저 커밋하면 취소가 그 응모와 USE 이력을 읽어 반환한다. 취소가 먼저 커밋하면 이후 신규 응모는 기존 응모 서비스의 `ENTRY-005`로 거절된다. READ COMMITTED는 잠금 전 관리자 조회가 생성한 스냅샷 때문에 기존 반환 서비스의 일반 USE 조회가 누락되는 것을 방지한다.

별도 테이블·컬럼이나 Flyway 변경은 없다. 배너는 수동 관리 정책을 유지한다. 추첨 실행·공개 차단은 기존 B 담당의 이벤트 잠금·취소 상태 검사를 재사용하며 GD-196의 범용 상태 변경이나 설정 수정 동작을 확장하지 않는다.

## 오류

| 조건 | 응답 |
| --- | --- |
| 사용자 문맥 없음·등록 사용자 부재 | 401 EVENT-005 |
| 비활성 사용자·관리자 아님 | 403 EVENT-006 |
| 없는 이벤트·논리 삭제 이벤트 | 404 EVENT-007 |
| 사유 null·공백·TEXT 바이트 초과 | 400, DTO 입력 오류 또는 EVENT-016 |
| 이미 취소된 이벤트 | 409 EVENT-017 |
| 반환 서비스 업무 실패 | 기존 TICKET 오류를 전파하고 취소도 롤백 |

## 검증

2026-10-15(KST) 실제 실행 결과: 서비스 단위 11개·`AdminEventCancellationTest`의 MySQL 통합 9개 성공. 전체 `test build`도 1,948개 테스트에서 실패·오류·건너뜀 없이 성공했다. 전체 실행 후 과거 상태를 새 단위 테스트의 제공 상태 목록에서 제외하고, 변경된 단위 테스트를 포함한 `test build`를 다시 확인했다.

통합 테스트는 실제 응모별 반환과 취소 재요청 409, 무료·거절 응모 제외, 두 번째 응모 반환 도중 실패 및 알림·감사 실패의 전체 롤백, 실패 뒤 재시도, 응모/취소 양방향 이벤트 행 잠금 경합을 검증한다. 고정 Clock과 독립 트랜잭션을 사용하며 `performance_schema.data_lock_waits`에서 실제 이벤트 잠금 대기를 확인한다.

```bash
./gradlew :core:test --tests 'com.getddo.core.event.service.EventCancellationServiceTest'
./gradlew :api:integrationTest --tests 'com.getddo.api.event.AdminEventCancellationTest'
./gradlew test build
```
