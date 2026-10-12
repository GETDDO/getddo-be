# 출석·미션·응모권 테이블 컬럼 설계

- 상태: 논리 설계 제안. 실제 DDL·마이그레이션은 수행하지 않았다.
- 기준: 공용 명세 `02-domain/attendance.md`, `mission.md`, `game.md`, `ticket.md` 및 ADR-005~008.
- 범위: 담당 영역(출석·미션·게임·응모권)에서 **추가할 테이블**과 **기존 테이블에서 고칠 컬럼**.
- 자료형은 기존 ERD를 따랐다. 식별자는 `binary(16)` UUID, 시각은 `datetime`(UTC), 수량은 `bigint` 또는 `int`.

전체 요약: **신규 7개 · 기존 수정 9곳**이다. 제안 목록에 있던 나머지는 기존 테이블이 이미 담을 수 있어 추가하지 않는다.

---

## 1. 출석

### `attendances` — 신규

일일 출석 기록. 연속 출석 판정의 원본이다.

| 컬럼 | 자료형 | 설명 |
| --- | --- | --- |
| `id` | binary(16) | 출석 ID |
| `user_id` | binary(16) | 사용자 ID |
| `attendance_date` | date | 출석 기준일. 00:00 UTC 경계로 판정한다 |
| `streak_count` | int | 이 출석 시점의 연속 출석 일수 |
| `created_at` | datetime | 출석 시각 UTC |

- `UNIQUE(user_id, attendance_date)` — 연타·새로고침·동시 요청·날짜 경계에서 하루 1회를 DB가 강제한다.
- `CHECK(streak_count >= 1)`, `FK user_id → users(id)`.
- `streak_count`를 기록해 두면 직전 출석 한 행만 읽어 판정한다. 어제면 `+1`, 아니거나 월이 바뀌면 `1`. 위 유니크 인덱스가 그 조회를 그대로 커버해 별도 인덱스가 필요 없다.
- 지급 수량과 적용 정책은 넣지 않는다. 그 정보는 `reward_claims`에 있으며, 두 곳에 두면 어긋난다.

### `attendance_streak_policies` — 신규

연속 출석 단계와 보상. 관리자가 수정하며 월 단위로 이력이 남는다.

| 컬럼 | 자료형 | 설명 |
| --- | --- | --- |
| `id` | binary(16) | 정책 ID |
| `created_by` | binary(16) | 등록 관리자 ID |
| `effective_month` | date | 적용 시작 월. UTC 기준 해당 월의 첫 날짜 |
| `stage_no` | int | 단계 번호. 1~3 |
| `streak_days` | int | 단계 달성에 필요한 연속 출석 일수. 1~28 |
| `reward_ticket_count` | int | 단계 달성 시 지급할 응모권 수 |
| `created_at` | datetime | 생성 시각 UTC |

- `UNIQUE(effective_month, stage_no)` — 한 달에 단계당 1행.
- `UNIQUE(effective_month, streak_days)` — 같은 달에 같은 일수를 두 단계에 넣는 것을 막는다.
- `CHECK(streak_days BETWEEN 1 AND 28)`, `CHECK(stage_no BETWEEN 1 AND 3)`.
- 초기값은 7·14·28일에 1·3·7장이다. 변경이 없는 달은 행을 만들지 않고, 판정할 때 `effective_month <= 해당 월` 중 가장 최근 월의 3행을 읽는다. 9월 판정이 9월 행을 보므로 정책을 바꿔도 소급되지 않는다.
- 단계 번호 오름차순과 일수 오름차순의 일치는 행 간 비교라 DB로 막기 어렵다. 애플리케이션에서 검증한다.

### 기존 테이블

| 테이블 | 무엇을 | 왜 |
| --- | --- | --- |
| `reward_policies` | 그대로 사용 | 기본 출석은 수량이 단일 값이라 `reward_type=ATTENDANCE` + `effective_from`으로 충분하다 |

---

## 2. 미션

퀴즈와 설문은 문항 구조가 같고 차이는 정답 유무뿐이라 테이블을 공유한다.

### `mission_questions` — 신규

