-- Source: docs/03-database/schema.dbml
-- GD-93: Initial schema; version numbers retained by user request.
-- Initial tables and foreign keys owned by ticket.

CREATE TABLE `tickets` (
  `id` binary(16) NOT NULL COMMENT '응모권 한 장 ID. 반환과 재사용 후에도 유지',
  `user_id` binary(16) NOT NULL COMMENT '소유 사용자 ID. 최초 확정 후 변경 금지',
  `attendance_reward_claim_id` binary(16) COMMENT '최초 출석 지급 근거 ID. 다른 보상 종류이면 NULL. 같은 청구로 여러 장 지급 가능. 확정 후 변경 금지',
  `mission_reward_claim_id` binary(16) COMMENT '최초 미션 지급 근거 ID. 다른 보상 종류이면 NULL. 청구당 티켓 1장. 확정 후 변경 금지',
  `game_reward_claim_id` binary(16) COMMENT '최초 게임 지급 근거 ID. 다른 보상 종류이면 NULL. 청구당 티켓 1장. 확정 후 변경 금지',
  `grade` ENUM ('BRONZE', 'SILVER', 'GOLD') NOT NULL COMMENT '최초 지급 등급. 출석 BRONZE 고정, 게임·미션은 최초 확정한 무작위 등급. 이후 불변. 기존 무등급 응모권은 없다고 가정하며 있다면 일괄 BRONZE로 전환',
  `status` ENUM ('AVAILABLE', 'RETURNED', 'SPENT', 'EXPIRED') NOT NULL COMMENT '현재 상태. 최초 사용 가능 / 반환 후 사용 가능 / 사용됨 / 만료 처리됨',
  `expires_at` datetime(6) NOT NULL COMMENT '현재 만료 시각 UTC. 반환 성공 시 갱신',
  `version` bigint NOT NULL DEFAULT 1 COMMENT '현재 변경 순번. 최초 지급 1, 변경마다 1 증가. 마지막 이력 버전과 일치',
  `created_at` datetime(6) NOT NULL COMMENT '최초 지급 시각 UTC. 변경 금지',
  `updated_at` datetime(6) NOT NULL COMMENT '최근 업무 처리 시각 UTC. 현재값 갱신과 함께 반영',
  CONSTRAINT `chk_ticket_source` CHECK ((attendance_reward_claim_id IS NOT NULL) + (mission_reward_claim_id IS NOT NULL) + (game_reward_claim_id IS NOT NULL) = 1),
  CONSTRAINT `chk_ticket_attendance_bronze` CHECK (attendance_reward_claim_id IS NULL OR grade = 'BRONZE'),
  CONSTRAINT `chk_ticket_version` CHECK (version >= 1),
  CONSTRAINT `chk_ticket_times` CHECK (updated_at >= created_at),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_tickets_1` (`mission_reward_claim_id`),
  UNIQUE KEY `uq_tickets_2` (`game_reward_claim_id`),
  CONSTRAINT `fk_tickets_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_tickets_2` FOREIGN KEY (`attendance_reward_claim_id`, `user_id`) REFERENCES `attendance_reward_claims` (`id`, `user_id`),
  CONSTRAINT `fk_tickets_3` FOREIGN KEY (`mission_reward_claim_id`, `user_id`) REFERENCES `mission_reward_claims` (`id`, `user_id`),
  CONSTRAINT `fk_tickets_4` FOREIGN KEY (`game_reward_claim_id`, `user_id`) REFERENCES `game_reward_claims` (`id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ticket_histories` (
  `id` binary(16) NOT NULL COMMENT '응모권 한 장의 처리 이력 ID. 확정 후 수정·삭제 없이 보존',
  `ticket_id` binary(16) NOT NULL COMMENT '처리 대상 응모권 ID. 한 티켓에 여러 버전의 이력이 누적됨',
  `original_use_history_id` binary(16) COMMENT 'REFUND의 원본 USE 이력 ID. 동일 사용 이력은 한 번만 반환. 다른 유형 NULL',
  `corrected_history_id` binary(16) COMMENT 'CORRECTION의 정정 대상 이력 ID. 같은 티켓의 과거 기록. 다른 유형 NULL. 원본 수정 없이 새 이력 추가',
  `event_entry_id` binary(16) COMMENT 'USE 개별 응모 ID. 한 응모에 여러 티켓 사용 가능. 다른 처리 NULL',
  `operation_type` ENUM ('GRANT', 'USE', 'REFUND', 'EXPIRE', 'CORRECTION') NOT NULL COMMENT '지급 / 사용 / 반환 / 만료 / 정정. 반환·정정은 해당 원본 이력에 연결',
  `ticket_version` bigint NOT NULL COMMENT '처리 후 티켓 버전. 최초 GRANT 1, 이후 1씩 증가하는 이력 순서',
  `status` ENUM ('AVAILABLE', 'RETURNED', 'SPENT', 'EXPIRED') NOT NULL COMMENT '처리 결과 상태. 해당 버전 처리 완료 당시 값이며 이후 변경하지 않음',
  `expires_at` datetime(6) NOT NULL COMMENT '처리 결과 만료 시각 UTC. 해당 버전 완료 당시 값이며 이후 변경하지 않음',
  `reason` text NOT NULL COMMENT '처리 사유. 원문 예외·개인정보·비밀값 저장 금지',
  `created_at` datetime(6) NOT NULL COMMENT '실제 처리 시각 UTC. 반환 만료 계산의 기준',
  CONSTRAINT `chk_history_refund_source` CHECK ((operation_type = 'REFUND' AND original_use_history_id IS NOT NULL) OR (operation_type <> 'REFUND' AND original_use_history_id IS NULL)),
  CONSTRAINT `chk_history_correction_source` CHECK ((operation_type = 'CORRECTION' AND corrected_history_id IS NOT NULL) OR (operation_type <> 'CORRECTION' AND corrected_history_id IS NULL)),
  CONSTRAINT `chk_history_no_self_refund` CHECK (original_use_history_id IS NULL OR original_use_history_id <> id),
  CONSTRAINT `chk_history_no_self_correction` CHECK (corrected_history_id IS NULL OR corrected_history_id <> id),
  CONSTRAINT `chk_history_version` CHECK ((operation_type = 'GRANT' AND ticket_version = 1) OR (operation_type <> 'GRANT' AND ticket_version >= 2)),
  CONSTRAINT `chk_history_entry` CHECK ((operation_type = 'USE' AND event_entry_id IS NOT NULL) OR (operation_type <> 'USE' AND event_entry_id IS NULL)),
  CONSTRAINT `chk_history_state` CHECK ((operation_type = 'GRANT' AND status = 'AVAILABLE') OR (operation_type = 'USE' AND status = 'SPENT') OR (operation_type = 'REFUND' AND status = 'RETURNED') OR (operation_type = 'EXPIRE' AND status = 'EXPIRED') OR operation_type = 'CORRECTION'),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ticket_histories_ticket` (`id`, `ticket_id`),
  UNIQUE KEY `uq_ticket_histories_refund_source` (`original_use_history_id`),
  UNIQUE KEY `uq_ticket_histories_1` (`ticket_id`, `ticket_version`),
  UNIQUE KEY `uq_ticket_histories_2` (`event_entry_id`, `ticket_id`),
  CONSTRAINT `fk_ticket_histories_1` FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`),
  CONSTRAINT `fk_ticket_histories_2` FOREIGN KEY (`event_entry_id`) REFERENCES `event_entries` (`id`),
  CONSTRAINT `fk_ticket_histories_3` FOREIGN KEY (`original_use_history_id`, `ticket_id`) REFERENCES `ticket_histories` (`id`, `ticket_id`),
  CONSTRAINT `fk_ticket_histories_4` FOREIGN KEY (`corrected_history_id`, `ticket_id`) REFERENCES `ticket_histories` (`id`, `ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
