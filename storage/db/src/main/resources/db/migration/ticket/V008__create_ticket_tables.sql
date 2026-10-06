-- Source: docs/03-database/schema.dbml
-- Initial tables and foreign keys owned by ticket.

-- Allow forward and cyclic references while creating this empty schema.
SET SESSION FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `ticket_wallets` (
  `id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `expiry_month` DATE NOT NULL,
  `valid_from` DATETIME(6) NOT NULL,
  `expires_at` DATETIME(6) NOT NULL,
  `balance` BIGINT NOT NULL DEFAULT 0,
  `status` ENUM('ACTIVE', 'EXPIRED') NOT NULL,
  `version` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  CONSTRAINT `chk_wallet_balance` CHECK (balance >= 0),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ticket_wallets_1` (`user_id`, `expiry_month`),
  UNIQUE KEY `uq_ticket_wallets_2` (`id`, `user_id`),
  CONSTRAINT `fk_ticket_wallets_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ticket_ledger` (
  `id` BINARY(16) NOT NULL,
  `wallet_id` BINARY(16) NOT NULL,
  `actor_id` BINARY(16),
  `event_entry_id` BINARY(16),
  `mission_reward_claim_id` BINARY(16),
  `attendance_reward_claim_id` BINARY(16),
  `game_reward_claim_id` BINARY(16),
  `recovery_target_id` BINARY(16),
  `expires_at` DATETIME(6),
  `refund_of_id` BINARY(16),
  `related_ledger_id` BINARY(16),
  `transaction_type` ENUM('GRANT', 'SPEND', 'REFUND', 'EXPIRE', 'REVOKE', 'CORRECTION') NOT NULL,
  `quantity` BIGINT NOT NULL,
  `idempotency_key` VARCHAR(160) NOT NULL,
  `reason` TEXT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `balance_after` BIGINT NOT NULL,
  `wallet_version` BIGINT NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  CONSTRAINT `chk_ledger_sign` CHECK ((transaction_type IN ('GRANT','REFUND') AND quantity > 0) OR (transaction_type IN ('SPEND','EXPIRE','REVOKE') AND quantity < 0) OR (transaction_type = 'CORRECTION' AND quantity <> 0)),
  CONSTRAINT `chk_ledger_balance_after` CHECK (balance_after >= 0),
  CONSTRAINT `chk_ledger_refund_source` CHECK ((transaction_type = 'REFUND' AND refund_of_id IS NOT NULL) OR (transaction_type <> 'REFUND' AND refund_of_id IS NULL)),
  CONSTRAINT `chk_ledger_recovery_source` CHECK (transaction_type <> 'REVOKE' OR recovery_target_id IS NOT NULL),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ticket_ledger_1` (`idempotency_key`),
  UNIQUE KEY `uq_ticket_ledger_refund_of` (`refund_of_id`),
  UNIQUE KEY `uq_ticket_ledger_3` (`wallet_id`, `wallet_version`),
  KEY `ticket_ledger_user_time_idx` (`user_id`, `created_at`),
  CONSTRAINT `fk_ticket_ledger_1` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_ticket_ledger_2` FOREIGN KEY (`event_entry_id`) REFERENCES `event_entries` (`id`),
  CONSTRAINT `fk_ticket_ledger_3` FOREIGN KEY (`mission_reward_claim_id`) REFERENCES `mission_reward_claims` (`id`),
  CONSTRAINT `fk_ticket_ledger_4` FOREIGN KEY (`attendance_reward_claim_id`) REFERENCES `attendance_reward_claims` (`id`),
  CONSTRAINT `fk_ticket_ledger_5` FOREIGN KEY (`game_reward_claim_id`) REFERENCES `game_reward_claims` (`id`),
  CONSTRAINT `fk_ticket_ledger_6` FOREIGN KEY (`recovery_target_id`) REFERENCES `ticket_recovery_targets` (`id`),
  CONSTRAINT `fk_ticket_ledger_7` FOREIGN KEY (`refund_of_id`) REFERENCES `ticket_ledger` (`id`),
  CONSTRAINT `fk_ticket_ledger_8` FOREIGN KEY (`related_ledger_id`) REFERENCES `ticket_ledger` (`id`),
  CONSTRAINT `fk_ticket_ledger_9` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_ticket_ledger_10` FOREIGN KEY (`wallet_id`, `user_id`) REFERENCES `ticket_wallets` (`id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ticket_ledger_allocations` (
  `id` BINARY(16) NOT NULL,
  `ledger_id` BINARY(16) NOT NULL,
  `source_credit_ledger_id` BINARY(16) NOT NULL,
  `original_grant_id` BINARY(16) NOT NULL,
  `quantity` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ticket_ledger_allocations_1` (`ledger_id`, `source_credit_ledger_id`, `original_grant_id`),
  CONSTRAINT `fk_ticket_ledger_allocations_1` FOREIGN KEY (`ledger_id`) REFERENCES `ticket_ledger` (`id`),
  CONSTRAINT `fk_ticket_ledger_allocations_2` FOREIGN KEY (`source_credit_ledger_id`) REFERENCES `ticket_ledger` (`id`),
  CONSTRAINT `fk_ticket_ledger_allocations_3` FOREIGN KEY (`original_grant_id`) REFERENCES `ticket_ledger` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ticket_refund_jobs` (
  `id` BINARY(16) NOT NULL,
  `spend_ledger_id` BINARY(16) NOT NULL,
  `reason_type` ENUM('EVENT_CANCELED', 'PARTICIPANT_EXCLUDED') NOT NULL,
  `reason` TEXT NOT NULL,
  `status` ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL,
  `target_expires_at` DATETIME(6),
  `attempt_count` INT NOT NULL DEFAULT 0,
  `last_error` TEXT,
  `created_at` DATETIME(6) NOT NULL,
  `completed_at` DATETIME(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ticket_refund_jobs_1` (`spend_ledger_id`),
  CONSTRAINT `fk_ticket_refund_jobs_1` FOREIGN KEY (`spend_ledger_id`) REFERENCES `ticket_ledger` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ticket_recovery_targets` (
  `id` BINARY(16) NOT NULL,
  `abuse_case_id` BINARY(16) NOT NULL,
  `original_grant_id` BINARY(16) NOT NULL,
  `target_quantity` BIGINT NOT NULL,
  `decided_by` BINARY(16) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `reason` TEXT NOT NULL,
  `status` ENUM('ACTIVE', 'SUPERSEDED') NOT NULL,
  `supersedes_target_id` BINARY(16),
  `active_guard` BINARY(16) GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN original_grant_id ELSE NULL END) STORED,
  CONSTRAINT `chk_recovery_quantity` CHECK (target_quantity > 0),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ticket_recovery_targets_1` (`abuse_case_id`, `active_guard`),
  CONSTRAINT `fk_ticket_recovery_targets_1` FOREIGN KEY (`abuse_case_id`) REFERENCES `abuse_cases` (`id`),
  CONSTRAINT `fk_ticket_recovery_targets_2` FOREIGN KEY (`original_grant_id`) REFERENCES `ticket_ledger` (`id`),
  CONSTRAINT `fk_ticket_recovery_targets_3` FOREIGN KEY (`decided_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_ticket_recovery_targets_4` FOREIGN KEY (`supersedes_target_id`) REFERENCES `ticket_recovery_targets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET SESSION FOREIGN_KEY_CHECKS = 1;
