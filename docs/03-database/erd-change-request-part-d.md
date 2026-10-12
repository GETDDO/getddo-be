# ERD 변경 요청 — 윤태형

기준 파일: `getddo-review-20260922.dbml`. 아래는 원본에 반영할 변경사항이다.
각 변경의 사유를 함께 기록했다.

담당 범위: `games` `game_plays` `user_game_stats` `game_reward_claims` `events` `event_entries` `event_participants` `ticket_wallets` `ticket_ledger` `ticket_ledger_allocations` `ticket_refund_jobs` `ticket_recovery_targets` `reward_policies`

## 1. 컬럼 변경

| 테이블 | 작업 | 컬럼 | 타입 · NULL 허용 | 설명 · 변경 사유 |
| --- | --- | --- | --- | --- |
| `event_participants` | 추가 | `used_ticket_count` | `bigint`, NOT NULL, 기본값 `0` | 이벤트별 누적 사용 응모권 수. ADR-010의 "사용자당 이벤트별 누적 5장"을 동시 요청에서 검증할 기준값이 없다. 지금은 `event_entries` 집계뿐이라 3장씩 동시 2건이면 둘 다 통과해 6장이 나간다. |
| `event_participants` | 추가 | `exclusion_reason_code` | `participant_exclusion_reason`, NULL 허용 | `EXCLUDED`일 때 필수. 어뷰징 제외는 응모권을 반환하지 않고 자격 미달은 반환하는데, 지금은 `exclusion_reason` 자유 텍스트뿐이라 반환 여부를 텍스트 파싱으로 판단하게 된다. |
| `event_participants` | 설명 변경 | `exclusion_reason` | `text`, NULL 허용 유지 | 코드가 분리되므로 "추첨 대상 제외 사유" → "추첨 대상 제외 사유 상세"로 용도를 명확히 한다. |
| `ticket_recovery_targets` | 추가 | `active_guard` | `binary(16)` 생성 컬럼, NULL 허용 | `status='ACTIVE'`이면 `original_grant_id`, 아니면 `NULL`. 한 검토 건이 같은 지급분에 유효 결정을 두 개 만드는 것을 막는다. `ticket.md`의 "같은 지급 건에서 이미 회수한 수량을 다시 회수하지 않는다"를 DB로 보장한다. |
| `ticket_ledger` | 추가 | `balance_after` | `bigint`, NOT NULL | 이 거래 직후의 지갑 잔액. `ticket.md`의 "이력 합계와 현재 보유 응모권 수의 일치를 보장한다"를 검증할 수단이 없다. 지금은 불일치가 생겨도 차액만 알고 어느 거래에서 틀어졌는지 찾을 수 없다. |
| `ticket_ledger` | 추가 | `wallet_version` | `bigint`, NOT NULL | 기록 시점 `ticket_wallets.version` 값. 지갑별 거래 순서를 고정한다. 낙관적 락 컬럼이 이미 갱신마다 증가하므로 별도 채번이 필요 없다. |
| `ticket_ledger` | 추가 | `user_id` | `binary(16)`, NOT NULL | 사용자 이력 조회가 매번 `ticket_wallets`를 조인해야 한다. `ticket.md`의 "보유 응모권 수와 지급·차감 이력을 조회한다"가 기본 화면이므로 직접 참조를 둔다. 7절 복합 FK로 지갑 소유자 일치도 함께 보장한다. |
| `ticket_refund_jobs` | 삭제 | `lease_until` | 기존 `datetime`, NULL 허용 | 여러 워커가 큐를 나눠 가질 때 쓰는 선점 컬럼이다. 이 테이블의 Note와 `event.md`가 동기 처리를 명시하므로 선점할 워커가 없다. |
| `ticket_refund_jobs` | 삭제 | `next_attempt_at` | 기존 `datetime`, NULL 허용 | 예약 재시도를 집어가는 워커가 없다. 실패하면 관리자가 취소를 다시 요청하며, 그 기록은 `attempt_count`와 `last_error`로 남는다. |
| `user_game_stats` | 추가 | `created_at` | `datetime`, NOT NULL | 유일하게 `updated_at`만 있고 생성 시각이 없다. 최초 유효 플레이 시점을 알 수 없고 공통 엔티티 규약에서도 벗어난다. |
| `reward_policies` | 삭제 | `conditions` | 기존 `json`, NOT NULL | 확정된 조건 중 여기 담을 내용이 없다. 미션 완료는 문항 테이블과 서버 고정 규칙으로 판정하고, 게임은 ADR-005가 "유효 플레이 기준, 차등 지급 없음"으로 닫았다. |
| `reward_policies` | 삭제 | `day_timezone` | 기존 `varchar(50)`, NOT NULL | FR「공통 시간 기준」이 전역 KST를 확정했다. 정책마다 시간대를 저장할 필요가 없다. 시각 저장은 UTC를 유지한다. |
| `reward_policies` | 설명 변경 | `effective_until` | `datetime`, NULL 허용 유지 | 교체 시 새 정책의 `effective_from`과 같은 시각으로 닫는다는 규칙을 기록한다. 변경 이력은 `audit_logs`로 남기며 `updated_at`은 추가하지 않는다. |

