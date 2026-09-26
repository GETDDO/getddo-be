-- Source: docs/03-database/schema.dbml
-- Tables owned by reward. Foreign keys are added in V012.

CREATE TABLE `reward_policies` (
  `id` BINARY(16) NOT NULL,
  `created_by` BINARY(16) NOT NULL,
  `reward_type` ENUM('ATTENDANCE', 'MISSION', 'GAME') NOT NULL,
  `game_id` BINARY(16),
  `reward_ticket_count` INT NOT NULL,
  `effective_from` DATETIME NOT NULL,
  `effective_until` DATETIME,
  `created_at` DATETIME NOT NULL,
  `open_guard` BINARY(16) GENERATED ALWAYS AS (CASE WHEN effective_until IS NULL AND reward_type = 'ATTENDANCE' THEN UNHEX(REPEAT('00', 16)) WHEN effective_until IS NULL AND reward_type = 'GAME' THEN game_id ELSE NULL END) VIRTUAL,
  CONSTRAINT `chk_reward_policy_game` CHECK ((reward_type = 'GAME' AND game_id IS NOT NULL) OR (reward_type <> 'GAME' AND game_id IS NULL)),
  CONSTRAINT `chk_reward_policy_period` CHECK (effective_until IS NULL OR effective_until > effective_from),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_reward_policies_1` (`reward_type`, `open_guard`),
  UNIQUE KEY `uq_game_policy_effective_from` (`game_id`, `effective_from`),
  KEY `reward_policy_effective_idx` (`reward_type`, `effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
