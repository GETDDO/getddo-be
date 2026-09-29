-- Source: docs/03-database/schema.dbml
-- Initial tables and foreign keys owned by attendance.

-- Allow forward and cyclic references while creating this empty schema.
SET SESSION FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `attendances` (
  `id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `attendance_date` DATE NOT NULL COMMENT '출석 기준 날짜 KST',
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_attendances_1` (`user_id`, `attendance_date`),
  CONSTRAINT `fk_attendances_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `attendance_streaks` (
  `id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `policy_set_id` BINARY(16) NOT NULL,
  `streak_month` DATE NOT NULL,
  `consecutive_days` INT NOT NULL,
  `last_attendance_date` DATE NOT NULL,
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_attendance_streaks_1` (`user_id`, `streak_month`),
  CONSTRAINT `chk_streak_days` CHECK (`consecutive_days` BETWEEN 0 AND 31),
  CONSTRAINT `fk_attendance_streaks_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_attendance_streaks_2` FOREIGN KEY (`policy_set_id`) REFERENCES `attendance_streak_policy_sets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `attendance_streak_policy_sets` (
  `id` BINARY(16) NOT NULL,
  `created_by` BINARY(16) NOT NULL,
  `effective_month` DATE NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_streak_policy_effective_month` (`effective_month`),
  CONSTRAINT `fk_attendance_streak_policy_sets_1` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `attendance_streak_policies` (
  `id` BINARY(16) NOT NULL,
  `policy_set_id` BINARY(16) NOT NULL,
  `milestone_days` INT NOT NULL,
  `reward_ticket_count` INT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_streak_policy_milestone` (`policy_set_id`, `milestone_days`),
  CONSTRAINT `chk_streak_policy_milestone` CHECK (`milestone_days` BETWEEN 1 AND 28),
  CONSTRAINT `chk_streak_policy_reward` CHECK (`reward_ticket_count` >= 1),
  CONSTRAINT `fk_attendance_streak_policies_1` FOREIGN KEY (`policy_set_id`) REFERENCES `attendance_streak_policy_sets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `attendance_reward_claims` (
  `id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `attendance_id` BINARY(16) NOT NULL,
  `reward_type` ENUM('DAILY', 'STREAK') NOT NULL,
  `reward_policy_id` BINARY(16),
  `attendance_streak_policy_id` BINARY(16),
  `reward_date` DATE NOT NULL,
  `milestone_days` INT,
  `source_key` VARCHAR(160) NOT NULL,
  `ticket_count` INT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_attendance_reward_claims_1` (`user_id`, `reward_type`, `source_key`),
  CONSTRAINT `chk_attendance_claim_shape` CHECK (
    (`reward_type` = 'DAILY'
       AND `reward_policy_id` IS NOT NULL
       AND `attendance_streak_policy_id` IS NULL
       AND `milestone_days` IS NULL)
    OR
    (`reward_type` = 'STREAK'
       AND `attendance_streak_policy_id` IS NOT NULL
       AND `milestone_days` IS NOT NULL
       AND `reward_policy_id` IS NULL)
  ),
  CONSTRAINT `chk_attendance_claim_ticket` CHECK (`ticket_count` >= 1),
  CONSTRAINT `fk_attendance_reward_claims_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_attendance_reward_claims_2` FOREIGN KEY (`attendance_id`) REFERENCES `attendances` (`id`),
  CONSTRAINT `fk_attendance_reward_claims_3` FOREIGN KEY (`reward_policy_id`) REFERENCES `reward_policies` (`id`),
  CONSTRAINT `fk_attendance_reward_claims_4` FOREIGN KEY (`attendance_streak_policy_id`) REFERENCES `attendance_streak_policies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET SESSION FOREIGN_KEY_CHECKS = 1;
