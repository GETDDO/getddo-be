-- Source: docs/03-database/schema.dbml
-- GD-93: Initial schema; version numbers retained by user request.
-- Initial tables and foreign keys owned by abuse.

CREATE TABLE `abuse_cases` (
  `id` binary(16) NOT NULL COMMENT '어뷰징 탐지 ID',
  `event_id` binary(16) COMMENT '관련 이벤트 ID',
  `user_id` binary(16) NOT NULL COMMENT '사용자 ID',
  `attendance_id` binary(16) COMMENT '원본 출석 기록 ID',
  `mission_submission_id` binary(16) COMMENT '원본 미션 제출 ID',
  `game_play_id` binary(16) COMMENT '원본 게임 플레이 ID',
  `reviewed_by` binary(16) COMMENT 'ALLOWED·CONFIRMED에서 필수, PENDING에서는 NULL',
  `request_id` varchar(100) COMMENT '관련 HTTP 요청 추적 ID',
  `detection_key` varchar(160) NOT NULL COMMENT '탐지 건 중복 저장 방지 키',
  `detection_type` varchar(50) NOT NULL COMMENT '탐지 유형 코드',
  `detection_reason` text NOT NULL COMMENT '탐지 사유',
  `evidence` json COMMENT '탐지 근거',
  `occurred_at` datetime(6) NOT NULL COMMENT '탐지 대상 행위 발생 시각 UTC',
  `detected_at` datetime(6) NOT NULL COMMENT '탐지 시각 UTC',
  `review_status` ENUM ('PENDING', 'ALLOWED', 'CONFIRMED') NOT NULL COMMENT 'PENDING=검토 전',
  `reviewed_at` datetime(6) COMMENT 'ALLOWED·CONFIRMED에서 필수, PENDING에서는 NULL',
  `review_reason` text COMMENT 'ALLOWED·CONFIRMED에서 필수, PENDING에서는 NULL',
  CONSTRAINT `chk_abuse_review_shape` CHECK ((review_status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL) OR (review_status IN ('ALLOWED','CONFIRMED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_reason IS NOT NULL)),
  CONSTRAINT `chk_abuse_one_source` CHECK ((attendance_id IS NOT NULL) + (mission_submission_id IS NOT NULL) + (game_play_id IS NOT NULL) <= 1),
  CONSTRAINT `chk_abuse_source_evidence` CHECK ((attendance_id IS NOT NULL OR mission_submission_id IS NOT NULL OR game_play_id IS NOT NULL) OR (request_id IS NOT NULL AND evidence IS NOT NULL)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_abuse_detection_key` (`detection_key`),
  CONSTRAINT `fk_abuse_cases_1` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `fk_abuse_cases_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_abuse_cases_3` FOREIGN KEY (`attendance_id`) REFERENCES `attendances` (`id`),
  CONSTRAINT `fk_abuse_cases_4` FOREIGN KEY (`mission_submission_id`) REFERENCES `mission_submissions` (`id`),
  CONSTRAINT `fk_abuse_cases_5` FOREIGN KEY (`game_play_id`) REFERENCES `game_plays` (`id`),
  CONSTRAINT `fk_abuse_cases_6` FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
