# 출석 ERD

[전체 ERD](../../../README.md#erd) · [도메인 목록](../README.md#도메인별-erd) · [ERDCloud](https://www.erdcloud.com/d/vuDvgmGQcvHf6f6g9)

일일·연속 출석과 보상 기록에 해당하는 테이블을 정리했습니다.

## 테이블

| 테이블 | 역할 |
| --- | --- |
| [`attendances`](../../../storage/db/src/main/resources/db/migration/attendance/V004__create_attendance_tables.sql#L7) | 사용자별 일일 출석 기록 |
| [`attendance_streaks`](../../../storage/db/src/main/resources/db/migration/attendance/V004__create_attendance_tables.sql#L17) | 사용자별 월간 연속 출석 상태 |
| [`attendance_streak_policy_sets`](../../../storage/db/src/main/resources/db/migration/attendance/V004__create_attendance_tables.sql#L33) | 적용월별 연속 출석 정책 묶음 |
| [`attendance_streak_policies`](../../../storage/db/src/main/resources/db/migration/attendance/V004__create_attendance_tables.sql#L43) | 연속 출석 단계와 보상 수량 |
| [`attendance_reward_claims`](../../../storage/db/src/main/resources/db/migration/attendance/V004__create_attendance_tables.sql#L56) | 출석 보상 지급 기록 |

## 관계도

이 도메인의 PK·FK와 일부 주요 컬럼, 내부 FK 관계를 요약했습니다. 전체 컬럼·UNIQUE·CHECK는 아래 SQL을 확인합니다.

```mermaid
erDiagram
    attendances {
        binary id PK
        binary user_id FK
        date attendance_date
    }
    attendance_streaks {
        binary id PK
        binary user_id FK
        binary policy_set_id FK
        date streak_month
        int consecutive_days
    }
    attendance_streak_policy_sets {
        binary id PK
        binary created_by FK
        date effective_month
    }
    attendance_streak_policies {
        binary id PK
        binary policy_set_id FK
        int milestone_days
        int reward_ticket_count
    }
    attendance_reward_claims {
        binary id PK
        binary user_id FK
        binary attendance_id FK
        attendance_reward_type reward_type
        binary reward_policy_id FK
        binary attendance_streak_policy_id FK
        date reward_date
    }
    attendance_streak_policy_sets ||..o{ attendance_streak_policies : "policy_set_id"
    attendance_streak_policy_sets ||..o{ attendance_streaks : "policy_set_id"
    attendances ||..o{ attendance_reward_claims : "attendance_id"
    attendance_streak_policies |o..o{ attendance_reward_claims : "attendance_streak_policy_id"
```

## 다른 도메인과의 연결

FK가 있는 테이블에서 참조하는 테이블 방향으로 표시합니다. 테이블 이름을 누르면 해당 도메인으로 이동합니다.

| FK가 있는 테이블 | FK 컬럼 | 참조 테이블 | 참조 컬럼 |
| --- | --- | --- | --- |
| `attendances` | `user_id` | [`users`](user.md) | `id` |
| [`abuse_cases`](abuse.md) | `attendance_id` | `attendances` | `id` |
| [`ticket_ledger`](ticket.md) | `attendance_reward_claim_id` | `attendance_reward_claims` | `id` |
| `attendance_streaks` | `user_id` | [`users`](user.md) | `id` |
| `attendance_streak_policy_sets` | `created_by` | [`users`](user.md) | `id` |
| `attendance_reward_claims` | `user_id` | [`users`](user.md) | `id` |
| `attendance_reward_claims` | `reward_policy_id` | [`reward_policies`](reward.md) | `id` |

## 스키마 원본

- [Flyway SQL](../../../storage/db/src/main/resources/db/migration/attendance/V004__create_attendance_tables.sql): 실제 DB 적용 기준
- [통합 DBML](../schema.dbml): 전체 컬럼과 FK 관계

[← 이벤트·응모](event.md) · [미션 →](mission.md)

[전체 ERD](../../../README.md#erd) · [도메인 목록](../README.md#도메인별-erd) · [ERDCloud](https://www.erdcloud.com/d/vuDvgmGQcvHf6f6g9)
