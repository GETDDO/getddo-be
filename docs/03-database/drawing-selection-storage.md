# GD-36 추첨 선정 저장 설계 검토

- 상태: 검토 대기 — 담당자 확인 전
- 범위: 추첨 후보의 응모권 수·가중치와 선정 결과의 저장 대응
- 공용 정책: `getddo-spec/02-domain/drawing.md`, `getddo-spec/03-decisions/013-linear-drawing-weight.md`
- 기준 코드: [이벤트 DDL](../../storage/db/src/main/resources/db/migration/event/V006__create_event_tables.sql), [추첨 DDL](../../storage/db/src/main/resources/db/migration/drawing/V007__create_drawing_tables.sql)

이 문서는 현재 SQL을 기준으로 한 백엔드 설계 검토다. DB 변경이나 선정 로직 구현이 완료됐다는 뜻은 아니다. 추첨 기회와 응모권 사용 상한은 위 공용 정책을 따른다.

## 후보 수량과 가중치

| 이벤트 방식 | `draw_candidates.ticket_count` | `draw_candidates.weight` |
| --- | --- | --- |
| 응모권 미사용 | 0 | 1 |
| 응모권 사용·가중치 미적용 | 접수 완료된 응모의 실제 차감 수량인 1 | 1 |
| 응모권 사용·가중치 적용 | 해당 이벤트에서 접수 완료된 응모의 실제 차감 수량 합계 | `ticket_count`와 동일 |

- 수량의 근거는 `event_entries.status = ACCEPTED`인 행의 `deducted_ticket_count`다. 추가 응모도 합산하고 `requested_ticket_count`와 거절 행은 제외한다. `event_participants.used_ticket_count`는 같은 이벤트·사용자의 누적 차감 수량을 대조하는 값으로 사용한다. 불일치 시 임의의 한 값을 채택하지 않고 정합성 오류로 다룬다.
- 후보는 해당 이벤트의 `event_participants`에서 추첨 대상 제외가 확정되지 않은 사용자로 한정한다. `draw_candidates`는 `(draw_run_id, participant_id)`가 유일하므로 후보당 실행별 한 행을 둔다.
- `ticket_count`는 현재 `BIGINT`, `weight`는 `DECIMAL(30,10)`이다. 선형 가중치는 정수 값으로 저장할 수 있고 별도 가중치 상한 컬럼이나 스키마 변경은 현재 정책상 필요하지 않다. 월말 소진용 이벤트에는 공용 정책상 수량 상한이 없으므로 구현에서 저장 자료형의 허용 범위와 합계 연산 오버플로를 검증해야 한다.

## 실행 스냅샷과 결과

- `draw_runs.status = READY`로 후보·조건을 고정할 때 실행별 후보의 응모권 수와 가중치를 `draw_candidates`에 저장한다. 같은 실행의 실패 후 재시도에서는 이 값을 다시 계산하지 않는다. 최초 추첨 뒤 새 재추첨 실행에는 공용 정책에 따라 최초 추첨 당시 가중치를 사용한다.
- 실제 선정은 상위 등수부터 진행하고, 선정된 사용자는 다음 자리의 후보에서 제외한다. `draw_results`의 `(draw_run_id, candidate_id)` 유일 제약은 한 실행 내 동일 후보의 중복 선정을 막는다. 후보가 부족한 자리는 `result_type = UNFILLED`로 남긴다.
- `draw_results`의 외래 키와 유일 제약만으로 후보·경품·실행이 같은 이벤트에 속하는지, 재추첨에서 이전 당첨자가 제외됐는지는 보장되지 않는다. 선정 결과를 확정하기 전에 이 관계를 검증해야 한다.

## 구현 전 확인

- D 담당 응모 처리와 `ACCEPTED` 행의 실제 차감 수량, `used_ticket_count` 갱신 시점·정합성 확인. 이 연동 경계의 코드 변경은 담당자 확인 후 진행한다.
- 후보와 경품 스냅샷 고정 시점의 동시 응모·제외 확정 처리 순서, 실패한 실행의 재시도 방식 확인.
- 가중치에 비례한 무작위 선정의 난수·정수 합계 계산 방식과 동시 실행 제어는 별도 백엔드 구현 설계에서 확정한다. 이 문서는 특정 알고리즘이나 스케줄러를 채택하지 않는다.
