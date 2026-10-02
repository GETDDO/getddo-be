# 응모권 ERD

[전체 ERD](../../../README.md#erd) · [도메인 목록](../README.md#도메인별-erd) · [ERDCloud](https://www.erdcloud.com/d/vuDvgmGQcvHf6f6g9)

지갑·원장·배분·반환·회수에 해당하는 테이블을 정리했습니다.

## 테이블

| 테이블 | 역할 |
| --- | --- |
| [`ticket_wallets`](#ticket_wallets) | 사용자별 만료월 지갑과 잔액 |
| [`ticket_ledger`](#ticket_ledger) | 응모권 거래 원장 |
| [`ticket_ledger_allocations`](#ticket_ledger_allocations) | 거래별 응모권 지급 출처와 배분 수량 |
| [`ticket_refund_jobs`](#ticket_refund_jobs) | 응모권 반환 작업과 재시도 상태 |
| [`ticket_recovery_targets`](#ticket_recovery_targets) | 부정 획득분 회수 결정과 근거 |

## 테이블 이미지

저장소 DBML의 전체 컬럼·자료형·키·설명을 캡처한 이미지입니다. 이미지를 누르면 원본 크기로 볼 수 있습니다.

### ticket_wallets

[![ticket_wallets 전체 컬럼](../../assets/erd-tables/ticket_wallets.png)](../../assets/erd-tables/ticket_wallets.png)

### ticket_ledger

[![ticket_ledger 전체 컬럼](../../assets/erd-tables/ticket_ledger.png)](../../assets/erd-tables/ticket_ledger.png)

### ticket_ledger_allocations

[![ticket_ledger_allocations 전체 컬럼](../../assets/erd-tables/ticket_ledger_allocations.png)](../../assets/erd-tables/ticket_ledger_allocations.png)

### ticket_refund_jobs

[![ticket_refund_jobs 전체 컬럼](../../assets/erd-tables/ticket_refund_jobs.png)](../../assets/erd-tables/ticket_refund_jobs.png)

### ticket_recovery_targets

[![ticket_recovery_targets 전체 컬럼](../../assets/erd-tables/ticket_recovery_targets.png)](../../assets/erd-tables/ticket_recovery_targets.png)

## 다른 도메인과의 연결

FK가 있는 테이블에서 참조하는 테이블 방향으로 표시합니다. 테이블 이름을 누르면 해당 도메인으로 이동합니다.

| FK가 있는 테이블 | FK 컬럼 | 참조 테이블 | 참조 컬럼 |
| --- | --- | --- | --- |
| `ticket_wallets` | `user_id` | [`users`](user.md#users) | `id` |
| `ticket_ledger` | `actor_id` | [`users`](user.md#users) | `id` |
| `ticket_ledger` | `event_entry_id` | [`event_entries`](event.md#event_entries) | `id` |
| `ticket_ledger` | `mission_reward_claim_id` | [`mission_reward_claims`](mission.md#mission_reward_claims) | `id` |
| `ticket_ledger` | `attendance_reward_claim_id` | [`attendance_reward_claims`](attendance.md#attendance_reward_claims) | `id` |
| `ticket_ledger` | `game_reward_claim_id` | [`game_reward_claims`](game.md#game_reward_claims) | `id` |
| `ticket_recovery_targets` | `abuse_case_id` | [`abuse_cases`](abuse.md#abuse_cases) | `id` |
| `ticket_recovery_targets` | `decided_by` | [`users`](user.md#users) | `id` |
| `ticket_ledger` | `user_id` | [`users`](user.md#users) | `id` |

## 스키마 원본

- [Flyway SQL](../../../storage/db/src/main/resources/db/migration/ticket/V008__create_ticket_tables.sql): 실제 DB 적용 기준
- [통합 DBML](../schema.dbml): 전체 컬럼과 FK 관계

[← 보상 정책](reward.md) · [어뷰징 검토 →](abuse.md)

[전체 ERD](../../../README.md#erd) · [도메인 목록](../README.md#도메인별-erd) · [ERDCloud](https://www.erdcloud.com/d/vuDvgmGQcvHf6f6g9)
