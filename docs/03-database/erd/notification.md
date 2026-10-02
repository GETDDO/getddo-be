# 알림 ERD

[전체 ERD](../../../README.md#erd) · [도메인 목록](../README.md#도메인별-erd) · [ERDCloud](https://www.erdcloud.com/d/vuDvgmGQcvHf6f6g9)

알림 생성 작업과 사용자 알림에 해당하는 테이블을 정리했습니다.

## 테이블

| 테이블 | 역할 |
| --- | --- |
| [`notification_jobs`](#notification_jobs) | 알림 생성 작업과 재시도 상태 |
| [`notifications`](#notifications) | 사용자 알림과 읽음·모의 발송 상태 |

## 테이블 이미지

저장소 DBML의 전체 컬럼·자료형·키·설명을 캡처한 이미지입니다. 이미지를 누르면 원본 크기로 볼 수 있습니다.

### notification_jobs

[![notification_jobs 전체 컬럼](../../assets/erd-tables/notification_jobs.png)](../../assets/erd-tables/notification_jobs.png)

### notifications

[![notifications 전체 컬럼](../../assets/erd-tables/notifications.png)](../../assets/erd-tables/notifications.png)

## 다른 도메인과의 연결

FK가 있는 테이블에서 참조하는 테이블 방향으로 표시합니다. 테이블 이름을 누르면 해당 도메인으로 이동합니다.

| FK가 있는 테이블 | FK 컬럼 | 참조 테이블 | 참조 컬럼 |
| --- | --- | --- | --- |
| `notification_jobs` | `event_id` | [`events`](event.md#events) | `id` |
| `notification_jobs` | `publication_id` | [`publications`](drawing.md#publications) | `id` |
| `notification_jobs` | `target_user_id` | [`users`](user.md#users) | `id` |
| `notifications` | `user_id` | [`users`](user.md#users) | `id` |
| `notifications` | `event_id` | [`events`](event.md#events) | `id` |

## 스키마 원본

- [Flyway SQL](../../../storage/db/src/main/resources/db/migration/notification/V010__create_notification_tables.sql): 실제 DB 적용 기준
- [통합 DBML](../schema.dbml): 전체 컬럼과 FK 관계

[← 추첨·발표](drawing.md) · [감사 로그 →](audit.md)

[전체 ERD](../../../README.md#erd) · [도메인 목록](../README.md#도메인별-erd) · [ERDCloud](https://www.erdcloud.com/d/vuDvgmGQcvHf6f6g9)