| 컬럼 | 자료형 | 설명 |
| --- | --- | --- |
| `id` | binary(16) | 문항 ID |
| `mission_id` | binary(16) | 미션 ID |
| `question_type` | varchar(20) | `OX` / `SINGLE_CHOICE` / `TEXT` |
| `question` | text | 문항 내용 |
| `is_required` | boolean | 필수 응답 여부. 설문 완료 판정에 사용 |
| `sort_order` | int | 노출 순서 |
| `created_at` | datetime | 생성 시각 UTC |

- `UNIQUE(mission_id, sort_order)`, `FK mission_id → missions(id)`.
- 채점 여부는 문항 유형이 아니라 `missions.mission_type`이 정한다. 퀴즈의 `TEXT`는 채점하고 설문의 `TEXT`는 자유 응답이다.

### `mission_question_options` — 신규

선택지이자 정답 후보다.

| 컬럼 | 자료형 | 설명 |
| --- | --- | --- |
| `id` | binary(16) | 선택지 ID |
| `question_id` | binary(16) | 문항 ID |
| `label` | varchar(200) | 선택지 문구. `TEXT` 유형에서는 정답 후보 문자열 |
| `is_correct` | boolean | 퀴즈 채점용. 설문은 전부 false |
| `sort_order` | int | 노출 순서 |

- `UNIQUE(question_id, sort_order)`, `FK question_id → mission_questions(id)`.
- 유형별 사용:

| 문항 유형 | 행 수 | 사용자에게 | 정답 |
| --- | --- | --- | --- |
| `OX` | 2행 (O·X) | 보인다 | 한 행이 `is_correct` |
| `SINGLE_CHOICE` | N행 | 보인다 | 한 행이 `is_correct` |
| `TEXT` (퀴즈) | 정답 후보 N행 | **숨긴다** | 전부 `is_correct` |
| `TEXT` (설문) | 0행 | — | 없음 |

- 단답형 정답을 별도 컬럼이 아니라 이 테이블에 두면 복수 정답이 자연히 담긴다. `서울` `서울시` `서울특별시`를 3행으로 등록한다.
- API 응답에서 `is_correct`는 제외하고, `TEXT` 유형은 선택지 자체를 내려보내지 않는다.

### `mission_answers` — 신규

| 컬럼 | 자료형 | 설명 |
| --- | --- | --- |
| `id` | binary(16) | 응답 ID |
| `submission_id` | binary(16) | 미션 제출 건 ID |
| `question_id` | binary(16) | 문항 ID |
| `selected_option_id` | binary(16) NULL | 선택한 항목. `TEXT` 유형은 NULL |
| `text_answer` | varchar(500) NULL | 입력한 답변. 선택형은 NULL |
| `created_at` | datetime | 생성 시각 UTC |

- `UNIQUE(submission_id, question_id)` — 한 제출에서 같은 문항에 두 번 답할 수 없다.
- `CHECK` — `selected_option_id`와 `text_answer` 중 정확히 하나만 채워진다.
- 제출 건별로 저장한다. 퀴즈 오답 재도전이 무제한이라 제출 행이 여러 개 쌓이며, 오답 응답도 남으면 문항별 오답률을 볼 수 있다. 통계 조회에는 `is_completed = true` 조건을 더한다.
- 문항 유형과 응답 형태가 맞는지는 테이블이 달라 DB로 막을 수 없다. 애플리케이션에서 검증한다.

### 기존 테이블

| 테이블 | 무엇을 | 왜 |
| --- | --- | --- |
| `missions` | `url` 제거 | 설문을 서비스 내부에서 받기로 확정해 외부 링크가 필요 없다 |
| `missions` | `mission_type`에서 `CONSECUTIVE_ATTENDANCE` 제거 | 출석과 게임은 미션으로 관리하지 않는다. `SURVEY` / `QUIZ`만 남는다 |
| `mission_submissions` | `submitted_data` 제거 | 응답은 `mission_answers`가 담는다. 두 곳에 두면 어긋난다 |
| `mission_submissions` | `completion_guard` int NULL 추가 | 완료 행만 `1`, 오답 행은 NULL. `UNIQUE(user_id, mission_id, completion_guard)`로 완료 1회를 강제한다. `event_entries.single_entry_guard`와 같은 패턴이다 |

---