생성 컬럼 DDL은 다음과 같다. 원본의 `event_entries.single_entry_guard`와 같은 패턴이다.

```sql
ALTER TABLE ticket_recovery_targets
  ADD COLUMN active_guard BINARY(16)
    AS (CASE WHEN status = 'ACTIVE' THEN original_grant_id END) STORED;
```

## 2. 컬럼 이름 변경 — 공통 엔티티 규약

행 생성 시각을 `created_at`으로 통일해 `@MappedSuperclass` 기반 공통 엔티티와 감사(auditing) 설정을 재사용한다. 아래는 업무 시각과 행 생성 시각이 일치하는 컬럼이다.

| 테이블 | 변경 전 | 변경 후 | 확인 근거 |
| --- | --- | --- | --- |
| `ticket_refund_jobs` | `requested_at` | `created_at` | 반환 요청을 받는 시점에 행을 만든다. |
| `ticket_recovery_targets` | `decided_at` | `created_at` | 관리자가 회수를 결정하는 시점에 행을 만든다. |
| `event_participants` | `first_accepted_at` | `created_at` | 최초 응모가 접수될 때 행을 만든다. |
| `game_plays` | `started_at` | `created_at` | 플레이를 시작할 때 행을 만든다. `completed_at`은 유지한다. |
| `event_entries` | `processed_at` | `created_at` | 처리가 끝난 시점에 행을 확정한다. |

`event_entries`는 시각이 세 개이고 의미가 서로 다르므로 `requested_at`과 `accepted_at`은 바꾸지 않는다.

| 컬럼 | 의미 |
| --- | --- |
| `requested_at` | 요청이 서버에 도착한 시각 |
| `accepted_at` | 접수가 확정된 시각. 마감 판정 기준이며 거절 건은 `NULL` |
| `created_at` | 처리를 마치고 행을 확정한 시각 (기존 `processed_at`) |

> `entry.md` — 접수 완료 시점은 응모 기록·응모권 차감·차감 이력이 하나의 DB 트랜잭션으로 확정된 시점이다. 서버 검증 통과나 성공 응답 전달을 접수 완료로 보지 않는다.

`ticket_ledger_allocations`에는 시각 컬럼이 없다. 원장에 종속된 배분 행이라 `ticket_ledger.created_at`으로 추적할 수 있으므로 이번에는 추가하지 않는다. 공통 엔티티를 모든 테이블에 강제한다면 함께 검토한다.

### 참고 — 공통 엔티티를 둘로 나누는 안

담당 범위의 절반이 수정되지 않는 이력 테이블이다.

> `ticket.md` — 응모권 이력은 수정·삭제 없이 누적 기록한다.

| | 테이블 |
| --- | --- |
| 수정 없음 | `ticket_ledger` `ticket_ledger_allocations` `event_entries` `game_reward_claims` |
| 수정 있음 | `ticket_wallets` `event_participants` `events` `games` `ticket_refund_jobs` `ticket_recovery_targets` `user_game_stats` |

