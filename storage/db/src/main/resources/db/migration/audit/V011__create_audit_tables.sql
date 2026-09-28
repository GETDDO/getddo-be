-- Source: docs/03-database/schema.dbml
-- Initial tables and foreign keys owned by audit.

CREATE TABLE `audit_logs` (
  `id` BINARY(16) NOT NULL,
  `actor_id` BINARY(16),
  `action` VARCHAR(60) NOT NULL,
  `target_type` VARCHAR(60) NOT NULL,
  `target_id` BINARY(16) NOT NULL,
  `reason` TEXT,
  `before_data` JSON,
  `after_data` JSON,
  `request_id` VARCHAR(100),
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `audit_target_time_idx` (`target_type`, `target_id`, `created_at`),
  CONSTRAINT `fk_audit_logs_1` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
