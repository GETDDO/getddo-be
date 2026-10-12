# ERD 검토 결과 — 2026-09-23

- **검토 대상**: `getddo-review-20260922.dbml` (UNIQUE 최종 정리 전 복사본)
- **기준 문서**: `getddo-spec` `001c3db` (PR #4 · 자동 발표·응모권 상한·KST 운영 기준 반영)
- **상태**: 검토 대기 — 담당자 확인 전

---

## 요약

| 심각도 | 건수 | 항목 |
|---|---|---|
| CRITICAL | 1 | 출석 KST 날짜 컬럼 부재 |
| HIGH | 2 | 제외 사유 코드 부재 · 누적 상한 동시성 |
| MEDIUM | 2 | 특별일 출석 정책 공백 · 원장 잔액 감사 수단 |
| LOW | 5 | 네이밍, 잔여 메모, 미사용 컬럼 등 |
| 별도 | — | UNIQUE 제약 일괄 정리 (파일 자체가 미완 상태로 표기) |

직전 검토(`getddo (1).dbml`)에서 지적한 21건 중 18건이 반영됐다. 아래는 남은 항목과 신규 확인 사항이다.

---

## 1. CRITICAL — 반드시 고쳐야 함

### 1-1. `attendances`에 KST 출석 날짜가 없다

**현재**

```dbml
Table attendances {
  id binary(16) [pk, not null, note: '출석 ID']
  users_id binary(16) [not null, note: '사용자 ID. 가상 사용자도 고정 ID 사용']
  created_at datetime [not null, note: '생성 시각 UTC']
}
```

**근거 — `00-requirements/functional-requirements.md` 「공통 시간 기준과 화면 표시」**

> 업무 기준일·기준월은 KST(Asia/Seoul)로 판단한다. … 이 업무 날짜는 KST 기준으로 저장하며, 실제 발생 시각을 저장하는 UTC 타임스탬프와 구분한다.
> 예를 들어 9월 22일 15:30 UTC의 출석은 한국 시간 9월 23일 00:30이므로 9월 23일 출석이다.

**문제**

명세가 직접 예시로 든 "9월 23일 출석"을 담을 컬럼이 출석 본체에 없다. 주변 테이블은 모두 KST date를 갖고 있다.

| 테이블 | KST date 컬럼 |
|---|---|
| `attendance_reward_claims` | `reward_date` ✅ |
| `attendance_streaks` | `last_attendance_date` ✅ |
| `game_reward_claims` | `reward_date` ✅ |
| `attendances` | **없음** ❌ |

파급 효과는 세 가지다.

1. **하루 1회 중복 방지를 DB에서 못 건다.** `attendance_reward_claims.source_key`가 보상 중복은 막지만, 출석 **기록** 자체의 중복은 막지 못한다. 동일 사용자·동일 KST 날짜로 `attendances` 행이 여러 개 생길 수 있다.
2. **연속 출석 계산에서 인덱스를 못 탄다.** `created_at`(UTC)을 매 조회마다 KST로 변환해 GROUP BY 해야 한다.
3. **어뷰징 검토에서 "그날 출석"을 조회할 수단이 없다.** `abuse_cases.attendance_id`로 역참조는 되지만 날짜 기준 집계가 불가능하다.

**수정안**

```dbml
Table attendances {
  id binary(16) [pk, not null, note: '출석 ID']
  user_id binary(16) [not null, note: '사용자 ID. 가상 사용자도 고정 ID 사용']
  attendance_date date [not null, note: '출석 인정 날짜 KST. created_at을 KST로 변환한 업무일']
  created_at datetime [not null, note: '생성 시각 UTC']

  indexes {
    (user_id, attendance_date) [unique]
  }
}
```

---

## 2. HIGH — 병합 전 수정 권장

### 2-1. 제외 사유가 자유 텍스트라 반환 분기를 결정할 수 없다

**확정 정책**

- 어뷰징으로 제외 → 응모권 **반환하지 않음**
- 자격 미달로 제외 → 응모권 **반환함**
- 한 건이라도 어뷰징이면 해당 사용자분은 전부 미반환

**현재**

```dbml
Table event_participants {
  eligibility_status participant_eligibility_status  // ELIGIBLE / EXCLUDED
  exclusion_reason text [note: '추첨 대상 제외 사유']   // 자유 텍스트
}

Enum ticket_refund_reason {
  EVENT_CANCELED
  PARTICIPANT_EXCLUDED   // 어뷰징/자격미달 구분 없음
}
```

**문제**

`EXCLUDED` 하나에 두 경우가 뭉쳐 있다. 반환 작업(`ticket_refund_jobs`)을 생성할지 말지를 **자유 텍스트 파싱으로 판단**하게 된다. 정책이 명확히 갈리는데 스키마가 그 구분을 담지 못한다.

**수정안**

```dbml
Enum participant_exclusion_reason {
  ABUSE        // 어뷰징 확인. 응모권 반환하지 않음
  INELIGIBLE   // 자격 미달. 응모권 반환
}

Table event_participants {
  // ...
  exclusion_reason_code participant_exclusion_reason [note: 'EXCLUDED일 때 필수. ABUSE는 반환 대상에서 제외']
  exclusion_reason text [note: '제외 사유 상세 설명']
}
```

`ticket_refund_jobs.reason_type`은 그대로 두고, `ABUSE`인 참가자에 대해서는 job 자체를 생성하지 않는다.

---

### 2-2. 이벤트별 누적 5장 상한을 동시 요청에서 지킬 수 없다

**근거 — ADR-010**

> 일반 가중치 적용 이벤트는 사용자당 해당 이벤트에서 추가 응모를 포함해 응모권을 누적 최대 5장까지 사용할 수 있다. **1회 요청당 제한이 아니라 이벤트별 사용자 누적 사용 상한이다.**

**현재**

`events.max_tickets_per_user`는 반영됐다.

```dbml
max_tickets_per_user int [note: '사용자별 이벤트 누적 상한. 일반 가중치 5, 미적용 1, 월말 소진용 NULL. 미사용 이벤트는 응모권 0장']
```

그러나 검증 기준값이 `event_entries`의 집계뿐이다.

```sql
SELECT SUM(deducted_ticket_count) FROM event_entries
WHERE event_id = ? AND user_id = ? AND status = 'ACCEPTED'
```

**문제**

동시에 3장 요청 2건이 들어오면 두 트랜잭션 모두 "현재 0장 + 3 ≤ 5"를 통과하고 **6장**이 차감된다. `event_entries`에는 잠글 대상 행이 존재하지 않는다(아직 INSERT 전이므로). 갭 락에 의존하는 방식은 격리 수준과 인덱스 구성에 따라 동작이 달라진다.

**수정안**

`event_participants`에 누적 컬럼을 두고 조건부 UPDATE로 원자적 검증한다.

```dbml
Table event_participants {
  // ...
  used_ticket_count bigint [not null, default: 0, note: '이벤트별 누적 사용 응모권 수. max_tickets_per_user 검증 기준']
}
```

```sql
UPDATE event_participants
   SET used_ticket_count = used_ticket_count + :requested
 WHERE id = :participant_id
   AND used_ticket_count + :requested <= :max_tickets_per_user
```

affected rows가 0이면 상한 초과로 거절한다. `SELECT ... FOR UPDATE` 후 SUM 집계로도 가능하지만, 매 응모마다 전체 응모 내역을 집계하게 되므로 비용과 명확성 모두 위 방식이 낫다.

`max_tickets_per_user`가 NULL(월말 소진용)이면 조건절을 생략한다.

---

## 3. MEDIUM — 결정 필요

### 3-1. 특별일 출석 보상 정책에 공백이 생긴다

**요구사항**

> 지금은 출석이 1일 1응모권이지만 연휴나 특별한 날은 10개 뭐 이런 식으로 주게

**현재 구조**

```dbml
Table reward_policies {
  reward_type reward_type          // ATTENDANCE / MISSION / GAME
  reward_ticket_count int
  effective_from datetime [not null]
  effective_until datetime          // nullable
}
```

**문제**

"9월 30일만 10장"을 표현하려고 `effective_from = 9/30`, `effective_until = 10/1`로 닫으면, **10월 1일 이후에 적용할 정책이 없다.** 기본 정책(1장)은 `effective_until`이 9월 30일로 이미 닫혀 있거나, 닫지 않았다면 특별일과 기간이 겹쳐 어느 쪽을 쓸지 모호해진다.

비교 대상으로, 연속 출석 정책은 이 문제가 없다.

```dbml
Table attendance_streak_policy_sets {
  effective_month date [not null, note: '적용 시작월의 1일 KST. 다음 정책 시작 전까지 유지']
  Note: '대상 월 이하 최신 시작월의 묶음을 선택, 그 묶음의 단계만 사용'
}
```

종료 시각 없이 "가장 최근 시작"만으로 선택하므로 공백이 구조적으로 발생하지 않는다.

**선택지**

| 안 | 내용 | 장단 |
|---|---|---|
| A | `reward_policies`도 `effective_until`을 없애고 "적용일 이하 최신 정책" 방식으로 통일 | 공백 없음. 하지만 하루짜리 특별일을 표현하려면 다음 날 복귀 정책을 반드시 함께 등록해야 함 |
| B | 기본 정책과 별개로 `attendance_date_overrides(date, ticket_count)` 2층 구조 | 특별일 표현이 직관적. 테이블 1개 추가 |
| C | 현행 유지 + "정책 종료 시 후속 정책 동시 등록"을 운영 규칙으로 명문화 | 스키마 변경 없음. 운영 실수 시 보상 지급이 멈춤 |

연휴가 연속 며칠인 경우와 특별일이 겹치는 경우를 고려하면 **B**가 안전하다. 담당자 결정 필요.

---

### 3-2. 원장만으로 잔액 정합성을 검증할 수 없다

**현재**

```dbml
Table ticket_wallets {
  balance bigint [not null, default: 0, note: '현재 잔액. 0 이상']
  version bigint [not null, default: 0, note: '동시 변경 검증용 버전']
}
```

`version`은 낙관적 락으로 동시 변경 충돌은 잡는다. 다만 **감사 수단은 아니다.**

**문제**

`ticket_wallets.balance`와 `SUM(ticket_ledger.quantity)`가 어긋났을 때, 어느 거래에서 틀어졌는지 특정할 방법이 없다. 전체 재집계로 차이 총액은 알 수 있지만 원인 시점은 찾지 못한다.

`ticket_ledger`에 `balance_after`(거래 직후 잔액)와 `seq`(지갑별 순번)가 있으면 3중 대조가 가능하다.

1. 원장 각 행: `balance_after` = 직전 행의 `balance_after` + `quantity`
2. 지갑: `ticket_wallets.balance` = 마지막 행의 `balance_after`
3. 전체: `SUM(quantity)` = `balance`

**판단**

`ticket_ledger_allocations`로 출처별 재구성은 가능하므로 치명적이지는 않다. 의도한 트레이드오프라면 현행 유지해도 된다. 다만 **응모권이 금전에 준하는 자산**이고 회수·정정·반환 경로가 여럿이라, 운영 중 불일치가 발생하면 조사 비용이 크다.

---

## 4. LOW — 정리 권장

### 4-1. `users_id` 네이밍 불일치

`attendances.users_id`, `attendance_streaks.users_id` 두 곳만 복수형이다. 나머지 30여 개 FK는 모두 `user_id`다. JPA 매핑에서 이 둘만 `@Column(name = "users_id")` 예외 처리가 필요해진다.

→ `user_id`로 통일.

---

### 4-2. `draw_candidates.input_order` — 유지해야 한다

```dbml
input_order int [not null, note: '추첨 후보 입력 순서. 원본 메모: 필요성 확인']
```

**필요하다.** 과제 원문의 "다시 추첨해도 같은 결과"는 후보 집합의 순서가 결정적이어야 성립한다. 정렬 기준 컬럼이 없으면 DB가 반환하는 순서에 의존하게 되고, 이는 실행 계획에 따라 바뀔 수 있다.

→ 메모만 제거하고 컬럼 유지.

---

### 4-3. `canceled_at` / `detected_at`을 `created_at`으로 합치지 말 것

```dbml
award_cancellations.canceled_at  // 원본 메모: created_at으로 수정 가능
abuse_cases.detected_at          // 원본 메모: created_at으로 수정 가능
```

`abuse_cases`는 세 시각이 모두 다르다.

| 컬럼 | 의미 |
|---|---|
| `occurred_at` | 탐지 대상 행위가 실제 발생한 시각 |
| `detected_at` | 시스템이 탐지한 시각 |
| (행 생성) | 레코드가 저장된 시각 |

소급 탐지(과거 행위를 나중에 배치로 탐지)를 하면 셋이 전부 갈라진다. `canceled_at`도 마찬가지로 "취소 처리가 유효해진 업무 시각"이며 행 생성 시각과 구분된다.

→ 메모 제거하고 현행 유지.

---

### 4-4. `missions.url` 용도 불명확

```dbml
url varchar(255) [note: '선택적 보조 링크. 실제 문항과 답변은 서비스 내부 처리']
```

문항·선택지·답변이 모두 내부 테이블로 들어온 이상 이 컬럼의 용도가 특정되지 않는다. 실제 사용처가 없으면 제거하는 편이 낫다. 브랜드 퀴즈의 참고 페이지 같은 용도가 확정되면 그때 추가한다.

---

### 4-5. `banners.event_id`만 `ON DELETE CASCADE`

**근거 — `02-domain/event.md`**

> 이벤트 삭제 시 해당 이벤트에 연결된 배너도 함께 삭제한다.

배너는 이력이 아니라 노출 설정이므로 함께 삭제하는 것이 맞다. 나머지 FK는 모두 `RESTRICT`를 유지한다(응모·원장·추첨 결과는 이력이므로 이벤트 삭제로 사라지면 안 된다).

DBML에는 삭제 동작 표기가 없으므로 마이그레이션 작성 시 반영한다.

---

## 5. UNIQUE 제약 목록

파일 상단에 `UNIQUE 최종 정리 전`으로 표기된 항목이다. 아래를 일괄 적용한다.

### 5-1. 멱등 키 — 단일 컬럼 UNIQUE

| 테이블 | 컬럼 |
|---|---|
| `ticket_ledger` | `idempotency_key` |
| `event_entries` | `idempotency_key` |
| `mission_submissions` | `idempotency_key` |
| `draw_runs` | `idempotency_key` |
| `notification_jobs` | `occurrence_key` |
| `abuse_cases` | `detection_key` |
| `game_plays` | `play_token` |

### 5-2. 보상 중복 방지 — `(user_id, source_key)`

- `attendance_reward_claims`
- `reward_claims`
- `game_reward_claims`

### 5-3. 1행 보장

| 테이블 | UNIQUE | 비고 |
|---|---|---|
| `attendances` | `(user_id, attendance_date)` | 1-1 수정 선행 필요 |
| `attendance_streaks` | `(user_id, streak_month)` | |
| `attendance_streak_policy_sets` | `(effective_month)` | 월당 정책 묶음 1개 |
| `attendance_streak_policies` | `(policy_set_id, milestone_days)` | 묶음 내 단계 중복 금지 |
| `ticket_wallets` | `(user_id, expiry_month)` | 만료월당 지갑 1개 |
| `event_participants` | `(event_id, user_id)` | |
| `event_entries` | `(event_id, user_id, single_entry_guard)` | NULL 허용 부분 유니크 |
| `publications` | `(event_id)` | ADR-009 "최초 발표는 한 번만 반영" |
| `draw_runs` | `(event_id, run_number)` | |
| `draw_results` | `(draw_run_id, event_prize_id, slot_number)` | |
| `current_awards` | `(event_id, event_prize_id, slot_number)` | |
| `event_prizes` | `(event_id, prize_rank)` | |
| `survey_answers` | `(submission_id, survey_question_id)` | 문항당 1응답 |
| `quiz_question_options` | `(quiz_question_id, display_order)` | |
| `survey_question_options` | `(survey_question_id, display_order)` | |
| `banners` | `(display_order)` | 1~5 범위 CHECK와 함께 |
| `games` | `(code)` | |
| `users` | `(email)`, `(phone_num)` | NULL 허용 |

### 5-4. CHECK 제약 (참고)

- `event_prizes.winner_count >= 1`
- `ticket_ledger.quantity <> 0`
- `ticket_wallets.balance >= 0`
- `banners.display_order BETWEEN 1 AND 5`
- `attendance_streak_policies.milestone_days BETWEEN 1 AND 28`

---

## 6. 스펙 쪽 확인 필요

### 6-1. 응모권 차감 순서는 스펙이 정하지 않았다 — 구현이 정해야 한다

**`02-domain/ticket.md` 확정 문장**

> 응모권의 유효기간은 월별로 구분하며, **별도의 차감 순서는 두지 않는다.**
> 일반 지급분과 반환분의 만료일이 다르므로 지급 건별 잔여 수량·만료일과 차감 출처를 보존한다. **별도의 차감 우선순위는 이번 결정으로 추가하지 않는다.**

**문제**

스펙이 순서를 규정하지 않았으므로 구현이 정한다. 다만 순서를 정하지 않으면 만료 임박분을 남긴 채 장기분을 써서 사용자가 손해를 본다.

**구현 기본값을 `expires_at ASC`(만료 임박 지갑 우선)로 하고 `docs/04-decisions`에 ADR로 남긴다.** 공용 정책 변경이 아니라 백엔드 구현 결정이므로 BE 저장소에서 확정할 수 있다.

> 로컬 브랜치 `docs/ticket-deduction-order`(커밋 `c2c9b16`)는 이 두 문장을 FIFO로 바꾸려던 제안이다. 머지된 스펙이 "우선순위를 추가하지 않는다"로 확정했으므로 **push하지 않고 폐기한다.**

**참고 — 지갑 구조상 tie-break는 애초에 불필요하다**

`ticket_wallets`는 **만료일별로 지갑을 묶는다.** 만료일이 같으면 같은 지갑의 단일 `balance`로 합산되므로, 지급 순서 구분이 존재하지 않는다.

```dbml
Table ticket_wallets {
  expiry_month date [not null, note: '사용 가능한 마지막 KST 월의 1일. 지급월이 아니라 만료 묶음 구분값']
  balance bigint [not null, default: 0]
  Note: '사용자별 만료일별 응모권 지갑. 같은 지갑의 모든 입금은 동일 만료 시각'
}
```

만료일이 같으면 같은 지갑으로 합쳐지므로 "먼저 지급된 건" 같은 2차 정렬 기준은 표현할 수 없고, 표현할 필요도 없다. 만료 시점이 같으면 어느 쪽을 먼저 쓰든 사용자 손익과 만료 결과가 동일하다.

출처별 추적이 필요한 경우(어뷰징 회수)는 `ticket_ledger_allocations.original_grant_id`로 해결된다.

---

## 7. 직전 검토 대비 해결된 항목

기록용. 조치 불필요.

| 항목 | 상태 |
|---|---|
| `reward_policies.day_timezone` UTC 회귀 | `Asia/Seoul` 복구 |
| `attendance_streaks` KST 회귀 | `streak_month`, `last_attendance_date` KST 복구 |
| 연속 출석 정책 버전 관리 부재 | `attendance_streak_policy_sets` 신설 (묶음 단위 교체) |
| 단계 수·보상량 고정 | `milestone_days 1~28`, 단계 수 가변으로 관리자 수정 가능 |
| `mission_quizzes` 껍데기 | 문항 테이블로 실체화 (OX/단일선택/단답형) |
| 퀴즈 선택지·정답 구조 부재 | `quiz_question_options`(`is_correct`) + `quiz_accepted_answers` |
| 퀴즈 재도전·진행 추적 부재 | `quiz_answers` + `quiz_question_progress` |
| 설문 문항 구조 부재 | `survey_questions` + `survey_question_options` + `survey_answers` |
| `mission_submissions.submitted_data` JSON | 제거, 정규화 테이블로 대체 |
| `missions.mission_type`의 `CONSECUTIVE_ATTENDANCE` | 제거 (SURVEY/QUIZ만) |
| 출석·게임 보상 지급 이력 부재 | `attendance_reward_claims`, `game_reward_claims` 신설 |
| 회수 결정 기록 부재 | `ticket_recovery_targets` 신설 |
| 지급 건별 잔여·만료 추적 부재 | `ticket_wallets` + `ticket_ledger_allocations` |
| `publications.published_by` NOT NULL | nullable + "시스템 자동 발표는 NULL" (ADR-009) |
| `max_tickets_per_entry` 의미 불일치 | `max_tickets_per_user` 누적 상한으로 변경 (ADR-010) |
| `notifications.read_at` | `is_read` boolean으로 단순화 |
| `abuse_cases.event_id` NOT NULL | nullable (출석·미션·게임 검토) |
| `EVENT_SUSPENDED` 등 ENUM 정리 | `SUSPENDED`, `NO_ENTRANTS`, `NO_ELIGIBLE_ENTRANTS`, `REDRAWING` |

---

## 조치 순서

1. **1-1** `attendances.attendance_date` 추가 — 컬럼 1개
2. **2-1** `participant_exclusion_reason` enum 추가 — enum 1개 + 컬럼 1개
3. **2-2** `event_participants.used_ticket_count` 추가 — 컬럼 1개
4. **3-1** 특별일 출석 정책 방식 결정 (A/B/C)
5. **3-2** `balance_after`·`seq` 도입 여부 결정
6. **5장** UNIQUE·CHECK 일괄 적용
7. **4장** 네이밍·메모·미사용 컬럼 정리
