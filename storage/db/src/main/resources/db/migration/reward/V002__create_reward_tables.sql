-- Source: docs/03-database/schema.dbml
-- Initial tables and foreign keys owned by reward.

-- Allow forward and cyclic references while creating this empty schema.
SET SESSION FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `reward_policies` (
  `id` BINARY(16) NOT NULL,
  `created_by` BINARY(16) NOT NULL,
  `reward_type` ENUM('ATTENDANCE', 'MISSION', 'GAME') NOT NULL,
  `game_id` BINARY(16),
  `reward_ticket_count` INT NOT NULL,
  `effective_from` DATETIME(6) NOT NULL,
  `effective_until` DATETIME(6),
  `created_at` DATETIME(6) NOT NULL,
  `open_guard` BINARY(16) GENERATED ALWAYS AS (CASE WHEN effective_until IS NULL AND reward_type = 'ATTENDANCE' THEN UNHEX(REPEAT('00', 16)) WHEN effective_until IS NULL AND reward_type = 'GAME' THEN game_id ELSE NULL END) VIRTUAL,
  CONSTRAINT `chk_reward_policy_game` CHECK ((reward_type = 'GAME' AND game_id IS NOT NULL) OR (reward_type <> 'GAME' AND game_id IS NULL)),
  CONSTRAINT `chk_reward_policy_period` CHECK (effective_until IS NULL OR effective_until > effective_from),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_reward_policies_1` (`reward_type`, `open_guard`),
  UNIQUE KEY `uq_game_policy_effective_from` (`game_id`, `effective_from`),
  KEY `reward_policy_effective_idx` (`reward_type`, `effective_from`),
  CONSTRAINT `fk_reward_policies_1` FOREIGN KEY (`game_id`) REFERENCES `games` (`id`),
  CONSTRAINT `fk_reward_policies_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET SESSION FOREIGN_KEY_CHECKS = 1;