이력 테이블에 `updated_at`을 두면 값이 항상 `created_at`과 같아 "수정된 행"과 구분되지 않는다. 생성 시각만 갖는 상위 클래스와 수정 시각까지 갖는 상위 클래스를 나누면 감사 설정은 그대로 두고 구분할 수 있다. 이 저장소의 구현 관례이므로 ERD 반영 대상은 아니다.

## 3. Enum 변경

| Enum | 작업 | 값 | 변경 사유 |
| --- | --- | --- | --- |
| `participant_exclusion_reason` | 신규 | `ABUSE`, `INELIGIBLE` | `ABUSE`는 응모권을 반환하지 않고 `INELIGIBLE`은 반환한다. `event_participants.exclusion_reason_code`에서 사용한다. |

## 4. 테이블 설명 변경

| 테이블 | 변경 사유 |
| --- | --- |
| `ticket_wallets` | `status`는 만료 배치의 처리 결과 표시이므로, 배치가 지연되면 `ACTIVE`인데 `expires_at`이 지난 지갑이 존재한다. 사용 가능 잔액 조회 기준을 `expires_at > NOW() AND balance > 0`으로 명시한다. |
| `ticket_ledger_allocations` | 현재는 `GRANT`의 최초 수량을 원장에, `REFUND`는 자기 배분 행에 보존해 잔여 계산식이 거래 유형마다 다르다. 분기를 빠뜨리면 `GRANT` 잔여가 원금만큼 과소 계산된다. `GRANT`도 자기 배분 행을 만들어 계산식을 하나로 통일한다. |
| `event_participants` | 누적 사용량을 조건부 `UPDATE`로 원자적으로 검증한다는 점을 기록한다. |
| `ticket_ledger` | `balance_after`와 `wallet_version`이 성립하려면 원장 행 하나당 지갑 `UPDATE`가 정확히 한 번이어야 한다. 한 트랜잭션에서 같은 지갑에 두 행을 쓸 때(반환과 회수) 갱신을 합치면 `version`이 한 번만 올라 순서가 겹친다. |
| `ticket_refund_jobs` | 동기 처리임을 명확히 하고, 재시도가 관리자의 재요청으로만 발생한다는 점을 기록한다. |
| `reward_policies` | 정책 교체·조회 규칙을 DB 제약 대신 문서로 고정한다. 열린 정책이 둘이 되어도 `effective_from` 역순 조회로 최신 한 건을 쓰고, 0건은 설정 오류로 처리한다. |

변경할 Note 본문은 다음과 같다.

```
ticket_wallets
  사용자별 만료일별 응모권 지갑. 같은 지갑의 모든 입금은 동일 만료 시각.
  status는 만료 배치의 처리 결과 표시이므로, 사용 가능 잔액 조회는
  expires_at > NOW() AND balance > 0을 기준으로 한다

ticket_ledger_allocations
  GRANT와 REFUND 모두 생성 시 자기 입금에 대한 양수 배분 행을 만든다.
  사용·만료·회수는 해당 입금 출처에 음수 배분.
  입금 X의 잔여 = SUM(quantity) WHERE source_credit_ledger_id = X (거래 유형 무관 단일 계산식).
  최초 지급 G의 회수 가능량 = SUM(quantity) WHERE original_grant_id = G.
  반환은 원래 SPEND를 undo하지 않고 새 REFUND 입금을 생성

event_participants
  이벤트 응모자. 누적 사용량은 조건부 UPDATE로 상한을 원자적으로 검증한다

ticket_ledger
  응모권 거래 원장. 수정·삭제 없이 누적 기록한다.
  balance_after는 이 거래 직후의 지갑 잔액이며, wallet_version은 기록 시점의
  지갑 version이다. 원장 행 하나당 지갑 UPDATE를 정확히 한 번 수행해
  wallet_version이 지갑별 거래 순서와 1:1로 대응하도록 한다.
  정합성은 세 가지로 대조한다.
    balance_after[n] = balance_after[n-1] + quantity[n]
    ticket_wallets.balance = 지갑의 마지막 balance_after
    SUM(quantity) = ticket_wallets.balance

ticket_refund_jobs
  취소·반환 동기 처리 및 재시도 기록. 취소 성공 후 반환만 비동기로 대기시키지 않는다.
  실패하면 취소와 반환을 둘 다 미완료로 두고, 재시도는 관리자의 재요청으로만
  발생하며 attempt_count와 last_error에 누적한다

reward_policies
  보상 정책. 교체 시 이전 정책의 effective_until을 새 정책의 effective_from과
  같은 시각으로 닫는다. 조회는 effective_from 역순으로 정렬해 한 건만 사용하며,
  0건은 설정 오류이므로 에러로 처리한다
```

