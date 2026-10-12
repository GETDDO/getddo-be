# D 파트 ERD 구조 메모

ERD 검토 분담(임시) 기준 13개 테이블. 개인 참고용.

| 기능 | 테이블 |
|---|---|
| 게임 | `games` `game_plays` `user_game_stats` `game_reward_claims` |
| 응모 | `events` `event_entries` `event_participants` |
| 응모권 | `ticket_wallets` `ticket_ledger` `ticket_ledger_allocations` `ticket_refund_jobs` |
| 회수 | `ticket_recovery_targets` |
| 정책 | `reward_policies` |

---

## 응모권 3층 구조

이게 제일 헷갈리는 부분이라 먼저.

```
ticket_wallets        잔액만 안다.        "지금 얼마 있나"
ticket_ledger         거래 이력.          "언제 왜 늘고 줄었나"
ticket_ledger_allocations  출처 배분.     "이 잔액이 어느 지급분에서 왔나"
```

원장은 **수정·삭제가 없다.** 정정이 필요하면 원본에 연결된 새 행을 추가한다.

> `ticket.md` — 응모권 이력은 수정·삭제 없이 누적 기록한다. 이력 합계와 현재 보유 응모권 수의 일치를 보장한다.

### 지갑은 사용자별이 아니라 `(사용자, 만료월)`별이다

만료가 월 경계에 일괄로 걸리기 때문이다.

| 입금 | 만료 | 지갑 |
|---|---|---|
| 9월 일반 지급 | 10/1 00:00 KST | A |
| 9월 반환분 | 11/1 00:00 KST | B |
| 10월 일반 지급 | 11/1 00:00 KST | **B** ← 반환분과 합쳐짐 |

지급 시점이 아니라 **만료 시점**이 묶음 기준이다. 만료일이 같으면 한 지갑으로 합쳐지므로 "먼저 지급된 것부터" 같은 2차 정렬은 표현할 수 없고, 필요도 없다.

### 출처 추적은 배분 테이블이 한다

어뷰징 회수가 "이 지급분 3장을 회수" 식으로 특정 지급 건을 지목하는데, 지갑은 합산 잔액만 갖고 있어서 답을 못 한다.

```
ticket_ledger_allocations
  ledger_id                 이번 거래 (SPEND / EXPIRE / REVOKE / REFUND)
  source_credit_ledger_id   수량이 변동한 입금 건 (GRANT 또는 REFUND)
  original_grant_id         최초 GRANT. 반환으로 재사용돼도 계속 유지
  quantity                  감소 음수, 입금 양수
```

- 입금 X의 잔여 = `SUM(quantity) WHERE source_credit_ledger_id = X`
- 최초 지급 G의 회수 가능량 = `SUM(quantity) WHERE original_grant_id = G`

`original_grant_id`가 핵심이다. 반환(REFUND)으로 응모권이 새 지갑에 다시 들어가도 "원래 어느 지급에서 나온 건지"가 계속 따라다닌다. 그래야 부정 지급분을 반환 후에도 회수할 수 있다.

### 수명 주기

```
[지급]  보상 확정 → 지갑 확보(만료월 기준) → GRANT(+)  → 자기 배분(+)
[차감]  응모 접수 → 지갑 선택             → SPEND(-)  → 출처별 배분(-)
[반환]  제외·취소 → refund_jobs           → REFUND(+) → 새 지갑(반환월 +2달)
[만료]  월 경계 배치                      → EXPIRE(-)
[회수]  어뷰징 확정 → recovery_targets    → REVOKE(-)
```

반환분 만료가 왜 +2달인지: `ticket.md`가 "반환한 KST 기준월의 다음 달 말까지" 로 정했다. 9월 반환 → 10월 말까지 유효 → 11/1 만료. 일반 지급분(지급월 말까지)보다 한 달 길다. 이 차이 때문에 지갑이 갈린다.

### 정합성은 세 겹으로 확인한다

원장에 `balance_after`(거래 직후 잔액)와 `wallet_version`(기록 시점 지갑 version)을 둔다.

```
balance_after[n] = balance_after[n-1] + quantity[n]     원장 안에서 연속
ticket_wallets.balance = 지갑의 마지막 balance_after     지갑과 원장
SUM(quantity) = ticket_wallets.balance                  전체 합계
```

`wallet_version`은 지갑의 낙관적 락 값을 그대로 적는 것이라 따로 채번하지 않는다. 대신 **원장 행 하나당 지갑 `UPDATE`를 정확히 한 번** 해야 한다. 한 트랜잭션에서 같은 지갑에 두 행을 쓸 때(반환과 회수) 갱신을 합치면 `version`이 한 번만 올라 순서가 겹친다.

`ticket_ledger.user_id`는 사용자 이력 화면의 조회 경로다. 지갑을 조인하지 않고 바로 읽고, `(wallet_id, user_id)` 복합 FK로 다른 사용자의 지갑에 기록하는 것도 막는다.

---

## 응모 경로

```
events                  상한 · 최소등급 · 기간
  └ event_participants  사용자×이벤트 1행. 자격 · 누적 사용량
      └ event_entries   요청 1건 1행. 거절 건도 보존
          └ SPEND       지갑이 여러 개면 원장 여러 행
```

`event_entries`는 거절까지 남기고 `event_participants`는 접수 성공자만 갖는다. 그래서 `entries.participant_id`가 nullable이다.

**상한 두 개가 다르다.**

