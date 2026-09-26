-- Source: docs/03-database/schema.dbml
-- Tables owned by notification. Foreign keys are added in V012.

CREATE TABLE `notification_jobs` (
  `id` BINARY(16) NOT NULL,
  `event_id` BINARY(16),
  `publication_id` BINARY(16),
  `target_user_id` BINARY(16),
  `payload` JSON NOT NULL,
  `source_job_id` BINARY(16),
  `occurrence_key` VARCHAR(160) NOT NULL,
  `notification_type` ENUM('EVENT_START', 'RESULT_PUBLISHED', 'ENTRY_EXCLUDED', 'TICKET_REVOKED', 'EVENT_SCHEDULE_CHANGED', 'EVENT_CANCELED', 'RESULT_CHANGED') NOT NULL,
  `status` ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL,
  `last_processed_user_id` BINARY(16),
  `attempt_count` INT NOT NULL DEFAULT 0,
  `scheduled_at` DATETIME NOT NULL,
  `next_attempt_at` DATETIME,
  `lease_until` DATETIME,
  `last_error` TEXT,
  `created_at` DATETIME NOT NULL,
  `completed_at` DATETIME,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification_job_occurrence` (`occurrence_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `notifications` (
  `id` BINARY(16) NOT NULL,
  `job_id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `event_id` BINARY(16),
  `title` VARCHAR(200) NOT NULL,
  `body` TEXT NOT NULL,
  `link_url` VARCHAR(500),
  `created_at` DATETIME NOT NULL,
  `is_read` BOOLEAN NOT NULL DEFAULT 0,
  `mock_delivery_status` ENUM('PENDING', 'SENT', 'FAILED') NOT NULL,
  `mock_sent_at` DATETIME,
  `delivery_attempt_count` INT NOT NULL DEFAULT 0,
  `next_delivery_attempt_at` DATETIME,
  `last_delivery_error` TEXT,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification_job_user` (`job_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