## 3. 응모권

만료일이 지급 건별로 달라 잔액의 단위가 월이 아니라 지급 건이 된다.

### `ticket_lots` — 신규

지급·반환 건별 잔여 수량과 만료일.

| 컬럼 | 자료형 | 설명 |
| --- | --- | --- |
| `id` | binary(16) | lot ID |
| `user_id` | binary(16) | 사용자 ID |
| `grant_ledger_id` | binary(16) | 이 lot을 만든 지급·반환 원장 행 |
| `lot_type` | varchar(20) | `GRANT`=일반 지급 / `REFUND`=반환 |
| `granted_quantity` | bigint | 최초 수량 |
| `remaining_quantity` | bigint | 현재 잔여 수량 |
| `expires_at` | datetime | 만료 시각 UTC |
| `status` | varchar(20) | `ACTIVE` / `EXHAUSTED`=소진 / `EXPIRED`=만료 |
| `created_at` | datetime | 생성 시각 UTC |
| `updated_at` | datetime | 수정 시각 UTC |

- `UNIQUE(grant_ledger_id)`, `FK user_id → users(id)`, `FK grant_ledger_id → ticket_ledger(id)`.
- `CHECK(remaining_quantity >= 0)`, `CHECK(remaining_quantity <= granted_quantity)`.
- `INDEX(user_id, expires_at)` — 차감 시 만료 임박 순으로 읽는다.
- 만료일은 유형에 따라 다르다. `GRANT`는 지급한 달 다음 1일 00:00 UTC, `REFUND`는 반환한 달의 다음 달이 끝난 뒤 1일 00:00 UTC다.
- 차감은 만료일이 이른 lot부터, 만료일이 같으면 먼저 지급된 lot부터 사용한다.
- 만료 판정은 `status`가 아니라 `expires_at`으로 한다. 배치가 늦어도 결과가 흔들리지 않는다. `status`는 만료 처리를 마쳤는지를 나타내는 플래그다.

### `ticket_recovery_targets` — 신규

부정 획득 회수의 결정과 실제 결과를 구분해 보존한다.

| 컬럼 | 자료형 | 설명 |
| --- | --- | --- |
| `id` | binary(16) | 회수 대상 ID |
| `abuse_case_id` | binary(16) | 근거가 된 검토 건 |
| `user_id` | binary(16) | 대상 사용자 ID |
| `grant_ledger_id` | binary(16) | 회수 대상 원본 지급 원장 행 |
| `target_quantity` | bigint | 회수 대상 수량. 관리자가 결정한 값 |
| `recovered_quantity` | bigint | 실제로 회수된 수량 |
| `status` | varchar(20) | `DECIDED`=결정만 / `PARTIAL`=일부 회수 / `COMPLETED`=완료 / `UNRECOVERABLE`=회수 불가 |
| `decided_by` | binary(16) | 결정한 관리자 ID |
| `decided_at` | datetime | 결정 시각 UTC |
| `completed_at` | datetime NULL | 회수 완료 시각 UTC |

- `UNIQUE(abuse_case_id, grant_ledger_id)` — 한 검토 건에서 같은 지급 건을 두 번 지정하지 않는다.
- `CHECK(recovered_quantity <= target_quantity)`.
- 한 검토 건이 여러 지급 건을 대상으로 지정할 수 있어 N:M이다. `ticket_ledger.related_ledger_id`는 1:1이라 담을 수 없다.
- 이미 만료됐거나 소진돼 회수할 수 없는 지급분도 행을 남기고 `UNRECOVERABLE`로 둔다. 결정 근거가 보존된다.

### 기존 테이블

