-- Source: docs/03-database/schema.dbml
-- GD-93: Initial schema; version numbers retained by user request.
-- Initial tables and foreign keys owned by notification.

CREATE TABLE `notification_jobs` (
  `id` binary(16) NOT NULL COMMENT '알림 생성 작업 ID',
  `event_id` binary(16) COMMENT '연결 이벤트 ID',
  `source_job_id` binary(16) COMMENT '일정 변경 및 취소 시 기존 시작 알림 수신자를 찾는 원본 작업 ID',
  `payload` json NOT NULL COMMENT '알림 내용과 최초 생성 시점의 대상 스냅샷',
  `occurrence_key` varchar(160) NOT NULL COMMENT '작업별 고정 발생 키',
  `notification_type` ENUM ('EVENT_START', 'RESULT_PUBLISHED', 'ENTRY_EXCLUDED', 'EVENT_SCHEDULE_CHANGED', 'EVENT_CANCELED', 'RESULT_CHANGED') NOT NULL COMMENT '시작·최초 발표·제외·일정 변경·취소·결과 변경. 실제 업무 확정에 연결해 발생 키 고정',
  `status` ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL COMMENT 'PENDING=대기',
  `last_processed_user_id` binary(16) COMMENT '고정 대상 집합에서 UUID 바이트 순으로 마지막 처리한 사용자 ID',
  `attempt_count` int NOT NULL DEFAULT 0 COMMENT '작업 실행 시도 횟수',
  `scheduled_at` datetime(6) NOT NULL COMMENT '알림 생성 예정 시각 UTC',
  `next_attempt_at` datetime(6) COMMENT '다음 재시도 예정 시각 UTC',
  `lease_until` datetime(6) COMMENT '작업 처리권 임대 만료 시각 UTC',
  `last_error` text COMMENT '마지막 작업 오류 내용',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  `completed_at` datetime(6) COMMENT '완료 시각 UTC',
  CONSTRAINT `chk_notification_attempts` CHECK (attempt_count >= 0),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification_job_occurrence` (`occurrence_key`),
  CONSTRAINT `fk_notification_jobs_1` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `fk_notification_jobs_2` FOREIGN KEY (`source_job_id`) REFERENCES `notification_jobs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `notifications` (
  `id` binary(16) NOT NULL COMMENT '개인 알림 ID',
  `job_id` binary(16) NOT NULL COMMENT '알림 생성 작업 ID',
  `user_id` binary(16) NOT NULL COMMENT '사용자 ID',
  `event_id` binary(16) COMMENT '연결 이벤트 ID',
  `title` varchar(200) NOT NULL COMMENT '알림 제목',
  `body` text NOT NULL COMMENT '알림 본문',
  `link_url` varchar(500) COMMENT '알림 선택 시 이동 URL',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  `is_read` boolean NOT NULL DEFAULT 0 COMMENT '읽음 여부',
  `mock_delivery_status` ENUM ('PENDING', 'SENT', 'FAILED') NOT NULL COMMENT 'PENDING=발송 대기',
  `mock_sent_at` datetime(6) COMMENT '모의 발송 완료 시각 UTC',
  `delivery_attempt_count` int NOT NULL DEFAULT 0 COMMENT '모의 발송 시도 횟수',
  `next_delivery_attempt_at` datetime(6) COMMENT '다음 모의 발송 재시도 예정 시각 UTC',
  `last_delivery_error` text COMMENT '마지막 모의 발송 오류 내용',
  CONSTRAINT `chk_delivery_attempts` CHECK (delivery_attempt_count >= 0),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification_job_user` (`job_id`, `user_id`),
  CONSTRAINT `fk_notifications_1` FOREIGN KEY (`job_id`) REFERENCES `notification_jobs` (`id`),
  CONSTRAINT `fk_notifications_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_notifications_3` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
