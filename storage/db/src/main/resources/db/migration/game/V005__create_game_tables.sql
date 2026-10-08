-- Source: docs/03-database/schema.dbml
-- GD-93: Initial schema; version numbers retained by user request.
-- Initial tables and foreign keys owned by game.

CREATE TABLE `games` (
  `id` binary(16) NOT NULL COMMENT '게임 ID',
  `code` varchar(50) NOT NULL COMMENT '게임 구분 코드',
  `name` varchar(100) NOT NULL COMMENT '게임명',
  `description` text COMMENT '상세 설명',
  `rules` json NOT NULL COMMENT '게임 규칙',
  `rule_version` varchar(30) NOT NULL COMMENT '적용 게임 규칙 버전',
  `is_active` boolean NOT NULL DEFAULT 1 COMMENT 'true=활성 / false=비활성',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_games_1` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `game_plays` (
  `id` binary(16) NOT NULL COMMENT '게임 플레이 ID',
  `game_id` binary(16) NOT NULL COMMENT '게임 ID',
  `user_id` binary(16) NOT NULL COMMENT '사용자 ID',
  `rule_version` varchar(30) NOT NULL COMMENT '적용 게임 규칙 버전',
  `score` bigint COMMENT '서버가 판정한 게임 점수',
  `result_hash` varchar(64) COMMENT '같은 플레이에 다른 결과 제출을 방지하는 해시',
  `created_at` datetime(6) NOT NULL COMMENT '플레이 시작·행 생성 시각 UTC',
  `completed_at` datetime(6) COMMENT '완료 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_game_plays_1` (`id`, `user_id`, `game_id`),
  CONSTRAINT `fk_game_plays_1` FOREIGN KEY (`game_id`) REFERENCES `games` (`id`),
  CONSTRAINT `fk_game_plays_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_game_stats` (
  `id` binary(16) NOT NULL COMMENT '식별자(UUID)',
  `user_id` binary(16) NOT NULL COMMENT '사용자 ID',
  `game_id` binary(16) NOT NULL COMMENT '게임 ID',
  `best_score` bigint NOT NULL DEFAULT 0 COMMENT '개인 최고 점수',
  `total_score` bigint NOT NULL DEFAULT 0 COMMENT '유효 플레이 누적 점수',
  `valid_play_count` bigint NOT NULL DEFAULT 0 COMMENT '유효 플레이 누적 횟수',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각 UTC',
  `created_at` datetime(6) NOT NULL COMMENT '사용자별 게임 통계 최초 생성 시각 UTC',
  CONSTRAINT `chk_game_stats` CHECK (best_score >= 0 AND valid_play_count >= 0),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_game_stats_1` (`user_id`, `game_id`),
  CONSTRAINT `fk_user_game_stats_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_user_game_stats_2` FOREIGN KEY (`game_id`) REFERENCES `games` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `game_reward_claims` (
  `id` binary(16) NOT NULL COMMENT '게임 보상 지급 ID',
  `user_id` binary(16) NOT NULL COMMENT '보상 대상 사용자 ID',
  `game_id` binary(16) NOT NULL COMMENT '보상 대상 게임 ID',
  `game_play_id` binary(16) NOT NULL COMMENT '유효 플레이 ID',
  `reward_policy_id` binary(16) NOT NULL COMMENT '적용 정책 ID',
  `reward_date` date NOT NULL COMMENT '게임별 일일 보상 기준 날짜 KST',
  `source_key` varchar(160) NOT NULL COMMENT '게임과 KST 날짜로 식별. 같은 플레이 재제출·정책 변경에도 새 보상 건으로 만들지 않음',
  `ticket_count` int NOT NULL COMMENT '보상 수량',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  CONSTRAINT `chk_game_claim_ticket` CHECK (ticket_count = 1),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_game_reward_claims_owner` (`id`, `user_id`),
  UNIQUE KEY `uq_game_reward_claims_play` (`game_play_id`),
  UNIQUE KEY `uq_game_reward_claims_1` (`user_id`, `source_key`),
  UNIQUE KEY `uq_game_reward_claims_2` (`user_id`, `game_id`, `reward_date`),
  CONSTRAINT `fk_game_reward_claims_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_game_reward_claims_2` FOREIGN KEY (`game_id`) REFERENCES `games` (`id`),
  CONSTRAINT `fk_game_reward_claims_3` FOREIGN KEY (`reward_policy_id`) REFERENCES `reward_policies` (`id`),
  CONSTRAINT `fk_game_reward_claims_4` FOREIGN KEY (`game_play_id`, `user_id`, `game_id`) REFERENCES `game_plays` (`id`, `user_id`, `game_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `game_log_batches` (
  `id` binary(16) NOT NULL COMMENT '로그 묶음 ID. 같은 묶음 재전송 시 동일 ID 유지',
  `game_play_id` binary(16) NOT NULL COMMENT '원본 게임 플레이 ID',
  `payload_hash` char(64) NOT NULL COMMENT '서버가 계산한 원본 내용 SHA-256',
  `event_count` int NOT NULL COMMENT '묶음에 포함된 행동 이벤트 수',
  `first_sequence_no` bigint NOT NULL COMMENT '묶음 내 최소 행동 순번',
  `last_sequence_no` bigint NOT NULL COMMENT '묶음 내 최대 행동 순번',
  `s3_bucket` varchar(63) NOT NULL COMMENT '원본을 저장할 S3 버킷',
  `s3_object_key` varchar(1024) NOT NULL COMMENT '원본 객체 키. 경로 기록만으로 저장 완료를 의미하지 않음',
  `storage_status` ENUM ('PENDING', 'STORED', 'FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '원본 저장 대기 / 저장 완료 / 저장 실패',
  `last_error_code` varchar(100) COMMENT '최근 원본 저장 실패 사유 코드',
  `received_at` datetime(6) NOT NULL COMMENT 'API 최초 수신 시각 UTC',
  `stored_at` datetime(6) COMMENT 'S3 저장 성공 확인 시각 UTC',
  `created_at` datetime(6) NOT NULL COMMENT 'DB 기록 생성 시각 UTC',
  `updated_at` datetime(6) NOT NULL COMMENT '최근 상태 변경 시각 UTC',
  CONSTRAINT `chk_batch_count` CHECK (event_count > 0 AND first_sequence_no <= last_sequence_no),
  CONSTRAINT `chk_batch_storage` CHECK ((storage_status = 'STORED' AND stored_at IS NOT NULL) OR (storage_status <> 'STORED' AND stored_at IS NULL)),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_game_log_batches_1` FOREIGN KEY (`game_play_id`) REFERENCES `game_plays` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `game_play_analyses` (
  `id` binary(16) NOT NULL COMMENT '플레이 분석 ID',
  `game_play_id` binary(16) NOT NULL COMMENT '분석 대상 게임 플레이 ID',
  `analyzer_version` varchar(50) NOT NULL COMMENT '분석 로직과 규칙 및 설정을 식별하는 버전',
  `input_hash` char(64) NOT NULL COMMENT '정규화한 분석 입력 목록과 수집 종료 정보의 SHA-256',
  `input_manifest` json NOT NULL COMMENT '분석한 batchId와 payloadHash 목록 및 기대 최종 순번 등 입력 스냅샷',
  `status` ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '분석 작업 대기 / 처리 중 / 완료 / 실패',
  `data_completeness` ENUM ('UNKNOWN', 'PARTIAL', 'COMPLETE') NOT NULL DEFAULT 'UNKNOWN' COMMENT '로그 수집 완전성. 어뷰징 판정과 별개',
  `verdict` ENUM ('NORMAL', 'ABUSE') COMMENT '정상 / 어뷰징. 분석 전과 실패 시 NULL',
  `event_count` int COMMENT '중복 제거 후 분석한 행동 이벤트 수',
  `duplicate_event_count` int COMMENT '분석 입력에서 확인한 중복 이벤트 수',
  `missing_sequence_count` bigint COMMENT '누락 순번 수. 최종 범위를 모르면 NULL',
  `metrics` json COMMENT '클릭 수와 입력 간격 및 반복 패턴 등 분석 지표',
  `detection_reasons` json COMMENT '자동 판정 근거. 규칙 코드와 관측값 및 기준값',
  `last_error_code` varchar(100) COMMENT '최근 분석 작업 실패 사유 코드',
  `started_at` datetime(6) COMMENT '최근 분석 시도 시작 시각 UTC',
  `completed_at` datetime(6) COMMENT '분석 성공 완료 시각 UTC',
  `created_at` datetime(6) NOT NULL COMMENT '분석 작업 생성 시각 UTC',
  `updated_at` datetime(6) NOT NULL COMMENT '최근 상태 변경 시각 UTC',
  CONSTRAINT `chk_analysis_result` CHECK ((status = 'COMPLETED' AND verdict IS NOT NULL AND completed_at IS NOT NULL) OR (status <> 'COMPLETED' AND verdict IS NULL AND completed_at IS NULL)),
  CONSTRAINT `chk_analysis_counts` CHECK ((event_count IS NULL OR event_count >= 0) AND (duplicate_event_count IS NULL OR duplicate_event_count >= 0) AND (missing_sequence_count IS NULL OR missing_sequence_count >= 0)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_game_play_analyses_input` (`game_play_id`, `analyzer_version`, `input_hash`),
  CONSTRAINT `fk_game_play_analyses_1` FOREIGN KEY (`game_play_id`) REFERENCES `game_plays` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