| 테이블 | 무엇을 | 왜 |
| --- | --- | --- |
| `ticket_ledger` | `balance_after` bigint 추가 | 사용자 잔액의 기록값. 불일치가 생긴 정확한 행을 특정한다 |
| `ticket_ledger` | `seq` bigint 추가, `UNIQUE(user_id, seq)` | 동시 append를 DB가 거부해 lost update를 탐지가 아니라 차단한다 |
| `ticket_ledger` | `wallet_id` → `user_id`로 교체 | 잔액 단위가 월별 지갑에서 사용자로 바뀐다. 어느 lot에서 나갔는지는 `ticket_ledger_allocations`가 담는다 |
| `ticket_wallets` | 제거 | `ticket_lots`가 진짜 단위가 되면 월별 잔액은 파생값이다. 둘 다 잔액을 들면 어느 쪽이 진실인지 모호해진다 |
| `ticket_refund_jobs` | `reason_type`에서 `EVENT_SUSPENDED` 제거 | 일시 중단만으로는 반환하지 않는다 |
| `ticket_refund_jobs` | `target_validity_month` 제거 | 반환 시점 기준으로 계산하므로 미리 담을 값이 아니다 |
| `reward_claims` | `attendance_id` binary(16) NULL 추가 | 출석·연속 출석 보상의 근거를 가리킬 FK가 없다 |
| `reward_claims` | `attendance_streak_policy_id` binary(16) NULL 추가 | 연속 출석 보상이 어느 월 정책으로 판정됐는지 |
| `reward_claims` | `source_key`에 UNIQUE | 중복 지급 방지의 실제 방어선이다 |

`ticket_ledger_allocations`는 그대로 둔다. 차감이 어느 지급분에서 나왔는지를 기록하며, 부정 획득 회수가 요구하는 "지급 건별 회수 수량"의 근거가 된다.

---

## 4. 게임 — 추가 없음

`games`, `game_plays`, `user_game_stats`를 그대로 사용한다. 일일 보상 획득 기록은 `reward_claims`가 담는다.

| 용도 | 방법 |
| --- | --- |
| 사용자·게임·날짜별 1회 지급 | `reward_claims.source_key = GAME:{userId}:{gameId}:{date}` + UNIQUE |
| 보상 근거 | `reward_claims.game_play_id` (기존 컬럼) |

---

## 지급 사유별 멱등 키

중복 지급 방지는 `reward_claims.source_key`의 UNIQUE 하나로 처리한다.

| 사유 | 키 형식 |
| --- | --- |
| 기본 출석 | `ATTENDANCE:{userId}:{date}` |
| 연속 출석 단계 | `STREAK:{userId}:{yyyy-MM}:{days}` |
| 미션 | `MISSION:{userId}:{missionId}` |
| 게임 | `GAME:{userId}:{gameId}:{date}` |

응모 차감은 `event_entries.idempotency_key`와 `ticket_ledger.idempotency_key`가 담당한다.

---

## 주요 관계

```text
users 1 ──< attendances                  UNIQUE(user_id, attendance_date)
users 1 ──< ticket_lots                  INDEX(user_id, expires_at)
users 1 ──< ticket_ledger                UNIQUE(user_id, seq)

attendances 1 ──< reward_claims          기본 보상 1건 + 단계 도달일에 1건
attendance_streak_policies 1 ──< reward_claims

missions 1 ──< mission_questions 1 ──< mission_question_options
missions 1 ──< mission_submissions 1 ──< mission_answers
mission_questions 1 ──< mission_answers

ticket_ledger 1 ──1 ticket_lots          지급·반환 원장 행이 lot을 만든다
ticket_ledger 1 ──< ticket_ledger_allocations >── 1 ticket_ledger
abuse_cases 1 ──< ticket_recovery_targets >── 1 ticket_ledger
```

---

## 아직 정하지 않은 것

- `ticket_wallets`를 제거할지, 조회용 캐시로 남길지. 둘 다 잔액을 들면 진실이 둘이 된다.
- 단답형 퀴즈의 채점 관용도. 앞뒤 공백 제거와 대소문자 무시는 최소선이고, 모든 공백 제거까지 갈지 정해야 한다. 표기 변형은 정답 후보를 여러 개 등록해 푸는 쪽을 권한다.
- 연휴·특별일의 출석 보상 차등 지급. 공용 명세에 아직 없다. 도입하면 `reward_policies`에 우선순위 컬럼이 필요하다.
- 단계 번호와 일수의 오름차순 일치를 애플리케이션 어디에서 검증할지.
- `docs/03-database/reward-tables.md`와 이름 체계가 다르다. 그 문서는 단수형(`attendance`, `mission_quiz`), 기존 ERD와 이 문서는 복수형이다. 한쪽으로 맞춰야 한다.
