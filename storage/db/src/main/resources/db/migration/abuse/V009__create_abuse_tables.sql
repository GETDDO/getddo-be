-- Source: docs/03-database/schema.dbml
-- Tables owned by abuse. Foreign keys are added in V012.

CREATE TABLE `abuse_cases` (
  `id` BINARY(16) NOT NULL,
  `event_id` BINARY(16),
  `user_id` BINARY(16) NOT NULL,
  `event_entry_id` BINARY(16),
  `attendance_id` BINARY(16),
  `mission_submission_id` BINARY(16),
  `game_play_id` BINARY(16),
  `reviewed_by` BINARY(16),
  `request_id` VARCHAR(100),
  `detection_key` VARCHAR(160) NOT NULL,
  `detection_type` VARCHAR(50) NOT NULL,
  `detection_reason` TEXT NOT NULL,
  `evidence` JSON,
  `occurred_at` DATETIME NOT NULL,
  `detected_at` DATETIME NOT NULL,
  `review_status` ENUM('PENDING', 'ALLOWED', 'CONFIRMED') NOT NULL,
  `reviewed_at` DATETIME,
  `review_reason` TEXT,
  CONSTRAINT `chk_abuse_one_source` CHECK ((event_entry_id IS NOT NULL) + (attendance_id IS NOT NULL) + (mission_submission_id IS NOT NULL) + (game_play_id IS NOT NULL) <= 1),
  CONSTRAINT `chk_abuse_source_evidence` CHECK ((event_entry_id IS NOT NULL OR attendance_id IS NOT NULL OR mission_submission_id IS NOT NULL OR game_play_id IS NOT NULL) OR (request_id IS NOT NULL AND evidence IS NOT NULL)),
  CONSTRAINT `chk_abuse_review_shape` CHECK ((review_status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL) OR (review_status IN ('ALLOWED','CONFIRMED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_reason IS NOT NULL)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_abuse_detection_key` (`detection_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
