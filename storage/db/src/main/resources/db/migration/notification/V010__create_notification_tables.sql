-- Source: docs/03-database/schema.dbml
-- Initial tables and foreign keys owned by notification.

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
  `scheduled_at` DATETIME(6) NOT NULL,
  `next_attempt_at` DATETIME(6),
  `lease_until` DATETIME(6),
  `last_error` TEXT,
  `created_at` DATETIME(6) NOT NULL,
  `completed_at` DATETIME(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification_job_occurrence` (`occurrence_key`),
  CONSTRAINT `fk_notification_jobs_1` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `fk_notification_jobs_2` FOREIGN KEY (`publication_id`) REFERENCES `publications` (`id`),
  CONSTRAINT `fk_notification_jobs_3` FOREIGN KEY (`target_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_notification_jobs_4` FOREIGN KEY (`source_job_id`) REFERENCES `notification_jobs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `notifications` (
  `id` BINARY(16) NOT NULL,
  `job_id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `event_id` BINARY(16),
  `title` VARCHAR(200) NOT NULL,
  `body` TEXT NOT NULL,
  `link_url` VARCHAR(500),
  `created_at` DATETIME(6) NOT NULL,
  `is_read` BOOLEAN NOT NULL DEFAULT 0,
  `mock_delivery_status` ENUM('PENDING', 'SENT', 'FAILED') NOT NULL,
  `mock_sent_at` DATETIME(6),
  `delivery_attempt_count` INT NOT NULL DEFAULT 0,
  `next_delivery_attempt_at` DATETIME(6),
  `last_delivery_error` TEXT,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification_job_user` (`job_id`, `user_id`),
  CONSTRAINT `fk_notifications_1` FOREIGN KEY (`job_id`) REFERENCES `notification_jobs` (`id`),
  CONSTRAINT `fk_notifications_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_notifications_3` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
