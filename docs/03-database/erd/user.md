# 사용자 ERD

[전체 ERD](../../../README.md#erd) · [도메인 목록](../README.md#도메인별-erd) · [ERDCloud](https://www.erdcloud.com/d/vuDvgmGQcvHf6f6g9)

사용자 정보·역할·멤버십에 해당하는 테이블을 정리했습니다.

## 테이블

| 테이블 | 역할 |
| --- | --- |
| [`users`](../../../storage/db/src/main/resources/db/migration/user/V001__create_user_tables.sql#L4) | 사용자 정보, 역할, 멤버십 |

## 다른 도메인과의 연결

FK가 있는 테이블에서 참조하는 테이블 방향으로 표시합니다. 테이블 이름을 누르면 해당 도메인으로 이동합니다.

| FK가 있는 테이블 | FK 컬럼 | 참조 테이블 | 참조 컬럼 |
| --- | --- | --- | --- |
| [`user_game_stats`](game.md#테이블) | `user_id` | `users` | `id` |
| [`ticket_wallets`](ticket.md#테이블) | `user_id` | `users` | `id` |
| [`game_plays`](game.md#테이블) | `user_id` | `users` | `id` |
| [`event_participants`](event.md#테이블) | `user_id` | `users` | `id` |
| [`mission_reward_claims`](mission.md#테이블) | `user_id` | `users` | `id` |
| [`current_awards`](drawing.md#테이블) | `user_id` | `users` | `id` |
| [`reward_policies`](reward.md#테이블) | `created_by` | `users` | `id` |
| [`missions`](mission.md#테이블) | `created_by` | `users` | `id` |
| [`banners`](event.md#테이블) | `created_by` | `users` | `id` |
| [`attendances`](attendance.md#테이블) | `user_id` | `users` | `id` |
| [`mission_submissions`](mission.md#테이블) | `user_id` | `users` | `id` |
| [`award_cancellations`](drawing.md#테이블) | `canceled_by` | `users` | `id` |
| [`events`](event.md#테이블) | `created_by` | `users` | `id` |
| [`notification_jobs`](notification.md#테이블) | `target_user_id` | `users` | `id` |
| [`notifications`](notification.md#테이블) | `user_id` | `users` | `id` |
| [`event_entries`](event.md#테이블) | `user_id` | `users` | `id` |
| [`publications`](drawing.md#테이블) | `published_by` | `users` | `id` |
| [`publications`](drawing.md#테이블) | `updated_by` | `users` | `id` |
| [`draw_runs`](drawing.md#테이블) | `executed_by` | `users` | `id` |
| [`abuse_cases`](abuse.md#테이블) | `user_id` | `users` | `id` |
| [`abuse_cases`](abuse.md#테이블) | `reviewed_by` | `users` | `id` |
| [`audit_logs`](audit.md#테이블) | `actor_id` | `users` | `id` |
| [`ticket_ledger`](ticket.md#테이블) | `actor_id` | `users` | `id` |
| [`attendance_streaks`](attendance.md#테이블) | `user_id` | `users` | `id` |
| [`quiz_question_progress`](mission.md#테이블) | `user_id` | `users` | `id` |
| [`attendance_streak_policy_sets`](attendance.md#테이블) | `created_by` | `users` | `id` |
| [`attendance_reward_claims`](attendance.md#테이블) | `user_id` | `users` | `id` |
| [`game_reward_claims`](game.md#테이블) | `user_id` | `users` | `id` |
| [`ticket_recovery_targets`](ticket.md#테이블) | `decided_by` | `users` | `id` |
| [`ticket_ledger`](ticket.md#테이블) | `user_id` | `users` | `id` |

## 스키마 원본

- [Flyway SQL](../../../storage/db/src/main/resources/db/migration/user/V001__create_user_tables.sql): 실제 DB 적용 기준
- [통합 DBML](../schema.dbml): 전체 컬럼과 FK 관계

[이벤트·응모 →](event.md)

[전체 ERD](../../../README.md#erd) · [도메인 목록](../README.md#도메인별-erd) · [ERDCloud](https://www.erdcloud.com/d/vuDvgmGQcvHf6f6g9)
