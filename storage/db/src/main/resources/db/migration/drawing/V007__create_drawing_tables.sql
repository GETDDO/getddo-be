-- Source: docs/03-database/schema.dbml
-- Tables owned by drawing. Foreign keys are added in V012.

CREATE TABLE `draw_runs` (
  `id` BINARY(16) NOT NULL,
  `event_id` BINARY(16) NOT NULL,
  `executed_by` BINARY(16),
  `previous_draw_id` BINARY(16),
  `original_draw_id` BINARY(16),
  `run_number` INT NOT NULL,
  `idempotency_key` VARCHAR(100) NOT NULL,
  `execution_type` ENUM('AUTO', 'MANUAL') NOT NULL,
  `status` ENUM('PREPARING', 'READY', 'RUNNING', 'CONFIRMED', 'FAILED') NOT NULL,
  `reason` TEXT,
  `algorithm_version` VARCHAR(100),
  `rules_snapshot` JSON,
  `snapshot_fixed_at` DATETIME,
  `started_at` DATETIME,
  `confirmed_at` DATETIME,
  `failure_count` INT NOT NULL DEFAULT 0,
  `last_failure_code` VARCHAR(50),
  `last_failure_reason` TEXT,
  `last_failed_at` DATETIME,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_draw_runs_1` (`event_id`, `run_number`),
  UNIQUE KEY `uq_draw_runs_2` (`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_candidates` (
  `id` BINARY(16) NOT NULL,
  `draw_run_id` BINARY(16) NOT NULL,
  `participant_id` BINARY(16) NOT NULL,
  `ticket_count` BIGINT NOT NULL,
  `weight` DECIMAL(30,10) NOT NULL,
  `entry_snapshot` JSON NOT NULL,
  `eligibility_snapshot` JSON NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_draw_candidates_1` (`draw_run_id`, `participant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_results` (
  `id` BINARY(16) NOT NULL,
  `draw_run_id` BINARY(16) NOT NULL,
  `event_prize_id` BINARY(16) NOT NULL,
  `candidate_id` BINARY(16),
  `slot_number` INT NOT NULL,
  `selection_order` INT,
  `result_type` ENUM('SELECTED', 'UNFILLED') NOT NULL,
  `publication_status` ENUM('PENDING', 'PUBLISHED', 'EXCLUDED'),
  `created_at` DATETIME NOT NULL,
  CONSTRAINT `chk_draw_result_shape` CHECK ((result_type = 'UNFILLED' AND candidate_id IS NULL AND selection_order IS NULL AND publication_status IS NULL) OR (result_type = 'SELECTED' AND candidate_id IS NOT NULL AND selection_order IS NOT NULL AND publication_status IS NOT NULL)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_draw_results_1` (`draw_run_id`, `event_prize_id`, `slot_number`),
  UNIQUE KEY `uq_draw_results_2` (`draw_run_id`, `candidate_id`),
  UNIQUE KEY `uq_draw_results_3` (`draw_run_id`, `selection_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `current_awards` (
  `id` BINARY(16) NOT NULL,
  `event_id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `event_prize_id` BINARY(16) NOT NULL,
  `draw_result_id` BINARY(16) NOT NULL,
  `slot_number` INT NOT NULL,
  `assigned_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_current_awards_1` (`event_id`, `user_id`),
  UNIQUE KEY `uq_current_awards_2` (`event_prize_id`, `slot_number`),
  UNIQUE KEY `uq_current_awards_3` (`draw_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `award_cancellations` (
  `id` BINARY(16) NOT NULL,
  `draw_result_id` BINARY(16) NOT NULL,
  `canceled_by` BINARY(16) NOT NULL,
  `replacement_draw_id` BINARY(16) NOT NULL,
  `reason` TEXT NOT NULL,
  `canceled_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_award_cancellations_1` (`draw_result_id`),
  UNIQUE KEY `uq_award_cancellations_2` (`replacement_draw_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `publications` (
  `id` BINARY(16) NOT NULL,
  `event_id` BINARY(16) NOT NULL,
  `draw_run_id` BINARY(16) NOT NULL,
  `published_by` BINARY(16),
  `revision` INT NOT NULL DEFAULT 1,
  `updated_by` BINARY(16),
  `updated_at` DATETIME NOT NULL,
  `published_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_publications_1` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
