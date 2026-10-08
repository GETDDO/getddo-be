-- Source: docs/03-database/schema.dbml
-- GD-93: Initial schema; version numbers retained by user request.
-- Initial tables and foreign keys owned by attendance.

CREATE TABLE `attendance_streak_policy_sets` (
  `id` binary(16) NOT NULL COMMENT '연속 출석 정책 묶음 ID',
  `created_by` binary(16) NOT NULL COMMENT '등록 관리자 ID',
  `effective_month` date NOT NULL COMMENT '적용 시작월의 1일 KST',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_streak_policy_effective_month` (`effective_month`),
  CONSTRAINT `fk_attendance_streak_policy_sets_1` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `attendance_streak_policies` (
  `id` binary(16) NOT NULL COMMENT '연속 출석 단계 정책 ID',
  `policy_set_id` binary(16) NOT NULL COMMENT '소속 정책 묶음 ID',
  `milestone_days` int NOT NULL COMMENT '연속 출석 기준 일수 1~28',
  `reward_ticket_count` int NOT NULL COMMENT '단계 달성 시 지급 수량',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  CONSTRAINT `chk_streak_policy_milestone` CHECK (milestone_days BETWEEN 1 AND 28),
  CONSTRAINT `chk_streak_policy_reward` CHECK (reward_ticket_count >= 1),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_streak_policy_milestone` (`policy_set_id`, `milestone_days`),
  CONSTRAINT `fk_attendance_streak_policies_1` FOREIGN KEY (`policy_set_id`) REFERENCES `attendance_streak_policy_sets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `attendances` (
  `id` binary(16) NOT NULL COMMENT '출석 ID',
  `user_id` binary(16) NOT NULL COMMENT '사용자 ID',
  `attendance_date` date NOT NULL COMMENT '출석 기준 날짜 KST',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_attendances_1` (`user_id`, `attendance_date`),
  CONSTRAINT `fk_attendances_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `attendance_streaks` (
  `id` binary(16) NOT NULL COMMENT '월별 연속 출석 상태 ID',
  `user_id` binary(16) NOT NULL COMMENT '사용자 ID',
  `policy_set_id` binary(16) NOT NULL COMMENT '해당 월에 적용한 연속 출석 정책 묶음 ID',
  `streak_month` date NOT NULL COMMENT 'KST 기준 출석 기준월의 1일',
  `consecutive_days` int NOT NULL COMMENT '마지막 출석일까지 이어진 연속 출석 일수',
  `last_attendance_date` date NOT NULL COMMENT '마지막 출석 인정 날짜 KST',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  `updated_at` datetime(6) NOT NULL COMMENT '연속 출석 상태 갱신 시각 UTC',
  CONSTRAINT `chk_streak_days` CHECK (consecutive_days BETWEEN 0 AND 31),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_attendance_streaks_1` (`user_id`, `streak_month`),
  CONSTRAINT `fk_attendance_streaks_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_attendance_streaks_2` FOREIGN KEY (`policy_set_id`) REFERENCES `attendance_streak_policy_sets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `attendance_reward_claims` (
  `id` binary(16) NOT NULL COMMENT '출석 보상 지급 ID',
  `attendance_id` binary(16) NOT NULL COMMENT '보상 근거 출석 기록 ID',
  `reward_policy_id` binary(16) COMMENT 'DAILY일 때 필수',
  `attendance_streak_policy_id` binary(16) COMMENT 'STREAK일 때 필수',
  `user_id` binary(16) NOT NULL COMMENT '보상 대상 사용자 ID',
  `reward_type` ENUM ('DAILY', 'STREAK') NOT NULL COMMENT 'DAILY=기본 / STREAK=연속 출석',
  `reward_date` date NOT NULL COMMENT '보상 기준 날짜 KST',
  `milestone_days` int COMMENT '연속 출석 달성 단계 일수',
  `source_key` varchar(160) NOT NULL COMMENT '사용자·종류별 고정 업무 키. DAILY YYYY-MM-DD, STREAK YYYY-MM:단계일수. 정책 변경·재시도에도 유지',
  `ticket_count` int NOT NULL COMMENT '실제 지급 수량',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  CONSTRAINT `chk_attendance_claim_shape` CHECK ((reward_type = 'DAILY' AND reward_policy_id IS NOT NULL AND attendance_streak_policy_id IS NULL AND milestone_days IS NULL) OR (reward_type = 'STREAK' AND attendance_streak_policy_id IS NOT NULL AND milestone_days IS NOT NULL AND reward_policy_id IS NULL)),
  CONSTRAINT `chk_attendance_claim_ticket` CHECK (ticket_count >= 1),
  CONSTRAINT `chk_attendance_milestone` CHECK (milestone_days IS NULL OR milestone_days BETWEEN 1 AND 28),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_attendance_reward_claims_owner` (`id`, `user_id`),
  UNIQUE KEY `uq_attendance_reward_claims_1` (`user_id`, `reward_type`, `source_key`),
  CONSTRAINT `fk_attendance_reward_claims_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_attendance_reward_claims_2` FOREIGN KEY (`attendance_id`) REFERENCES `attendances` (`id`),
  CONSTRAINT `fk_attendance_reward_claims_3` FOREIGN KEY (`reward_policy_id`) REFERENCES `reward_policies` (`id`),
  CONSTRAINT `fk_attendance_reward_claims_4` FOREIGN KEY (`attendance_streak_policy_id`) REFERENCES `attendance_streak_policies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