## 5. UNIQUE 추가 — 총 14개

괄호 안에 여러 컬럼이 있으면 조합 전체에 적용한다.

| 테이블 | UNIQUE 컬럼 | 추가 사유 |
| --- | --- | --- |
| `games` | `(code)` | 게임 구분 코드가 중복 등록되는 것을 막는다. |
| `game_plays` | `(play_token)` | 서버가 발급한 플레이 식별 토큰으로 같은 플레이의 중복 제출을 막는다. |
| `game_plays` | `(id, user_id, game_id)` | 7절 복합 FK의 참조 대상. 보상 지급이 다른 사용자의 플레이를 가리키는 것을 막는다. |
| `game_reward_claims` | `(user_id, source_key)` | 게임과 KST 날짜로 식별해 사용자별·게임별 하루 1회 지급을 보장한다. |
| `event_entries` | `(idempotency_key)` | 동일 요청이 재전송돼도 응모가 한 번만 접수되도록 한다. |
| `event_entries` | `(event_id, user_id, single_entry_guard)` | 1회 제한 이벤트에서 성공 건이 하나만 남도록 한다. 거절 건은 `NULL`이라 제약을 받지 않는다. |
| `event_participants` | `(event_id, user_id)` | 사용자·이벤트 조합당 응모자 행이 하나만 존재하도록 한다. |
| `event_participants` | `(id, event_id, user_id)` | 7절 복합 FK의 참조 대상. 응모가 다른 이벤트의 참가자를 가리키는 것을 막는다. |
| `ticket_wallets` | `(user_id, expiry_month)` | 사용자별 만료월당 지갑이 하나만 존재하도록 한다. |
| `ticket_wallets` | `(id, user_id)` | 7절 복합 FK의 참조 대상. 원장이 다른 사용자의 지갑을 가리키는 것을 막는다. |
| `ticket_ledger` | `(idempotency_key)` | 동일 요청이 재전송돼도 거래가 한 번만 기록되도록 한다. |
| `ticket_ledger` | `(wallet_id, wallet_version)` | 지갑별 거래 순서가 겹치지 않도록 한다. 원장 행 하나당 지갑 갱신 한 번이라는 규칙을 DB로 보장한다. |
| `ticket_refund_jobs` | `(spend_ledger_id)` | `ticket.md`의 "동일 차감 건의 반환 요청이 반복돼도 중복 반환하지 않는다"를 보장한다. |
| `ticket_recovery_targets` | `(abuse_case_id, active_guard)` | 한 검토 건이 같은 지급분에 유효 결정을 두 개 만드는 것을 막는다. |

`events`와 `reward_policies`에는 UNIQUE를 추가하지 않는다.

## 6. 일반 인덱스 추가

| 테이블 | 인덱스 컬럼 | 추가 사유 |
| --- | --- | --- |
| `reward_policies` | `(reward_type, effective_from)` | 적용 시점 기준으로 정책을 역순 조회하는 경로를 인덱스로 받는다. |
| `ticket_ledger` | `(user_id, created_at)` | 사용자 응모권 이력 화면의 기본 조회 경로다. |

## 7. 복합 FK 추가 — DBML 미지원

DBML로 표현할 수 없으므로 마이그레이션에서 적용한다. 단일 FK는 참조 대상의 존재만 확인하므로, 다른 사용자·다른 이벤트의 행을 가리키는 조합이 그대로 통과한다.

