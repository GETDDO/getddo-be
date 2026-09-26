-- Source: docs/03-database/schema.dbml
-- Tables owned by ticket. Foreign keys are added in V012.

CREATE TABLE `ticket_wallets` (
  `id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `expiry_month` DATE NOT NULL,
  `valid_from` DATETIME NOT NULL,
  `expires_at` DATETIME NOT NULL,
  `balance` BIGINT NOT NULL DEFAULT 0,
  `status` ENUM('ACTIVE', 'EXPIRED') NOT NULL,
  `version` BIGINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  CONSTRAINT `chk_wallet_balance` CHECK (balance >= 0),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ticket_wallets_1` (`user_id`, `expiry_month`),
  UNIQUE KEY `uq_ticket_wallets_2` (`id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ticket_ledger` (
  `id` BINARY(16) NOT NULL,
  `wallet_id` BINARY(16) NOT NULL,
  `actor_id` BINARY(16),
  `event_entry_id` BINARY(16),
  `reward_claim_id` BINARY(16),
  `attendance_reward_claim_id` BINARY(16),
  `game_reward_claim_id` BINARY(16),
  `recovery_target_id` BINARY(16),
  `expires_at` DATETIME,
  `refund_of_id` BINARY(16),
  `related_ledger_id` BINARY(16),
  `transaction_type` ENUM('GRANT', 'SPEND', 'REFUND', 'EXPIRE', 'REVOKE', 'CORRECTION') NOT NULL,
  `quantity` BIGINT NOT NULL,
  `idempotency_key` VARCHAR(160) NOT NULL,
  `reason` TEXT NOT NULL,
  `created_at` DATETIME NOT NULL,
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
  KEY `ticket_ledger_user_time_idx` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ticket_ledger_allocations` (
  `ledger_id` BINARY(16) NOT NULL,
  `source_credit_ledger_id` BINARY(16) NOT NULL,
  `original_grant_id` BINARY(16) NOT NULL,
  `quantity` BIGINT NOT NULL,
  PRIMARY KEY (`ledger_id`, `source_credit_ledger_id`, `original_grant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ticket_refund_jobs` (
  `id` BINARY(16) NOT NULL,
  `spend_ledger_id` BINARY(16) NOT NULL,
  `reason_type` ENUM('EVENT_CANCELED', 'PARTICIPANT_EXCLUDED') NOT NULL,
  `reason` TEXT NOT NULL,
  `status` ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL,
  `target_expires_at` DATETIME,
  `attempt_count` INT NOT NULL DEFAULT 0,
  `last_error` TEXT,
  `created_at` DATETIME NOT NULL,
  `completed_at` DATETIME,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ticket_refund_jobs_1` (`spend_ledger_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ticket_recovery_targets` (
  `id` BINARY(16) NOT NULL,
  `abuse_case_id` BINARY(16) NOT NULL,
  `original_grant_id` BINARY(16) NOT NULL,
  `target_quantity` BIGINT NOT NULL,
  `decided_by` BINARY(16) NOT NULL,
  `created_at` DATETIME NOT NULL,
  `reason` TEXT NOT NULL,
  `status` ENUM('ACTIVE', 'SUPERSEDED') NOT NULL,
  `supersedes_target_id` BINARY(16),
  `active_guard` BINARY(16) GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN original_grant_id ELSE NULL END) STORED,
  CONSTRAINT `chk_recovery_quantity` CHECK (target_quantity > 0),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ticket_recovery_targets_1` (`abuse_case_id`, `active_guard`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
