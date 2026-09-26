-- Source: docs/03-database/schema.dbml
-- Tables owned by attendance. Foreign keys are added in V012.

CREATE TABLE `attendances` (
  `id` BINARY(16) NOT NULL,
  `users_id` BINARY(16) NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `attendance_streaks` (
  `id` BINARY(16) NOT NULL,
  `users_id` BINARY(16) NOT NULL,
  `policy_set_id` BINARY(16) NOT NULL,
  `streak_month` DATE NOT NULL,
  `consecutive_days` INT NOT NULL,
  `last_attendance_date` DATE NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_attendance_streaks_1` (`users_id`, `streak_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `attendance_streak_policy_sets` (
  `id` BINARY(16) NOT NULL,
  `created_by` BINARY(16) NOT NULL,
  `effective_month` DATE NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_streak_policy_effective_month` (`effective_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `attendance_streak_policies` (
  `id` BINARY(16) NOT NULL,
  `policy_set_id` BINARY(16) NOT NULL,
  `milestone_days` INT NOT NULL,
  `reward_ticket_count` INT NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_streak_policy_milestone` (`policy_set_id`, `milestone_days`)
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
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_attendance_reward_claims_1` (`user_id`, `reward_type`, `source_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