| 컬럼 | 의미 |
|---|---|
| `events.max_tickets_per_user` | 사용자당 이벤트 **누적** 5장 (일반 가중치) / 1장 (미적용) / NULL (월말 소진용) |
| `event_entries.single_entry_guard` | 1회 제한 이벤트의 성공 건만 1. 응모 **횟수** 제한 |

장수 제한과 횟수 제한이라 별개다. 다만 "미적용 1장"과 "1회 제한"이 실질적으로 겹칠 수 있어 구현 시 어느 쪽으로 막을지 정해야 한다.

---

## 게임 경로

```
games                    규칙 · 규칙버전 · 보상정책
  └ game_plays           플레이 1건. 서버 판정
      ├ user_game_stats        최고 · 누적 점수 (집계)
      └ game_reward_claims     하루 1회 보상 → GRANT
```

플레이는 무제한이고 보상만 하루 1회로 막힌다(`source_key` = 게임 + KST 날짜). 점수 경쟁이 목적이라 의도된 구조다.

`games.rules`는 덮어써지지만 플레이별 적용 규칙이 `game_plays.validation_data`에 스냅샷되므로 과거 판정 근거는 남는다.

---

## 구현할 때 걸리는 것 4개

### 1. 회수는 잔여 확인 + 잠금이 먼저

`ticket_recovery_targets`는 **결정만** 기록한다. 실제 차감(`REVOKE`) 직전에 `original_grant_id`의 잔여를 `allocations`에서 읽고 잠가야 한다.

> 이미 만료되거나 회수된 수량은 중복 차감하지 않으며, 잔액을 음수로 만들지 않는다.
> 다른 정상 지급분을 대신 차감하지 않는다. — `ticket.md`

결정 수량보다 잔여가 적으면 **잔여만큼만** 회수하고 부족분은 포기한다. 실제 회수량은 `SUM(ticket_ledger.quantity) WHERE recovery_target_id = ?`로 나온다.

### 2. 차감 순서는 스펙이 안 정했다

> 응모권의 유효기간은 월별로 구분하며, **별도의 차감 순서는 두지 않는다.** — `ticket.md`

구현이 정해야 한다. 순서 없이 쓰면 만료 임박분을 남긴 채 장기분을 써서 사용자가 손해를 본다.

**기본값을 `expires_at ASC`로 하고 `docs/04-decisions`에 ADR로 남긴다.** 공용 정책이 아니라 백엔드 구현 결정이라 BE 저장소에서 확정 가능하다.

> 로컬 브랜치 `docs/ticket-deduction-order`(`c2c9b16`)는 이 문장을 FIFO로 바꾸려던 제안이다. 스펙이 반대로 확정했으므로 **push하지 않고 폐기.**

### 3. 반환은 동기 처리다

> 제외 처리로 반환되는 부정 획득분은 반환과 회수를 같은 트랜잭션으로 처리해 중간 재사용을 막는다. — `ticket.md`
> 취소 처리와 대상 응모권 반환을 함께 완료한다. 취소 완료 후 별도 배치로 반환하지 않는다. — `event.md`

`ticket_refund_jobs`는 이름과 달리 **큐가 아니다.** Note도 "취소·반환 동기 처리 및 재시도 기록"이라고 적혀 있다. 요청 스레드가 그 자리에서 취소와 반환을 함께 끝낸다.

그래서 워커 선점용 `lease_until`과 예약 재시도용 `next_attempt_at`은 쓰이지 않는다(삭제 요청함). 실패하면 **취소와 반환을 둘 다 미완료로 두고**, 관리자가 다시 요청할 때 `attempt_count`·`last_error`에 쌓는다.

> 남은 확인: 응모가 수천 건인 이벤트를 취소하면 반환도 그만큼 발생한다. 한 요청에서 감당되는 규모인지 측정이 필요하다.

### 4. 지갑 만료 조회는 `status`를 믿으면 안 된다

`ticket_wallets.status`는 만료 배치의 **처리 결과 표시**다. 배치가 지연되면 `ACTIVE`인데 `expires_at`이 지난 지갑이 생긴다.

→ 사용 가능 잔액은 `expires_at > NOW() AND balance > 0`으로 조회한다.

---

## 아직 안 정해진 것

| 항목 | 누가 |
|---|---|
| 게임 STARTED 동시 여러 개 허용 여부 | 나 (`pending-decisions.md`에 "게임별 유효성 검증 방식 설계"로 등록됨) |
| 대량 취소 시 동기 반환이 감당되는 규모인지 | 나 (측정 후 판단) |
| 등급 NULL 사용자를 응모시킬지 | 사용자 담당 · 스펙에 없음 |
| `membership_grade` 표기·비교 방식 | 사용자 담당 |

### 정해진 것

| 항목 | 결론 |
|---|---|
| 응모권 차감 순서 | 스펙이 순서를 두지 않으므로 구현이 `expires_at ASC`로 정하고 ADR로 남긴다 |
| 연휴 특별 보상 정책 | `reward_policies`를 유지하고 `effective_until` 체인으로 표현한다. 복귀 정책을 함께 등록한다 |
| 미션 보상 정책 | `reward_policies`로 계속 관리한다. 기간 불일치는 미션 등록·수정 시 서버에서 검사한다 |
| 잔액 정합성 | `balance_after` + `wallet_version` 도입 |
| 반환 처리 방식 | 동기. 워커 컬럼 2개 삭제 |

---

수정 요청 내역은 [erd-change-request-part-d.md](erd-change-request-part-d.md).
