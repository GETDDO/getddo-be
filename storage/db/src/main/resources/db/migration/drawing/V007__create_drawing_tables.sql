-- Source: docs/03-database/schema.dbml
-- Initial tables and foreign keys owned by drawing.

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
  `snapshot_fixed_at` DATETIME(6),
  `started_at` DATETIME(6),
  `confirmed_at` DATETIME(6),
  `failure_count` INT NOT NULL DEFAULT 0,
  `last_failure_code` VARCHAR(50),
  `last_failure_reason` TEXT,
  `last_failed_at` DATETIME(6),
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_draw_runs_1` (`event_id`, `run_number`),
  UNIQUE KEY `uq_draw_runs_2` (`idempotency_key`),
  CONSTRAINT `fk_draw_runs_1` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `fk_draw_runs_2` FOREIGN KEY (`executed_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_draw_runs_3` FOREIGN KEY (`previous_draw_id`) REFERENCES `draw_runs` (`id`),
  CONSTRAINT `fk_draw_runs_4` FOREIGN KEY (`original_draw_id`) REFERENCES `draw_runs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_candidates` (
  `id` BINARY(16) NOT NULL,
  `draw_run_id` BINARY(16) NOT NULL,
  `participant_id` BINARY(16) NOT NULL,
  `ticket_count` BIGINT NOT NULL,
  `weight` DECIMAL(30,10) NOT NULL,
  `entry_snapshot` JSON NOT NULL,
  `eligibility_snapshot` JSON NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_draw_candidates_1` (`draw_run_id`, `participant_id`),
  CONSTRAINT `fk_draw_candidates_1` FOREIGN KEY (`draw_run_id`) REFERENCES `draw_runs` (`id`),
  CONSTRAINT `fk_draw_candidates_2` FOREIGN KEY (`participant_id`) REFERENCES `event_participants` (`id`)
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
  `created_at` DATETIME(6) NOT NULL,
  CONSTRAINT `chk_draw_result_shape` CHECK ((result_type = 'UNFILLED' AND candidate_id IS NULL AND selection_order IS NULL AND publication_status IS NULL) OR (result_type = 'SELECTED' AND candidate_id IS NOT NULL AND selection_order IS NOT NULL AND publication_status IS NOT NULL)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_draw_results_1` (`draw_run_id`, `event_prize_id`, `slot_number`),
  UNIQUE KEY `uq_draw_results_2` (`draw_run_id`, `candidate_id`),
  UNIQUE KEY `uq_draw_results_3` (`draw_run_id`, `selection_order`),
  CONSTRAINT `fk_draw_results_1` FOREIGN KEY (`draw_run_id`) REFERENCES `draw_runs` (`id`),
  CONSTRAINT `fk_draw_results_2` FOREIGN KEY (`event_prize_id`) REFERENCES `event_prizes` (`id`),
  CONSTRAINT `fk_draw_results_3` FOREIGN KEY (`candidate_id`) REFERENCES `draw_candidates` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `current_awards` (
  `id` BINARY(16) NOT NULL,
  `event_id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `event_prize_id` BINARY(16) NOT NULL,
  `draw_result_id` BINARY(16) NOT NULL,
  `slot_number` INT NOT NULL,
  `assigned_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_current_awards_1` (`event_id`, `user_id`),
  UNIQUE KEY `uq_current_awards_2` (`event_prize_id`, `slot_number`),
  UNIQUE KEY `uq_current_awards_3` (`draw_result_id`),
  CONSTRAINT `fk_current_awards_1` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `fk_current_awards_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_current_awards_3` FOREIGN KEY (`event_prize_id`) REFERENCES `event_prizes` (`id`),
  CONSTRAINT `fk_current_awards_4` FOREIGN KEY (`draw_result_id`) REFERENCES `draw_results` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `award_cancellations` (
  `id` BINARY(16) NOT NULL,
  `draw_result_id` BINARY(16) NOT NULL,
  `canceled_by` BINARY(16) NOT NULL,
  `replacement_draw_id` BINARY(16) NOT NULL,
  `reason` TEXT NOT NULL,
  `canceled_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_award_cancellations_1` (`draw_result_id`),
  UNIQUE KEY `uq_award_cancellations_2` (`replacement_draw_id`),
  CONSTRAINT `fk_award_cancellations_1` FOREIGN KEY (`draw_result_id`) REFERENCES `draw_results` (`id`),
  CONSTRAINT `fk_award_cancellations_2` FOREIGN KEY (`canceled_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_award_cancellations_3` FOREIGN KEY (`replacement_draw_id`) REFERENCES `draw_runs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `publications` (
  `id` BINARY(16) NOT NULL,
  `event_id` BINARY(16) NOT NULL,
  `draw_run_id` BINARY(16) NOT NULL,
  `published_by` BINARY(16),
  `revision` INT NOT NULL DEFAULT 1,
  `updated_by` BINARY(16),
  `updated_at` DATETIME(6) NOT NULL,
  `published_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_publications_1` (`event_id`),
  CONSTRAINT `fk_publications_1` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `fk_publications_2` FOREIGN KEY (`draw_run_id`) REFERENCES `draw_runs` (`id`),
  CONSTRAINT `fk_publications_3` FOREIGN KEY (`published_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_publications_4` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
