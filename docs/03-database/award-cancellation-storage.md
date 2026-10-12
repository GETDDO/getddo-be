# 개별 당첨 취소·재추첨 저장

- 관련 작업: [GD-138](https://ureca4.atlassian.net/browse/GD-138)
- 업무 정책 원본: [추첨 규칙](https://github.com/GETDDO/getddo-spec/blob/main/02-domain/drawing.md)
- 공통 실행 계약: [GD-87](https://ureca4.atlassian.net/browse/GD-87)의 `공통 실행 계약 — 구현 기준`
- 공통 실행 저장: [추첨 저장 설계](drawing-selection-storage.md)

## 취소와 등록

`AwardCancellationService`는 외부 트랜잭션을 받지 않는다. 별도 Bean인 `AwardCancellationRegistrationService.register`가 REQUIRES_NEW·READ_COMMITTED 트랜잭션에서 관리자·대상을 검증하고 이벤트 → 원본 실행 순서로 잠근다. 새 REPLACEMENT 실행, 취소·멱등 키, 감사 기록과 이벤트 REDRAWING 전환을 같은 트랜잭션에서 저장한다. 기존 결과·후보 상세·응모권은 수정하지 않는다.

등록이 반환하면 커밋된 실행 ID와 실제 관리자 ID로 GD-87의 `execute(runId, actorId)`를 호출한다. 선정 실패는 취소·등록을 되돌리지 않으며 공통 실행의 실패 이력·예약·운영자 재개로 복구한다. 등록 이후 서버 중단으로 시작하지 못한 실행도 GD-87의 기존 등록 작업 회수 경로를 사용한다.

이번 구현에서 사용자에게 확인받은 조건: 같은 이벤트에 미확정 REPLACEMENT가 있으면 다른 당첨의 새 취소는 409로 거절한다. 고정된 유지 배정과 다른 취소가 충돌하지 않도록 하기 위한 조건이며 기존 취소 재요청과 AD09 시작·재개는 허용한다. 전체 취소 기능으로 확장하지 않는다.

## 멱등 키와 이력

[V020](../../storage/db/src/main/resources/db/migration/drawing/V020__add_award_cancellation_request_key.sql)은 기존 award_cancellations에 nullable `request_key BINARY(16)`과 UNIQUE를 추가한다. 기존 행은 NULL로 보존하고 새 AD05 등록은 UUID 키를 필수 검증한다. 기존 Flyway와 과거 데이터는 변경하지 않는다.

키는 이 테이블 전체에서 고유하다. 같은 키·처리자·당첨·양끝 공백을 제거한 사유는 같은 취소와 실행을 반환한다. 같은 키의 다른 요청은 409이며 다른 키로 이미 취소된 당첨을 요청해도 409다. 같은 이벤트 경합은 이벤트 잠금 이후 키를 다시 조회한다. 다른 이벤트의 같은 키 INSERT 경합은 패배한 등록 트랜잭션을 롤백한 뒤 별도 조회로 기존 요청과 비교한다. 키 충돌이 아닌 저장 오류는 감추지 않는다.

취소 저장은 EntityManager.persist로 INSERT만 수행하며 불변 엔티티를 merge로 덮어쓰지 않는다. 신규 취소 ID는 저장소에서 기존 Hibernate UUID v7 생성 방식을 재사용하며 실행 ID도 UUID v7을 사용한다. 기존 DDL상 실행 연결이 없는 행도 조회·변환할 수 있다. 서비스는 새 실행을 추정하거나 자동 등록하지 않고 취소 불가(409)로 거절한다.

감사는 A의 실제 `AuditLogService.record`를 재사용한다. AWARD_CANCEL은 취소 처리자·사유·결과·연결 실행을 등록 트랜잭션에 기록한다. 실제 실행·재개 처리자와 DRAW_EXECUTE/DRAW_RESUME 감사는 GD-87에 전달한다. 재요청으로 같은 취소 감사가 중복 생성되지 않는다.

## 입력 준비와 재개

`DrawReplacementInputPreparation`은 GD-87의 이벤트·실행 잠금과 시도 소유권이 확보된 준비 트랜잭션에 MANDATORY로 참여한다. `supports(REPLACEMENT)`와 `prepare(runId, attemptId)`를 구현하며 시도 토큰을 검증한다. 고정 입력은 그대로 반환하고 준비를 다시 하지 않는다.

GD-138은 최초 확정 후보, 현재 유지 결과와 전체 과거 취소 결과로 입력을 계산한다. 원래 후보 ID·수량·가중치·근거를 그대로 재사용한다. 이번 취소의 실제 경품 자리와 유지 결과 ID·사용자를 Rules 버전 2로 변환하고 GD-87의 `saveRegisteredReplacement`로 같은 등록 실행에 저장한다. 후보 상세를 새로 만들지 않는다. 후보가 없어도 READY 입력이며 공통 확정이 해당 자리만 UNFILLED로 저장한다.

AD09는 취소의 기존 연결 실행을 조회한다. FAILED는 `resume(runId, actorId)`, 준비·진행 중·CONFIRMED는 `execute(runId, actorId)`로 위임한다. 새 취소·실행을 만들지 않는다. CONFIRMED 재요청에는 저장 결과를 반환한다. 실제 공개 명단 갱신과 재추첨 결과 확인은 GD-139 책임이며 이 구현에서 자동 공개하지 않는다.

## HTTP 계약과 검증 경계

이 작업에서 승인받은 AD05는 `/api/v1/admin/wins/{winId}/cancellations`, UUID Idempotency-Key와 1~1000자 사유를 받는다. AD09는 `/api/v1/admin/cancellations/{cancellationId}/redraws`이며 본문이 없다. DB와 대조한 사용자 문맥을 받아 활성 관리자 권한을 서비스에서 검증한다. 확정 결과는 200, 미확정 상태는 202다. 공통 spec의 상세 API 초안 전체를 승인한 것으로 해석하지 않는다.

AD09 응답은 추가 사용자 확인에 따라 GD-87의 DrawExecutionResponse(runId, status, confirmation)를 재사용한다. confirmation은 CONFIRMED일 때 저장된 결과이며 후보·결과 개인정보는 관리자 응답에서만 제공한다.

GD-138 단위 테스트의 실행 대역 검증과 실제 연동을 구분한다. DB 통합 테스트는 실제 취소·등록·준비 서비스와 GD-87 실행을 연결하고, API 통합 테스트는 실제 HTTP·사용자 조회·서비스·DB까지 연결한다. 응모·보상 근거의 초기 테스트 데이터는 SQL fixture이며 D의 응모·보상 서비스 연동 검증을 대신하지 않는다.
