-- Source: docs/03-database/schema.dbml
-- Tables owned by event. Foreign keys are added in V012.

CREATE TABLE `events` (
  `id` BINARY(16) NOT NULL,
  `created_by` BINARY(16) NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `description` TEXT NOT NULL,
  `image_key` VARCHAR(500),
  `event_type` ENUM('NO_TICKET', 'TICKET') NOT NULL,
  `weighting_enabled` BOOLEAN NOT NULL DEFAULT 0,
  `max_tickets_per_user` INT,
  `starts_at` DATETIME NOT NULL,
  `ends_at` DATETIME NOT NULL,
  `status` ENUM('SCHEDULED', 'OPEN', 'CLOSED', 'DRAW_CONFIRMED', 'PUBLISHED', 'SUSPENDED', 'CANCELED', 'REDRAWING', 'NO_ENTRANTS', 'NO_ELIGIBLE_ENTRANTS') NOT NULL,
  `suspended_from_status` ENUM('SCHEDULED', 'OPEN'),
  `suspended_at` DATETIME,
  `canceled_at` DATETIME,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `membership_rule` ENUM('excellent', 'vip', 'vvip') NOT NULL,
  CONSTRAINT `chk_event_ticket_limit` CHECK (max_tickets_per_user IS NULL OR max_tickets_per_user > 0),
  CONSTRAINT `chk_event_dates` CHECK (ends_at > starts_at),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `event_prizes` (
  `id` BINARY(16) NOT NULL,
  `event_id` BINARY(16) NOT NULL,
  `prize_rank` INT NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `description` TEXT,
  `image_key` VARCHAR(500),
  `winner_count` INT NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  CONSTRAINT `chk_event_prize_winner_count` CHECK (winner_count > 0),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_event_prizes_1` (`event_id`, `prize_rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `event_participants` (
  `id` BINARY(16) NOT NULL,
  `event_id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `eligibility_status` ENUM('ELIGIBLE', 'EXCLUDED') NOT NULL,
  `exclusion_reason` TEXT,
  `excluded_at` DATETIME,
  `created_at` DATETIME NOT NULL,
  `used_ticket_count` BIGINT NOT NULL DEFAULT 0,
  `exclusion_reason_code` ENUM('ABUSE', 'INELIGIBLE'),
  CONSTRAINT `chk_participant_exclusion` CHECK ((eligibility_status = 'ELIGIBLE' AND exclusion_reason_code IS NULL AND exclusion_reason IS NULL AND excluded_at IS NULL) OR (eligibility_status = 'EXCLUDED' AND exclusion_reason_code IS NOT NULL AND exclusion_reason IS NOT NULL AND excluded_at IS NOT NULL)),
  CONSTRAINT `chk_participant_used` CHECK (used_ticket_count >= 0),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_event_participants_1` (`event_id`, `user_id`),
  UNIQUE KEY `uq_event_participants_2` (`id`, `event_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `event_entries` (
  `id` BINARY(16) NOT NULL,
  `event_id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `participant_id` BINARY(16),
  `idempotency_key` VARCHAR(100) NOT NULL,
  `requested_ticket_count` INT NOT NULL,
  `deducted_ticket_count` INT NOT NULL DEFAULT 0,
  `status` ENUM('ACCEPTED', 'REJECTED') NOT NULL,
  `rejection_code` VARCHAR(50),
  `rejection_reason` VARCHAR(500),
  `single_entry_guard` INT,
  `requested_at` DATETIME NOT NULL,
  `accepted_at` DATETIME,
  `created_at` DATETIME NOT NULL,
  CONSTRAINT `chk_entry_requested` CHECK (requested_ticket_count >= 0),
  CONSTRAINT `chk_entry_deducted` CHECK (deducted_ticket_count >= 0),
  CONSTRAINT `chk_entry_other_reason` CHECK (rejection_code IS NULL OR rejection_code <> 'OTHER' OR (rejection_reason IS NOT NULL AND TRIM(rejection_reason) <> '')),
  CONSTRAINT `chk_entry_shape` CHECK ((status = 'REJECTED' AND participant_id IS NULL AND accepted_at IS NULL AND rejection_code IS NOT NULL) OR (status = 'ACCEPTED' AND participant_id IS NOT NULL AND accepted_at IS NOT NULL AND accepted_at = created_at)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_event_entries_1` (`event_id`, `user_id`, `idempotency_key`),
  UNIQUE KEY `uq_event_entries_2` (`event_id`, `user_id`, `single_entry_guard`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `banners` (
  `id` BINARY(16) NOT NULL,
  `event_id` BINARY(16) NOT NULL,
  `created_by` BINARY(16) NOT NULL,
  `image_key` VARCHAR(500) NOT NULL,
  `display_order` INT NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
