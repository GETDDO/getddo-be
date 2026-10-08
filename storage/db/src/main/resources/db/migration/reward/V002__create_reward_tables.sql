-- Source: docs/03-database/schema.dbml
-- GD-93: Initial schema; version numbers retained by user request.
-- Initial tables and foreign keys owned by reward.

-- Allow forward references while creating this empty schema.
SET SESSION FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `reward_policies` (
  `id` binary(16) NOT NULL COMMENT '보상 정책 ID',
  `created_by` binary(16) NOT NULL COMMENT '등록 관리자 ID',
  `reward_type` ENUM ('ATTENDANCE', 'MISSION', 'GAME') NOT NULL COMMENT 'ATTENDANCE=출석 / MISSION=미션 / GAME=게임',
  `game_id` binary(16) COMMENT 'GAME 정책일 때 필수',
  `reward_ticket_count` int NOT NULL COMMENT '보상으로 지급할 응모권 수',
  `effective_from` datetime(6) NOT NULL COMMENT '정책 적용 시작 시각 UTC',
  `effective_until` datetime(6) COMMENT '정책 적용 종료 시각 UTC',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  `open_guard` BINARY(16) GENERATED ALWAYS AS (CASE WHEN effective_until IS NULL AND reward_type = 'ATTENDANCE' THEN UNHEX(REPEAT('00', 16)) WHEN effective_until IS NULL AND reward_type = 'GAME' THEN game_id ELSE NULL END) VIRTUAL,
  `reward_ticket_type` ENUM ('GOLD', 'SILVER', 'BRONZE') COMMENT '보상 등급 설정의 역할은 보완 필요. 출석 BRONZE 고정, 게임·미션은 무작위 지급이므로 고정 결과로 해석하지 않음',
  CONSTRAINT `chk_reward_policy_game` CHECK ((reward_type = 'GAME' AND game_id IS NOT NULL) OR (reward_type <> 'GAME' AND game_id IS NULL)),
  CONSTRAINT `chk_reward_policy_period` CHECK (effective_until IS NULL OR effective_until > effective_from),
  CONSTRAINT `chk_reward_policy_quantity` CHECK (reward_ticket_count >= 1),
  CONSTRAINT `chk_reward_current_quantity` CHECK ((reward_type = 'ATTENDANCE' AND reward_ticket_count >= 1) OR (reward_type IN ('MISSION','GAME') AND reward_ticket_count = 1)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_reward_policies_1` (`reward_type`, `open_guard`),
  UNIQUE KEY `uq_game_policy_effective_from` (`game_id`, `effective_from`),
  KEY `reward_policy_effective_idx` (`reward_type`, `effective_from`),
  CONSTRAINT `fk_reward_policies_1` FOREIGN KEY (`game_id`) REFERENCES `games` (`id`),
  CONSTRAINT `fk_reward_policies_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET SESSION FOREIGN_KEY_CHECKS = 1;
