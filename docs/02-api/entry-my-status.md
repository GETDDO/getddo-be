# GD-224 이벤트 목록용 내 응모 이벤트 조회

- 작업 브랜치: `feat/GD-224`
- 배경: 이벤트 목록(E01)의 사용자 화면은 카드마다 응모 여부를 알려고 이벤트별로 응모 현황(E03)의 `mySpentTicketCount`를 따로 조회해야 했다. 프론트 요청 R7(`EventSummary.myEntered`)은 이 요청을 목록 한 번으로 줄이기 위한 것이다.

## 범위

이 작업은 **응모 도메인이 제공하는 조회 기능**만 만든다. HTTP 경로나 응답 필드는 추가하지 않는다. E01의 `EventSummary`에 `myEntered`를 넣는 일은 이벤트 도메인(E01 담당)이 한다.

## 제공하는 기능

`MyEntryStatusService.findEnteredEventIds(userId, eventIds)`

- 이벤트 목록의 ID를 받아 그중 사용자가 **접수 완료된 응모**를 가진 이벤트의 ID 집합을 한 번의 조회로 돌려준다.
- 거절된 응모(`REJECTED`)는 응모한 것으로 보지 않는다. 응모자(`event_participants`)는 접수가 완료될 때만 생기므로, `(event_id, user_id)`로 응모자가 있는지만 보면 이 규칙이 된다. 유니크 키 `uq_event_participants_1`로 조회한다.
- 비로그인(`userId`가 null)이나 이벤트 목록이 비면 저장소를 읽지 않고 빈 집합이다.
- 서비스는 사용자의 역할을 검사하지 않고 주어진 `userId`의 응모 기록만 읽는다. 관리자는 응모할 수 없어 보통 기록이 없지만 서비스가 그것을 보장하지는 않는다. **관리자 요청에는 `myEntered=false`를 적용하는 일을 E01 호출자가 한다**(관리자면 이 서비스를 부르지 않아도 된다).
- 읽기 전용이며 아무것도 잠그지 않는다.

## 이벤트 도메인에서 쓰는 방법

```java
Set<UUID> entered = myEntryStatusService.findEnteredEventIds(userId, pageEventIds);
// 각 카드의 myEntered = entered.contains(eventId)
```

목록 한 페이지의 이벤트 ID(최대 100개)를 한 번에 넘긴다.

## 검증

- 단위: `MyEntryStatusServiceTest`(저장소 위임, 비로그인·빈 목록은 저장소를 읽지 않음).
- 실제 MySQL: `MyEntryStatusIntegrationTest`(접수된 이벤트만 반환, 거절만 있는 이벤트·응모가 없는 이벤트·다른 사용자의 응모는 제외, 여러 번 응모해도 한 번만, 목록에 없는 이벤트는 제외, 기록 없는 사용자와 없는 이벤트는 빈 집합).
