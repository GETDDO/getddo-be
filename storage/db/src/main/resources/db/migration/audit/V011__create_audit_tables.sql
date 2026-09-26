-- Source: docs/03-database/schema.dbml
-- Tables owned by audit. Foreign keys are added in V012.

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
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `audit_target_time_idx` (`target_type`, `target_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
