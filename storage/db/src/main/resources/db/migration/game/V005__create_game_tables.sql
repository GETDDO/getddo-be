-- Source: docs/03-database/schema.dbml
-- Tables owned by game. Foreign keys are added in V012.

CREATE TABLE `games` (
  `id` BINARY(16) NOT NULL,
  `code` VARCHAR(50) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `description` TEXT,
  `rules` JSON NOT NULL,
  `rule_version` VARCHAR(30) NOT NULL,
  `is_active` BOOLEAN NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_games_1` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `game_plays` (
  `id` BINARY(16) NOT NULL,
  `game_id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `play_token` VARCHAR(100) NOT NULL,
  `rule_version` VARCHAR(30) NOT NULL,
  `status` ENUM('STARTED', 'VALID', 'INVALID') NOT NULL,
  `score` BIGINT,
  `validation_data` JSON,
  `result_hash` VARCHAR(64),
  `created_at` DATETIME NOT NULL,
  `completed_at` DATETIME,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_game_plays_1` (`play_token`),
  UNIQUE KEY `uq_game_plays_2` (`id`, `user_id`, `game_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_game_stats` (
  `user_id` BINARY(16) NOT NULL,
  `game_id` BINARY(16) NOT NULL,
  `best_score` BIGINT NOT NULL DEFAULT 0,
  `total_score` BIGINT NOT NULL DEFAULT 0,
  `valid_play_count` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME NOT NULL,
  `created_at` DATETIME NOT NULL,
  CONSTRAINT `chk_game_stats` CHECK (best_score >= 0 AND valid_play_count >= 0),
  PRIMARY KEY (`user_id`, `game_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `game_reward_claims` (
  `id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `game_id` BINARY(16) NOT NULL,
  `game_play_id` BINARY(16) NOT NULL,
  `reward_policy_id` BINARY(16) NOT NULL,
  `reward_date` DATE NOT NULL,
  `source_key` VARCHAR(160) NOT NULL,
  `ticket_count` INT NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_game_reward_claims_1` (`user_id`, `source_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
