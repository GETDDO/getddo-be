# 보상 정책 ERD

[전체 ERD](../../../README.md#erd) · [도메인 목록](../README.md#도메인별-erd) · [ERDCloud](https://www.erdcloud.com/d/vuDvgmGQcvHf6f6g9)

출석·미션·게임의 보상 정책에 해당하는 테이블을 정리했습니다.

## 테이블

| 테이블 | 역할 |
| --- | --- |
| [`reward_policies`](#reward_policies) | 보상 유형·수량·적용 기간 |

## 테이블 이미지

저장소 DBML의 전체 컬럼·자료형·키·설명을 캡처한 이미지입니다. 이미지를 누르면 원본 크기로 볼 수 있습니다.

### reward_policies

[![reward_policies 전체 컬럼](../../assets/erd-tables/reward_policies.png)](../../assets/erd-tables/reward_policies.png)

## 다른 도메인과의 연결

FK가 있는 테이블에서 참조하는 테이블 방향으로 표시합니다. 테이블 이름을 누르면 해당 도메인으로 이동합니다.

| FK가 있는 테이블 | FK 컬럼 | 참조 테이블 | 참조 컬럼 |
| --- | --- | --- | --- |
| [`mission_reward_claims`](mission.md#mission_reward_claims) | `reward_policy_id` | `reward_policies` | `id` |
| `reward_policies` | `game_id` | [`games`](game.md#games) | `id` |
| `reward_policies` | `created_by` | [`users`](user.md#users) | `id` |
| [`missions`](mission.md#missions) | `reward_policy_id` | `reward_policies` | `id` |
| [`mission_submissions`](mission.md#mission_submissions) | `reward_policy_id` | `reward_policies` | `id` |
| [`attendance_reward_claims`](attendance.md#attendance_reward_claims) | `reward_policy_id` | `reward_policies` | `id` |
| [`game_reward_claims`](game.md#game_reward_claims) | `reward_policy_id` | `reward_policies` | `id` |

## 스키마 원본

- [Flyway SQL](../../../storage/db/src/main/resources/db/migration/reward/V002__create_reward_tables.sql): 실제 DB 적용 기준
- [통합 DBML](../schema.dbml): 전체 컬럼과 FK 관계

[← 게임](game.md) · [응모권 →](ticket.md)

[전체 ERD](../../../README.md#erd) · [도메인 목록](../README.md#도메인별-erd) · [ERDCloud](https://www.erdcloud.com/d/vuDvgmGQcvHf6f6g9)
