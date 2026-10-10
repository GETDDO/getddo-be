# API·DB 대응과 스키마 검토

- 상태: 검토 대기 — 담당자 확인 전
- 기준: `getddo-be` 원격 `dev`의 현재 SQL

공용 API 초안과 초기 스키마의 대응을 정리한다. 공용 요구사항과 도메인 정책은 `getddo-spec`을 따른다.

2026-10-08 GD-93에서 V001~V011을 최신 [DBML](schema.dbml)의 44개 테이블로 교체했다. 아래 테이블 대응과 검토 항목은 교체 전 SQL의 기록이며 현재 스키마의 기준으로 사용하지 않는다. API·서비스 코드의 호환성 수정 및 대응표 재검토는 담당자의 후속 작업이다.

2026-10-05 추첨 ERD 검토안은 이 문서의 V007 초기 정의와 구분한다. 갱신한 공용 계약은 [SPEC 추첨 API](https://github.com/GETDDO/getddo-spec/blob/main/05-api/drawing.md)의 AD01~AD09이며, 후보 연결·결과 FK·실패·검증·공개 이력 설계는 [ADR-0003](../04-decisions/0003-drawing-snapshots-and-publications.md)과 ERD SQL 초안 (`drawing-erd-reviewed-erdcloud.sql`, 정리로 삭제됨; 작성 당시 내용은 Git 이력 참조)을 참고한다. 아래 매핑을 새 설계의 운영 반영 완료로 해석하지 않는다.

## 테이블 대응

아래 매핑은 SQL 정적 분석 결과다. 실제 DB 적용·마이그레이션 실행·업무 트랜잭션 동작을 검증했다는 뜻이 아니다. 테이블마다 CRUD API를 만들지 않고 업무 단위로 묶었다.

| 마이그레이션 | 테이블 | API·내부 처리 |
| --- | --- | --- |
| [V001](../../storage/db/src/main/resources/db/migration/user/V001__create_user_tables.sql) | `users` | 헤더 문맥, U01, AO01~AO02, 응모 자격 |
| [V002](../../storage/db/src/main/resources/db/migration/reward/V002__create_reward_tables.sql) | `reward_policies` | AP01~AP02, AM03, 게임 보상 정책 적용 |
| [V003](../../storage/db/src/main/resources/db/migration/mission/V003__create_mission_tables.sql) | `missions`, `mission_submissions`, `mission_reward_claims`, `mission_quiz_questions`, `quiz_question_options`, `quiz_answers`, `quiz_question_progress`, `survey_questions`, `survey_question_options`, `survey_answers` | M01~M04, AM01~AM03 |
| [V004](../../storage/db/src/main/resources/db/migration/attendance/V004__create_attendance_tables.sql) | `attendances`, `attendance_streaks`, `attendance_streak_policy_sets`, `attendance_streak_policies`, `attendance_reward_claims` | AT01~AT03, AP01~AP04 |
| [V005](../../storage/db/src/main/resources/db/migration/game/V005__create_game_tables.sql) | `games`, `game_plays`, `user_game_stats`, `game_reward_claims` | G01~G05, AG01~AG03, AR07 |
| [V006](../../storage/db/src/main/resources/db/migration/event/V006__create_event_tables.sql) | `events`, `event_prizes`, `event_participants`, `event_entries`, `banners` | E01~E09, B01, AE01~AE08, AB01~AB05, AO06~AO07 |
| [V007](../../storage/db/src/main/resources/db/migration/drawing/V007__create_drawing_tables.sql) | `draw_runs`, `draw_candidates`, `draw_results`, `current_awards`, `award_cancellations`, `publications` | E08~E09, AD01~AD07, 최초 자동 추첨·발표 |
| [V008](../../storage/db/src/main/resources/db/migration/ticket/V008__create_ticket_tables.sql) | `ticket_wallets`, `ticket_ledger`, `ticket_ledger_allocations`, `ticket_refund_jobs`, `ticket_recovery_targets` | T01~T02, AR04~AR05, AO03~AO05·AO08, 지급·차감·반환·만료 |
| [V009](../../storage/db/src/main/resources/db/migration/abuse/V009__create_abuse_tables.sql) | `abuse_cases` | AR01~AR07, 탐지 기록 |
| [V010](../../storage/db/src/main/resources/db/migration/notification/V010__create_notification_tables.sql) | `notification_jobs`, `notifications` | N01~N03, AN01~AN05, 비동기 생성·모의 발송 |
| [V011](../../storage/db/src/main/resources/db/migration/audit/V011__create_audit_tables.sql) | `audit_logs` | AU01~AU02 및 검토·공개 명단·정책·점수 변경 이력 |
| [V014](../../storage/db/src/main/resources/db/migration/notification/V014__add_notification_worker_indexes.sql) | 알림 생성·발송 대기 및 선점 만료 조회용 인덱스 | GD-68 비동기 알림 처리 기반. 신규 공개 API는 추가하지 않음 |
| [V016](../../storage/db/src/main/resources/db/migration/drawing/V016__add_initial_draw_terminal_states.sql) | 최초 추첨의 응모자 없음·전원 제외 정상 종료 상태와 입력 CHECK | GD-84 내부 준비 서비스. 공개 API는 추가하지 않음 |

## 확인된 차이와 구현 검토 항목

| ID | 코드·SQL에서 확인한 사실 | 명세 영향과 후속 작업 |
| --- | --- | --- |
| D01 | V004 `chk_streak_days`는 현재 `consecutive_days BETWEEN 0 AND 31` | 실제 연속 일수 29~31일을 저장할 수 있도록 현재 원격 `dev`에 반영됨. 단계 설정 상한 28일과 구분 |
| D02 | V006은 `max_tickets_per_user`의 양수 여부만 제한하고 별도 소진용 지정 필드는 없음 | `TICKET`·가중치 적용·상한 null 조합이 월말 소진용 상한 없는 이벤트다. 팀은 월말에만 운영하지만 등록 서비스는 이벤트 날짜가 월말인지 검사하지 않는다. 서비스가 일반 5장·미가중치 1장·미사용 0장 조합을 검증하고, 실제 응모에서는 유효 보유량을 검증한다. [공용 응모권 규칙](https://github.com/GETDDO/getddo-spec/blob/main/02-domain/ticket.md)을 따른다 |
| D03 | V008에 비동기 작업처럼 보이는 `ticket_refund_jobs`가 있음 | 테이블 이름과 상태가 취소 후 별도 반환을 허용하는 근거는 아님. 취소·반환 동시 완료 트랜잭션 설계 필요 |
| D04 | V006에서 이벤트·배너에 `deleted_at`을 정의하며 이벤트 FK는 유지 | 이벤트 삭제 시 연결 배너도 논리 삭제한다. 기존 알림의 FK는 유지할 수 있지만, 미발송 알림 예약 중단과 삭제된 이벤트 링크 처리는 기능 구현 시 연동해야 한다 |
| D05 | 검토 현재 상태는 `abuse_cases`, 공개 현재 상태는 `publications`, 게임 판정은 `game_plays`; 별도 검토/공개 변경/검증 결과 이력 테이블 없음 | `audit_logs.before_data/after_data` 등에 구조화된 이력을 저장하는 설계 또는 추가 스키마 검토. 현재 JSON 컬럼 존재만으로 이력 API가 구현되었다고 보지 않음 |
| D06 | `current_awards`는 현재 당첨 배정이고 공개 revision별 스냅샷 테이블은 없음 | 재추첨 실행 중의 새 배정과 기존 공개 명단을 구분해야 함. `current_awards`를 그대로 공개 조회하지 않고 publication 상태·변경 이력과 일치시키는 설계 필요 |
| D07 | `game_reward_claims`는 사용자·source_key 및 사용자·게임·보상일 UNIQUE를 모두 가짐 | 현재 원격 `dev`에 추가된 사용자·게임·보상일 UNIQUE를 기준으로 중복 지급을 검증 |
| D08 | `reward_policies`에 게임별 보상 조건 JSON 또는 검증 버전 필드는 없음. `games.rules`는 현재 게임 규칙 | 다음 일일 기준일 적용이 필요한 게임 보상 조건의 버전·예약 저장 구조 설계 필요 |
| D09 | 공통 응답·페이징은 존재하나 업무 Controller·DTO·헤더 사용자 문맥 필터는 없음 | 본 문서의 API는 호출 가능한 현재 기능이 아님. 각 담당자 구현 후 Swagger와 계약 대조 필요 |
| D10 | `missions.status`는 DRAFT/ACTIVE/ENDED, 문항별 `quiz_question_progress` 존재 | 상태 전환·다문항 누적 달성 정책은 테이블에서 역으로 확정하지 않음 |
| D11 | `ticket_ledger`는 수량 0을 허용하지 않고 출석 단계 정책은 보상 0을 허용 | 보상 기록과 실제 지급 원장을 구분. 지급량 0 허용 범위와 응답 계약 확인 필요 |

위 항목은 최신 SQL의 정적 검토 결과이며 런타임 검증 결과가 아니다. D01·D07은 기존 초안과 달리 현재 `dev`에 반영된 상태를 기록했다.