```sql
ALTER TABLE game_reward_claims
  ADD FOREIGN KEY (game_play_id, user_id, game_id)
  REFERENCES game_plays (id, user_id, game_id);

ALTER TABLE event_entries
  ADD FOREIGN KEY (participant_id, event_id, user_id)
  REFERENCES event_participants (id, event_id, user_id);

ALTER TABLE ticket_ledger
  ADD FOREIGN KEY (wallet_id, user_id)
  REFERENCES ticket_wallets (id, user_id);
```

## 8. CHECK 추가 — DBML 미지원

```sql
ALTER TABLE ticket_ledger ADD CONSTRAINT chk_ledger_sign CHECK (
  (transaction_type IN ('GRANT','REFUND')          AND quantity > 0) OR
  (transaction_type IN ('SPEND','EXPIRE','REVOKE') AND quantity < 0) OR
  (transaction_type = 'CORRECTION'                 AND quantity <> 0)
);

ALTER TABLE event_participants ADD CONSTRAINT chk_participant_exclusion CHECK (
  (eligibility_status = 'ELIGIBLE' AND exclusion_reason_code IS NULL)
  OR (eligibility_status = 'EXCLUDED' AND exclusion_reason_code IS NOT NULL)
);

ALTER TABLE ticket_wallets          ADD CHECK (balance >= 0);
ALTER TABLE ticket_ledger           ADD CHECK (balance_after >= 0);
ALTER TABLE event_entries           ADD CHECK (requested_ticket_count >= 0);
ALTER TABLE event_entries           ADD CHECK (deducted_ticket_count >= 0);
ALTER TABLE event_participants      ADD CHECK (used_ticket_count >= 0);
ALTER TABLE events                  ADD CHECK (max_tickets_per_user IS NULL OR max_tickets_per_user > 0);
ALTER TABLE events                  ADD CHECK (ends_at > starts_at);
ALTER TABLE ticket_recovery_targets ADD CHECK (target_quantity > 0);
ALTER TABLE user_game_stats         ADD CHECK (best_score >= 0 AND valid_play_count >= 0);
```

`requested_ticket_count`는 0을 허용한다. 응모권 미사용 이벤트는 차감 수량이 0이다.

## 9. 보류 — 이번에 적용하지 않음

| 항목 | 보류 사유 |
| --- | --- |
| `users.membership`의 NULL 취급 | `events.membership_rule`은 NOT NULL인데 `users.membership`은 NULL을 허용한다. 등급이 없는 사용자의 응모 가능 여부가 스펙에 정의되지 않았다. `pending-decisions.md` 등록 대상이다. |
| `membership_grade` 표기·비교 방식 | 유일하게 소문자 Enum이고, 등급 우열을 선언 순서에 의존한다. 등급이 추가되면 비교가 깨진다. 표기 통일은 데이터가 없는 지금이 비용이 가장 낮다. |

## 10. 반환 처리 방식 확인 요청

1절에서 `ticket_refund_jobs`의 `lease_until`과 `next_attempt_at`을 삭제하는 근거는 반환이 동기 처리라는 점이다.

> `ticket_refund_jobs` Note — 취소·반환 동기 처리 및 전체 작업 재시도 기록. 취소 성공 후 반환만 비동기로 대기시키지 않음
> `event.md` — 취소 처리와 대상 응모권 반환을 함께 완료한다. 취소 완료 후 별도 배치로 반환하지 않는다.

이벤트 취소 시 응모 건이 많으면 반환도 그만큼 발생하므로, 한 요청에서 처리할 때 소요 시간을 확인해야 한다. 동기 처리가 감당되지 않는 규모로 판단되면 두 컬럼 삭제를 보류하고 도메인 규칙부터 재검토한다.

## 11. 담당 범위 밖 — 확인 필요

| 항목 | 담당 | 내용 |
| --- | --- | --- |
| `reward_policies` 공동 변경 | 미션 | `conditions`·`day_timezone` 삭제는 미션 담당자도 같은 내용을 요청했다. 중복 반영되지 않도록 한 번만 적용한다. |
| `event_participants.exclusion_reason_code` | 어뷰징 | 검토 결과(`abuse_cases.review_status`)·참가 자격·응모권 회수 3층 분리 중 참가 자격 층에 해당한다. |
| `users.membership` NULL 허용 | 사용자 | 9절 보류 항목과 연결된다. |
