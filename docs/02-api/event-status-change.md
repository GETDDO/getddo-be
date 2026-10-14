# 이벤트 내부 상태 변경 — GD-196

- 상태: 검토 대기 — 담당자 확인 전
- 담당: E의 상태 변경 메서드 제공, B의 호출 시점·추첨 흐름 연결
- 공용 기준: [이벤트 규칙](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/02-domain/event.md)

## 호출 계약

Spring Bean으로 주입한 `EventStatusChangeService`의 다음 메서드를 기존 추첨 업무 트랜잭션에서 호출한다.

```java
void changeStatus(UUID eventId, EventStatus expectedStatus, EventStatus newStatus)
```

```java
// 기존 @Transactional 업무 메서드 안에서 호출한다. 실제 호출 시점은 추첨 담당자가 정한다.
eventStatusChangeService.changeStatus(eventId, EventStatus.CLOSED, EventStatus.DRAW_CONFIRMED);
```

서비스는 `@Transactional(propagation = MANDATORY)`로 기존 트랜잭션에 참여한다. 새 트랜잭션을 열지 않으며 트랜잭션 없는 호출은 거절한다. 추첨 처리와 상태 변경이 함께 확정되거나 롤백된다. 같은 객체의 직접 호출이나 `new`로 만든 서비스로 트랜잭션 프록시를 우회하지 않는다.

## 변경 조건과 저장

- 이벤트가 존재하고 `deleted_at`이 null이어야 한다.
- 이벤트 행을 쓰기 잠금으로 읽은 실제 현재 상태가 `expectedStatus`와 같아야 한다.
- 취소된 이벤트를 다른 상태로 변경할 수 없다.
- 현재·예상·변경 상태가 모두 같으면 상태 저장을 생략한다. 이 상태 변경으로 수정 시각을 갱신하지 않는다.
- 실제 변경은 `status`와 공통 JPA Auditing의 `updated_at`에 한정한다.

호출자의 미반영 변경을 flush한 뒤 잠근 Entity를 refresh해 기존 영속성 컨텍스트의 오래된 상태로 판정하지 않는다. 잠금은 호출자 트랜잭션 종료까지 유지한다. 설정 수정용 `findByIdForUpdate`·`update`와 `EventModificationService`의 동작은 그대로 둔다.

전이 시점·허용 전이의 선택, 추첨·발표·알림·반환·감사 기록과 취소 시각 등 부가 처리는 호출자 담당이다. 이 메서드만으로 이벤트 취소 업무 전체가 완료되는 것은 아니다. 신규 HTTP API·테이블·컬럼은 제공하지 않는다.

## 예외

업무 실패는 기존 `EventException`과 `EventErrorCode`로 전달한다.

| 조건 | 코드 | 처리 |
| --- | --- | --- |
| eventId null, 미존재, 삭제 이벤트 | `EVENT_NOT_FOUND` / EVENT-007 | 404 |
| 예상 상태 불일치, 취소 상태에서 다른 상태로 변경 | `STATUS_CHANGE_CONFLICT` / EVENT-014 | 409 |
| expectedStatus 또는 newStatus null | `INVALID_STATUS_CHANGE` / EVENT-015 | 400 |
| 호출자 트랜잭션 없음 | Spring `IllegalTransactionStateException` | 내부 호출 계약 위반 |

HTTP 매핑 값은 기존 오류 구조의 정보이며 새 API를 추가한다는 의미는 아니다. 업무 예외를 잡고 같은 트랜잭션에서 계속 처리하지 않는다. EVENT-013은 별도 GD-61 삭제 작업의 코드이므로 사용하지 않는다.

## 검증 상태

상태 일치·불일치, 취소 상태 복귀 거절, 동일 상태 재요청, 미존재·삭제 이벤트와 null 입력의 단위 테스트 코드를 준비했다. 사용자 요청에 따라 테스트는 별도 실행 요청 전까지 수행하지 않는다. 컴파일·정적 검토 결과는 당일 워크로그에 기록하며 테스트 통과로 표현하지 않는다. 동시 변경, 삭제·취소 경합, 기존 영속성 컨텍스트와 호출자 롤백에 대한 실행 검증은 후속 테스트 대상으로 남긴다.
