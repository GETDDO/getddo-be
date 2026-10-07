-- GETDDO 최신 전체 ERD 설계 초안. 42개 테이블. 빈 스키마/ERDCloud용 CREATE 정의.
-- 운영 DB에 직접 실행하는 마이그레이션이 아님. DROP/기존 Flyway 변경 없음.
-- DBML의 CHECK·생성식을 실제 SQL로 함께 정의. ERDCloud의 표시/보존 여부는 별도 확인.
-- 보완 필요: erd-constraint-update-review.md. 회수 제외는 사용자 지시, 공용 스펙 갱신 대기.

CREATE TABLE `game_plays` (
  `id` BINARY(16) NOT NULL COMMENT '게임 플레이 ID',
  `game_id` BINARY(16) NOT NULL COMMENT '게임 ID',
  `user_id` BINARY(16) NOT NULL COMMENT '사용자 ID',
  `rule_version` VARCHAR(30) NOT NULL COMMENT '적용 게임 규칙 버전',
  `score` BIGINT NULL COMMENT '서버가 판정한 게임 점수',
  `result_hash` VARCHAR(64) NULL COMMENT '같은 플레이에 다른 결과 제출을 방지하는 해시',
  `created_at` DATETIME(6) NOT NULL COMMENT '플레이 시작·행 생성 시각 UTC',
  `completed_at` DATETIME(6) NULL COMMENT '완료 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_game_plays_1` (`id`, `user_id`, `game_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `attendances` (
  `id` BINARY(16) NOT NULL COMMENT '출석 ID',
  `user_id` BINARY(16) NOT NULL COMMENT '사용자 ID',
  `attendance_date` DATE NOT NULL COMMENT '출석 기준 날짜 KST',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_attendances_1` (`user_id`, `attendance_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_game_stats` (
  `id` BINARY(16) NOT NULL COMMENT '식별자(UUID)',
  `user_id` BINARY(16) NOT NULL COMMENT '사용자 ID',
  `game_id` BINARY(16) NOT NULL COMMENT '게임 ID',
  `best_score` BIGINT NOT NULL DEFAULT 0 COMMENT '개인 최고 점수',
  `total_score` BIGINT NOT NULL DEFAULT 0 COMMENT '유효 플레이 누적 점수',
  `valid_play_count` BIGINT NOT NULL DEFAULT 0 COMMENT '유효 플레이 누적 횟수',
  `updated_at` DATETIME(6) NOT NULL COMMENT '수정 시각 UTC',
  `created_at` DATETIME(6) NOT NULL COMMENT '사용자별 게임 통계 최초 생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_game_stats_1` (`user_id`, `game_id`),
  CONSTRAINT `chk_game_stats` CHECK (best_score >= 0 AND valid_play_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_run_candidates` (
  `draw_run_id` BINARY(16) NOT NULL COMMENT '후보가 참여한 추첨 실행 ID. 최초 및 재추첨 실행',
  `candidate_id` BINARY(16) NOT NULL COMMENT '재사용하는 최초 추첨 후보 ID',
  PRIMARY KEY (`draw_run_id`, `candidate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `missions` (
  `id` BINARY(16) NOT NULL COMMENT '미션 ID',
  `reward_policy_id` BINARY(16) NOT NULL COMMENT '적용 보상 정책 ID',
  `created_by` BINARY(16) NOT NULL COMMENT '등록 관리자 ID',
  `title` VARCHAR(200) NOT NULL COMMENT '미션 제목',
  `description` TEXT NOT NULL COMMENT '상세 설명',
  `mission_type` ENUM('SURVEY', 'QUIZ') NOT NULL COMMENT 'SURVEY=설문 / QUIZ=퀴즈',
  `starts_at` DATETIME(6) NOT NULL COMMENT '미션 운영 시작 시각 UTC',
  `ends_at` DATETIME(6) NOT NULL COMMENT '미션 운영 종료 시각 UTC',
  `status` ENUM('ACTIVE', 'ENDED') NOT NULL COMMENT 'ACTIVE=운영 중 / ENDED=종료',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  `updated_at` DATETIME(6) NOT NULL COMMENT '수정 시각 UTC',
  `image_key` VARCHAR(500) NULL COMMENT '미션 설명 이미지 한 개의 저장소 경로',
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_mission_dates` CHECK (ends_at > starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `award_cancellations` (
  `id` BINARY(16) NOT NULL COMMENT '당첨 취소 ID',
  `draw_result_id` BINARY(16) NOT NULL COMMENT '취소한 SELECTED 추첨 결과 ID',
  `draw_run_id` BINARY(16) NULL COMMENT '취소 저장 시 NULL, 재추첨 등록 시 설정 후 변경·해제 금지. 여러 취소가 같은 실행 참조 가능',
  `canceled_by` BINARY(16) NOT NULL COMMENT '당첨 취소를 확정한 관리자 ID',
  `reason` TEXT NOT NULL COMMENT '당첨 취소 사유',
  `canceled_at` DATETIME(6) NOT NULL COMMENT '당첨 취소 확정 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_award_cancellations_1` (`draw_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `survey_answers` (
  `id` BINARY(16) NOT NULL COMMENT '설문 응답 ID',
  `submission_id` BINARY(16) NOT NULL COMMENT '최종 제출 ID',
  `mission_id` BINARY(16) NOT NULL COMMENT '제출과 문항의 소속 미션',
  `survey_question_id` BINARY(16) NOT NULL COMMENT '설문 문항 ID',
  `selected_option_id` BINARY(16) NULL COMMENT '단일 선택 답변',
  `answer_text` TEXT NULL COMMENT '자유 입력 답변',
  `created_at` DATETIME(6) NOT NULL COMMENT '응답 저장 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_survey_answers_1` (`submission_id`, `survey_question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_candidates` (
  `id` BINARY(16) NOT NULL COMMENT '최초 추첨 후보 ID. 재추첨에서도 같은 후보 정보를 재사용',
  `participant_id` BINARY(16) NOT NULL COMMENT '원본 이벤트 응모자 관계 ID',
  `draw_run_id` BINARY(16) NOT NULL COMMENT '최초 후보 정보를 고정한 추첨 실행 ID. 재추첨 참여 실행은 draw_run_candidates에 연결',
  `ticket_count` BIGINT NOT NULL COMMENT '해당 이벤트에서 접수 완료된 응모의 실제 누적 차감 수량. 재추첨은 최초 추첨 당시 값',
  `weight` BIGINT NOT NULL COMMENT '해당 응모자의 추첨 가중치. 현재 선형 가중치 정책의 정수 값. 추가 배율 없음.',
  `entry_snapshot` JSON NOT NULL COMMENT '확정 응모 근거 스냅샷. 응모 ID·실제 장수·접수 시각·등급별 사용량·적용 가중치. 재추첨 시 최초 값 유지',
  `eligibility_snapshot` JSON NOT NULL COMMENT '후보 확정 당시 응모 자격과 제외 판단 스냅샷. 명단 확정 당시 자격·제외 판단 및 재추첨이면 최초 후보 ID.',
  `created_at` DATETIME(6) NOT NULL COMMENT '후보 스냅샷 생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_draw_candidates_1` (`draw_run_id`, `participant_id`),
  CONSTRAINT `chk_candidate_counts` CHECK (ticket_count >= 0 AND weight >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `event_participants` (
  `id` BINARY(16) NOT NULL COMMENT '이벤트 응모자 ID',
  `event_id` BINARY(16) NOT NULL COMMENT '연결 이벤트 ID',
  `user_id` BINARY(16) NOT NULL COMMENT '사용자 ID',
  `created_at` DATETIME(6) NOT NULL COMMENT '최초 응모 접수 완료·행 생성 시각 UTC',
  `used_ticket_count` BIGINT NOT NULL DEFAULT 0 COMMENT '사용자별 이벤트 누적 차감 응모권 수',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_event_participants_1` (`event_id`, `user_id`),
  UNIQUE KEY `uq_event_participants_2` (`id`, `event_id`, `user_id`),
  UNIQUE KEY `uq_event_participants_3` (`id`, `user_id`),
  CONSTRAINT `chk_participant_used` CHECK (used_ticket_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `users` (
  `id` BINARY(16) NOT NULL COMMENT '사용자 ID',
  `name` VARCHAR(20) NOT NULL COMMENT '사용자 이름',
  `role` ENUM('USER', 'ADMIN') NOT NULL COMMENT 'USER=일반 사용자 / ADMIN=관리자',
  `status` ENUM('ACTIVE', 'INACTIVE') NOT NULL COMMENT 'ACTIVE=활성 / INACTIVE=비활성',
  `membership` ENUM('EXCELLENT', 'VIP', 'VVIP') NULL COMMENT 'EXCELLENT=우수 / VIP=VIP / VVIP=VVIP',
  `phone_num` VARCHAR(11) NULL COMMENT '핸드폰 번호',
  `email` VARCHAR(255) NULL COMMENT '이메일',
  `updated_at` DATETIME(6) NOT NULL COMMENT '수정 시각 UTC',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  `suspended_at` DATETIME(6) NULL COMMENT '사용자 정지 시작 시각 UTC',
  `suspension_reason` TEXT NULL COMMENT '사용자 정지 사유',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_users_1` (`email`),
  UNIQUE KEY `uq_users_2` (`phone_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_failures` (
  `id` BINARY(16) NOT NULL COMMENT '같은 실패 기록의 저장 재시도에는 같은 ID 사용',
  `draw_run_id` BINARY(16) NOT NULL COMMENT '동일 실행의 여러 실패를 연결',
  `failure_code` VARCHAR(50) NOT NULL COMMENT '실패 분류 코드. 원문 예외 메시지나 개인정보를 저장하지 않음',
  `failed_at` DATETIME(6) NOT NULL COMMENT 'UTC. 기록 저장 시각과 구분',
  `trace_id` VARCHAR(100) NULL COMMENT '선택적인 상세 진단 기록 연결 ID. 발급·검색 시스템 연동 전에는 NULL 가능',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `reward_policies` (
  `id` BINARY(16) NOT NULL COMMENT '보상 정책 ID',
  `created_by` BINARY(16) NOT NULL COMMENT '등록 관리자 ID',
  `reward_type` ENUM('ATTENDANCE', 'MISSION', 'GAME') NOT NULL COMMENT 'ATTENDANCE=출석 / MISSION=미션 / GAME=게임',
  `game_id` BINARY(16) NULL COMMENT 'GAME 정책일 때 필수',
  `reward_ticket_count` INT NOT NULL COMMENT '보상으로 지급할 응모권 수',
  `effective_from` DATETIME(6) NOT NULL COMMENT '정책 적용 시작 시각 UTC',
  `effective_until` DATETIME(6) NULL COMMENT '정책 적용 종료 시각 UTC',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  `open_guard` BINARY(16) GENERATED ALWAYS AS (CASE WHEN effective_until IS NULL AND reward_type = 'ATTENDANCE' THEN UNHEX(REPEAT('00', 16)) WHEN effective_until IS NULL AND reward_type = 'GAME' THEN game_id ELSE NULL END) VIRTUAL COMMENT 'MySQL VIRTUAL 생성 컬럼. 종료일 없는 ATTENDANCE는 16바이트 0, GAME은 game_id, 그 외 NULL. 직접 입력 금지',
  `reward_ticket_type` ENUM('GOLD', 'SILVER', 'BRONZE') NULL COMMENT '보상 등급 설정의 역할은 보완 필요. 출석 BRONZE 고정, 게임·미션은 무작위 지급이므로 고정 결과로 해석하지 않음',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_reward_policies_1` (`reward_type`, `open_guard`),
  UNIQUE KEY `uq_game_policy_effective_from` (`game_id`, `effective_from`),
  KEY `reward_policy_effective_idx` (`reward_type`, `effective_from`),
  CONSTRAINT `chk_reward_policy_game` CHECK ((reward_type = 'GAME' AND game_id IS NOT NULL) OR (reward_type <> 'GAME' AND game_id IS NULL)),
  CONSTRAINT `chk_reward_policy_period` CHECK (effective_until IS NULL OR effective_until > effective_from),
  CONSTRAINT `chk_reward_policy_quantity` CHECK (reward_ticket_count >= 1),
  CONSTRAINT `chk_reward_current_quantity` CHECK ((reward_type = 'ATTENDANCE' AND reward_ticket_count >= 1) OR (reward_type IN ('MISSION','GAME') AND reward_ticket_count = 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `audit_logs` (
  `id` BINARY(16) NOT NULL COMMENT '운영 감사 로그 ID',
  `actor_id` BINARY(16) NULL COMMENT '처리자 사용자 ID',
  `action` VARCHAR(60) NOT NULL COMMENT '감사 대상 작업 코드',
  `target_type` VARCHAR(60) NOT NULL COMMENT '감사 대상 테이블 또는 업무 객체 종류',
  `target_id` BINARY(16) NOT NULL COMMENT '감사 대상 객체 UUID',
  `reason` TEXT NULL COMMENT '처리 사유',
  `before_data` JSON NULL COMMENT '변경 전 데이터',
  `after_data` JSON NULL COMMENT '변경 후 데이터',
  `request_id` VARCHAR(100) NULL COMMENT '관련 HTTP 요청 추적 ID',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  KEY `audit_target_time_idx` (`target_type`, `target_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `game_reward_claims` (
  `id` BINARY(16) NOT NULL COMMENT '게임 보상 지급 ID',
  `user_id` BINARY(16) NOT NULL COMMENT '보상 대상 사용자 ID',
  `game_id` BINARY(16) NOT NULL COMMENT '보상 대상 게임 ID',
  `game_play_id` BINARY(16) NOT NULL COMMENT '유효 플레이 ID',
  `reward_policy_id` BINARY(16) NOT NULL COMMENT '적용 정책 ID',
  `reward_date` DATE NOT NULL COMMENT '게임별 일일 보상 기준 날짜 KST',
  `source_key` VARCHAR(160) NOT NULL COMMENT '게임과 KST 날짜로 식별',
  `ticket_count` INT NOT NULL COMMENT '보상 수량',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_game_reward_claims_1` (`user_id`, `source_key`),
  UNIQUE KEY `uq_game_reward_claims_2` (`user_id`, `game_id`, `reward_date`),
  CONSTRAINT `chk_game_claim_ticket` CHECK (ticket_count = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_checks` (
  `id` BINARY(16) NOT NULL COMMENT '정합성 검증 기록 ID',
  `draw_run_id` BINARY(16) NOT NULL COMMENT '검증한 확정 추첨 실행 ID',
  `checked_by` BINARY(16) NOT NULL COMMENT '검증을 요청한 관리자 ID',
  `passed` BOOLEAN NOT NULL COMMENT '전체 검증 통과 여부. true=통과, false=불통과',
  `checks_snapshot` JSON NOT NULL COMMENT '항목별 정합성 검증 결과 스냅샷. 검증 버전·검증 범위·항목 코드·통과 여부·메시지.',
  `checked_at` DATETIME(6) NOT NULL COMMENT '검증 완료·기록 시각 UTC',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `attendance_streak_policy_sets` (
  `id` BINARY(16) NOT NULL COMMENT '연속 출석 정책 묶음 ID',
  `created_by` BINARY(16) NOT NULL COMMENT '등록 관리자 ID',
  `effective_month` DATE NOT NULL COMMENT '적용 시작월의 1일 KST',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_streak_policy_effective_month` (`effective_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `notifications` (
  `id` BINARY(16) NOT NULL COMMENT '개인 알림 ID',
  `job_id` BINARY(16) NOT NULL COMMENT '알림 생성 작업 ID',
  `user_id` BINARY(16) NOT NULL COMMENT '사용자 ID',
  `event_id` BINARY(16) NULL COMMENT '연결 이벤트 ID',
  `title` VARCHAR(200) NOT NULL COMMENT '알림 제목',
  `body` TEXT NOT NULL COMMENT '알림 본문',
  `link_url` VARCHAR(500) NULL COMMENT '알림 선택 시 이동 URL',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  `is_read` BOOLEAN NOT NULL DEFAULT 0 COMMENT '읽음 여부',
  `mock_delivery_status` ENUM('PENDING', 'SENT', 'FAILED') NOT NULL COMMENT 'PENDING=발송 대기',
  `mock_sent_at` DATETIME(6) NULL COMMENT '모의 발송 완료 시각 UTC',
  `delivery_attempt_count` INT NOT NULL DEFAULT 0 COMMENT '모의 발송 시도 횟수',
  `next_delivery_attempt_at` DATETIME(6) NULL COMMENT '다음 모의 발송 재시도 예정 시각 UTC',
  `last_delivery_error` TEXT NULL COMMENT '마지막 모의 발송 오류 내용',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification_job_user` (`job_id`, `user_id`),
  CONSTRAINT `chk_delivery_attempts` CHECK (delivery_attempt_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `mission_quiz_questions` (
  `id` BINARY(16) NOT NULL COMMENT '퀴즈 문항 ID',
  `mission_id` BINARY(16) NOT NULL COMMENT '퀴즈 미션 ID',
  `question_type` ENUM('OX', 'SINGLE_CHOICE', 'SHORT_ANSWER') NOT NULL COMMENT 'OX / 단일 선택 / 단답형',
  `question_text` TEXT NOT NULL COMMENT '문제 내용',
  `display_order` INT NOT NULL COMMENT '문항 표시 순서',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  `correct_answer` TEXT NULL COMMENT '단답형 정답 하나',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mission_quiz_questions_1` (`mission_id`, `display_order`),
  UNIQUE KEY `uq_mission_quiz_questions_2` (`id`, `mission_id`),
  CONSTRAINT `chk_quiz_correct_answer` CHECK ((question_type = 'SHORT_ANSWER' AND correct_answer IS NOT NULL) OR (question_type <> 'SHORT_ANSWER' AND correct_answer IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `mission_reward_claims` (
  `id` BINARY(16) NOT NULL COMMENT '미션 보상 지급 ID',
  `reward_policy_id` BINARY(16) NOT NULL COMMENT '적용 미션 보상 정책 ID',
  `mission_id` BINARY(16) NOT NULL COMMENT '완료 미션 ID',
  `mission_submission_id` BINARY(16) NOT NULL COMMENT '최종 완료 근거 제출 ID',
  `user_id` BINARY(16) NOT NULL COMMENT '보상 대상 사용자 ID',
  `source_key` VARCHAR(160) NOT NULL COMMENT '사용자별 미션 보상 식별 키',
  `ticket_count` INT NOT NULL COMMENT '실제 지급 수량',
  `created_at` DATETIME(6) NOT NULL COMMENT '보상 지급 이력 생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mission_reward_claims_1` (`user_id`, `mission_id`),
  CONSTRAINT `chk_mission_claim_ticket` CHECK (ticket_count = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `quiz_answers` (
  `id` BINARY(16) NOT NULL COMMENT '문항 답변 및 채점 기록 ID',
  `submission_id` BINARY(16) NOT NULL COMMENT '제출 요청 ID',
  `quiz_question_id` BINARY(16) NOT NULL COMMENT '응답 문항 ID',
  `selected_option_id` BINARY(16) NULL COMMENT 'OX 및 단일 선택에서 필수, 단답형은 NULL',
  `mission_id` BINARY(16) NOT NULL COMMENT '제출과 문항의 소속 미션',
  `answer_text` TEXT NULL COMMENT '단답형 입력, 선택형은 NULL',
  `is_correct` BOOLEAN NOT NULL COMMENT '서버 채점 결과',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_quiz_answers_1` (`submission_id`, `quiz_question_id`),
  CONSTRAINT `chk_quiz_answer_shape` CHECK (selected_option_id IS NOT NULL OR answer_text IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `survey_questions` (
  `id` BINARY(16) NOT NULL COMMENT '설문 문항 ID',
  `mission_id` BINARY(16) NOT NULL COMMENT '설문 미션 ID',
  `question_type` ENUM('SINGLE_CHOICE', 'FREE_TEXT') NOT NULL COMMENT '단일 선택 또는 자유 입력',
  `question_text` TEXT NOT NULL COMMENT '질문 내용',
  `is_required` BOOLEAN NOT NULL COMMENT '필수 응답 여부',
  `display_order` INT NOT NULL COMMENT '문항 순서',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  `updated_at` DATETIME(6) NOT NULL COMMENT '마지막 수정 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_survey_questions_1` (`mission_id`, `display_order`),
  UNIQUE KEY `uq_survey_questions_2` (`id`, `mission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `survey_question_options` (
  `id` BINARY(16) NOT NULL COMMENT '설문 선택지 ID',
  `survey_question_id` BINARY(16) NOT NULL COMMENT '단일 선택 설문 문항 ID',
  `option_text` TEXT NOT NULL COMMENT '선택지 내용',
  `display_order` INT NOT NULL COMMENT '선택지 순서',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_survey_question_options_1` (`survey_question_id`, `display_order`),
  UNIQUE KEY `uq_survey_question_options_2` (`id`, `survey_question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `attendance_reward_claims` (
  `id` BINARY(16) NOT NULL COMMENT '출석 보상 지급 ID',
  `attendance_id` BINARY(16) NOT NULL COMMENT '보상 근거 출석 기록 ID',
  `reward_policy_id` BINARY(16) NULL COMMENT 'DAILY일 때 필수',
  `attendance_streak_policy_id` BINARY(16) NULL COMMENT 'STREAK일 때 필수',
  `user_id` BINARY(16) NOT NULL COMMENT '보상 대상 사용자 ID',
  `reward_type` ENUM('DAILY', 'STREAK') NOT NULL COMMENT 'DAILY=기본 / STREAK=연속 출석',
  `reward_date` DATE NOT NULL COMMENT '보상 기준 날짜 KST',
  `milestone_days` INT NULL COMMENT '연속 출석 달성 단계 일수',
  `source_key` VARCHAR(160) NOT NULL COMMENT '사용자·종류별 고정 업무 키. DAILY YYYY-MM-DD, STREAK YYYY-MM:단계일수. 정책 변경·재시도에도 유지',
  `ticket_count` INT NOT NULL COMMENT '실제 지급 수량',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_attendance_reward_claims_1` (`user_id`, `reward_type`, `source_key`),
  CONSTRAINT `chk_attendance_claim_shape` CHECK ((reward_type = 'DAILY' AND reward_policy_id IS NOT NULL AND attendance_streak_policy_id IS NULL AND milestone_days IS NULL) OR (reward_type = 'STREAK' AND attendance_streak_policy_id IS NOT NULL AND milestone_days IS NOT NULL AND reward_policy_id IS NULL)),
  CONSTRAINT `chk_attendance_claim_ticket` CHECK (ticket_count >= 1),
  CONSTRAINT `chk_attendance_milestone` CHECK (milestone_days IS NULL OR milestone_days BETWEEN 1 AND 28)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `attendance_streak_policies` (
  `id` BINARY(16) NOT NULL COMMENT '연속 출석 단계 정책 ID',
  `policy_set_id` BINARY(16) NOT NULL COMMENT '소속 정책 묶음 ID',
  `milestone_days` INT NOT NULL COMMENT '연속 출석 기준 일수 1~28',
  `reward_ticket_count` INT NOT NULL COMMENT '단계 달성 시 지급 수량',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_streak_policy_milestone` (`policy_set_id`, `milestone_days`),
  CONSTRAINT `chk_streak_policy_milestone` CHECK (milestone_days BETWEEN 1 AND 28),
  CONSTRAINT `chk_streak_policy_reward` CHECK (reward_ticket_count >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `notification_jobs` (
  `id` BINARY(16) NOT NULL COMMENT '알림 생성 작업 ID',
  `event_id` BINARY(16) NULL COMMENT '연결 이벤트 ID',
  `source_job_id` BINARY(16) NULL COMMENT '일정 변경 및 취소 시 기존 시작 알림 수신자를 찾는 원본 작업 ID',
  `payload` JSON NOT NULL COMMENT '알림 내용과 최초 생성 시점의 대상 스냅샷',
  `occurrence_key` VARCHAR(160) NOT NULL COMMENT '작업별 고정 발생 키',
  `notification_type` ENUM('EVENT_START', 'RESULT_PUBLISHED', 'ENTRY_EXCLUDED', 'EVENT_SCHEDULE_CHANGED', 'EVENT_CANCELED', 'RESULT_CHANGED') NOT NULL COMMENT '시작·최초 발표·제외·일정 변경·취소·결과 변경. 실제 업무 확정에 연결해 발생 키 고정',
  `status` ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL COMMENT 'PENDING=대기',
  `last_processed_user_id` BINARY(16) NULL COMMENT '고정 대상 집합에서 UUID 바이트 순으로 마지막 처리한 사용자 ID',
  `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '작업 실행 시도 횟수',
  `scheduled_at` DATETIME(6) NOT NULL COMMENT '알림 생성 예정 시각 UTC',
  `next_attempt_at` DATETIME(6) NULL COMMENT '다음 재시도 예정 시각 UTC',
  `lease_until` DATETIME(6) NULL COMMENT '작업 처리권 임대 만료 시각 UTC',
  `last_error` TEXT NULL COMMENT '마지막 작업 오류 내용',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  `completed_at` DATETIME(6) NULL COMMENT '완료 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification_job_occurrence` (`occurrence_key`),
  CONSTRAINT `chk_notification_attempts` CHECK (attempt_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `quiz_question_options` (
  `id` BINARY(16) NOT NULL COMMENT '퀴즈 선택지 ID',
  `quiz_question_id` BINARY(16) NOT NULL COMMENT '퀴즈 문항 ID',
  `option_text` TEXT NOT NULL COMMENT '선택지 내용',
  `display_order` INT NOT NULL COMMENT '선택지 순서',
  `is_correct` BOOLEAN NOT NULL COMMENT '정답 여부',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_quiz_question_options_1` (`quiz_question_id`, `display_order`),
  UNIQUE KEY `uq_quiz_question_options_2` (`id`, `quiz_question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_results` (
  `id` BINARY(16) NOT NULL COMMENT 'SELECTED 결과의 ID가 API의 winId',
  `draw_run_id` BINARY(16) NOT NULL COMMENT '결과를 생성한 추첨 실행 ID',
  `event_prize_id` BINARY(16) NOT NULL COMMENT '선정 또는 미충원된 이벤트 경품 ID',
  `slot_number` INT NOT NULL COMMENT '경품별 당첨 자리 번호. 1부터 해당 경품 당첨 인원까지',
  `result_type` ENUM('SELECTED', 'UNFILLED') NOT NULL COMMENT '결과 종류. SELECTED=당첨자 선정, UNFILLED=후보 부족으로 미충원',
  `candidate_id` BINARY(16) NULL COMMENT '선정된 추첨 대상자 ID. UNFILLED는 NULL',
  `selection_order` INT NULL COMMENT '실행 내 실제 당첨 선정 순서. 상위 등수부터 선정하며 UNFILLED는 NULL',
  `created_at` DATETIME(6) NOT NULL COMMENT '선정·미충원 결과 기록 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_draw_results_1` (`draw_run_id`, `event_prize_id`, `slot_number`),
  UNIQUE KEY `uq_draw_results_2` (`draw_run_id`, `candidate_id`),
  UNIQUE KEY `uq_draw_results_3` (`draw_run_id`, `selection_order`),
  CONSTRAINT `chk_draw_result_shape` CHECK ((result_type = 'UNFILLED' AND candidate_id IS NULL AND selection_order IS NULL) OR (result_type = 'SELECTED' AND candidate_id IS NOT NULL AND selection_order IS NOT NULL)),
  CONSTRAINT `chk_draw_slot` CHECK (slot_number >= 1),
  CONSTRAINT `chk_draw_selection_order` CHECK (selection_order IS NULL OR selection_order >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_runs` (
  `id` BINARY(16) NOT NULL COMMENT '추첨 실행 ID',
  `event_id` BINARY(16) NOT NULL COMMENT '추첨 대상 이벤트 ID',
  `run_number` INT NOT NULL COMMENT '이벤트별 추첨 차수. 이벤트별 최초 0, 새 재추첨마다 증가. 실패 재시도는 유지.',
  `draw_type` ENUM('INITIAL', 'REPLACEMENT') NOT NULL COMMENT '추첨 종류. INITIAL=최초 추첨, REPLACEMENT=당첨 취소에 따른 재추첨',
  `status` ENUM('PREPARING', 'READY', 'RUNNING', 'CONFIRMED', 'FAILED') NOT NULL COMMENT '실행 상태. PREPARING=준비 중, READY=후보·조건 확정, RUNNING=실행 중, CONFIRMED=결과 확정, FAILED=실패',
  `algorithm_version` VARCHAR(100) NULL COMMENT 'READY 이후 필수·불변.',
  `rules_snapshot` JSON NULL COMMENT 'READY 이후 필수·불변. 경품·자리·가중치 기준·유지 당첨 결과 ID·대상 자리.',
  `snapshot_fixed_at` DATETIME(6) NULL COMMENT 'UTC. 확정 전 NULL',
  `started_at` DATETIME(6) NULL COMMENT 'UTC. 시작 전 NULL',
  `confirmed_at` DATETIME(6) NULL COMMENT 'UTC. 확정 전 NULL',
  `created_at` DATETIME(6) NOT NULL COMMENT '추첨 실행 및 작업 등록 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_draw_runs_1` (`event_id`, `run_number`),
  CONSTRAINT `chk_draw_number` CHECK ((draw_type = 'INITIAL' AND run_number = 0) OR (draw_type = 'REPLACEMENT' AND run_number >= 1)),
  CONSTRAINT `chk_draw_fixed_input` CHECK (status NOT IN ('READY','RUNNING','CONFIRMED') OR (algorithm_version IS NOT NULL AND rules_snapshot IS NOT NULL AND snapshot_fixed_at IS NOT NULL)),
  CONSTRAINT `chk_draw_confirmed_time` CHECK (status <> 'CONFIRMED' OR confirmed_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `mission_submissions` (
  `id` BINARY(16) NOT NULL COMMENT '미션 제출 ID',
  `mission_id` BINARY(16) NOT NULL COMMENT '미션 ID',
  `reward_policy_id` BINARY(16) NOT NULL COMMENT '적용 보상 정책 ID',
  `user_id` BINARY(16) NOT NULL COMMENT '사용자 ID',
  `is_completed` BOOLEAN NOT NULL COMMENT 'true=완료 / false=미완료',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mission_submissions_2` (`id`, `mission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `games` (
  `id` BINARY(16) NOT NULL COMMENT '게임 ID',
  `code` VARCHAR(50) NOT NULL COMMENT '게임 구분 코드',
  `name` VARCHAR(100) NOT NULL COMMENT '게임명',
  `description` TEXT NULL COMMENT '상세 설명',
  `rules` JSON NOT NULL COMMENT '게임 규칙',
  `rule_version` VARCHAR(30) NOT NULL COMMENT '적용 게임 규칙 버전',
  `is_active` BOOLEAN NOT NULL DEFAULT 1 COMMENT 'true=활성 / false=비활성',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  `updated_at` DATETIME(6) NOT NULL COMMENT '수정 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_games_1` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `quiz_question_progress` (
  `id` BINARY(16) NOT NULL COMMENT '문항 정답 진행 ID',
  `quiz_question_id` BINARY(16) NOT NULL COMMENT '정답으로 완료한 퀴즈 문항 ID',
  `correct_answer_id` BINARY(16) NOT NULL COMMENT '정답 판정의 근거 답변 ID',
  `user_id` BINARY(16) NOT NULL COMMENT '사용자 ID',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_quiz_question_progress_1` (`user_id`, `quiz_question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_publications` (
  `id` BINARY(16) NOT NULL COMMENT '공개 명단 버전 ID',
  `event_id` BINARY(16) NOT NULL COMMENT '명단을 공개한 이벤트 ID',
  `source_draw_id` BINARY(16) NOT NULL COMMENT '이번 공개·갱신의 원인이 된 추첨 실행 ID. 명단에는 이전 실행 결과도 포함 가능',
  `revision` INT NOT NULL COMMENT '이벤트별 공개 명단 버전 번호. 최초 1, 갱신마다 증가',
  `publication_type` ENUM('INITIAL', 'UPDATE') NOT NULL COMMENT '공개 종류. INITIAL=최초 자동 공개, UPDATE=관리자 확인 후 명단 갱신',
  `reason` TEXT NULL COMMENT '공개 명단 변경 사유. UPDATE는 필수. 정상 최초 자동 공개는 NULL 가능',
  `confirmed_by` BINARY(16) NULL COMMENT '공개 전 재추첨 결과 확인 관리자 ID. UPDATE는 필수. 정상 최초 추첨만 NULL 가능. 여러 실행의 확인 연결은 보완 필요',
  `confirmed_at` DATETIME(6) NULL COMMENT '관리자 공개 확인 시각 UTC. UPDATE는 필수. 정상 최초 추첨만 NULL 가능. 실제 공개 시각과 구분',
  `published_at` DATETIME(6) NOT NULL COMMENT '해당 명단 버전을 실제 공개에 반영한 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_draw_publications_1` (`event_id`, `revision`),
  CONSTRAINT `chk_publication_revision` CHECK (revision >= 1),
  CONSTRAINT `chk_publication_confirmation_pair` CHECK ((confirmed_by IS NULL AND confirmed_at IS NULL) OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)),
  CONSTRAINT `chk_publication_update` CHECK (publication_type <> 'UPDATE' OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL AND reason IS NOT NULL AND TRIM(reason) <> ''))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `event_prizes` (
  `id` BINARY(16) NOT NULL COMMENT '이벤트 경품 ID',
  `event_id` BINARY(16) NOT NULL COMMENT '연결 이벤트 ID',
  `prize_rank` INT NOT NULL COMMENT '경품 등수',
  `name` VARCHAR(200) NOT NULL COMMENT '경품명',
  `description` TEXT NULL COMMENT '상세 설명',
  `winner_count` INT NOT NULL COMMENT '당첨 인원',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  `updated_at` DATETIME(6) NOT NULL COMMENT '수정 시각 UTC',
  `image_key` VARCHAR(500) NULL COMMENT '경품 이미지 저장소 경로',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_event_prizes_1` (`event_id`, `prize_rank`),
  CONSTRAINT `chk_event_prize_winner_count` CHECK (winner_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `attendance_streaks` (
  `id` BINARY(16) NOT NULL COMMENT '월별 연속 출석 상태 ID',
  `user_id` BINARY(16) NOT NULL COMMENT '사용자 ID',
  `policy_set_id` BINARY(16) NOT NULL COMMENT '해당 월에 적용한 연속 출석 정책 묶음 ID',
  `streak_month` DATE NOT NULL COMMENT 'KST 기준 출석 기준월의 1일',
  `consecutive_days` INT NOT NULL COMMENT '마지막 출석일까지 이어진 연속 출석 일수',
  `last_attendance_date` DATE NOT NULL COMMENT '마지막 출석 인정 날짜 KST',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  `updated_at` DATETIME(6) NOT NULL COMMENT '연속 출석 상태 갱신 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_attendance_streaks_1` (`user_id`, `streak_month`),
  CONSTRAINT `chk_streak_days` CHECK (consecutive_days BETWEEN 0 AND 31)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `event_entries` (
  `id` BINARY(16) NOT NULL COMMENT '개별 응모 ID',
  `participant_id` BINARY(16) NOT NULL COMMENT '접수 완료된 응모의 이벤트 참여자 ID',
  `user_id` BINARY(16) NOT NULL COMMENT '사용자 ID',
  `idempotency_key` VARCHAR(100) NOT NULL COMMENT '동일 요청 중복 처리 방지 키',
  `requested_ticket_count` INT NOT NULL COMMENT '요청한 응모권 차감 수량',
  `deducted_ticket_count` INT NOT NULL DEFAULT 0 COMMENT '실제 차감 응모권 수량',
  `created_at` DATETIME(6) NOT NULL COMMENT '접수 완료된 응모의 처리 결과 기록 시각 UTC. 접수 성립·마감 판정은 차감과 함께 트랜잭션 확정 기준',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_event_entries_1` (`participant_id`, `idempotency_key`),
  CONSTRAINT `chk_entry_requested` CHECK (requested_ticket_count >= 0),
  CONSTRAINT `chk_entry_deducted` CHECK (deducted_ticket_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `banners` (
  `id` BINARY(16) NOT NULL COMMENT '배너 ID',
  `image_key` VARCHAR(500) NOT NULL COMMENT '이미지 저장소 경로',
  `display_order` INT NOT NULL COMMENT '서비스 전체 노출 순서 1~5',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  `updated_at` DATETIME(6) NOT NULL COMMENT '수정 시각 UTC',
  `event_url` VARCHAR(500) NULL COMMENT '이벤트 URL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_banners_1` (`display_order`),
  CONSTRAINT `chk_banner_order` CHECK (display_order BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `abuse_cases` (
  `id` BINARY(16) NOT NULL COMMENT '어뷰징 탐지 ID',
  `event_id` BINARY(16) NULL COMMENT '관련 이벤트 ID',
  `user_id` BINARY(16) NOT NULL COMMENT '사용자 ID',
  `attendance_id` BINARY(16) NULL COMMENT '원본 출석 기록 ID',
  `mission_submission_id` BINARY(16) NULL COMMENT '원본 미션 제출 ID',
  `game_play_id` BINARY(16) NULL COMMENT '원본 게임 플레이 ID',
  `reviewed_by` BINARY(16) NULL COMMENT 'ALLOWED·CONFIRMED에서 필수, PENDING에서는 NULL',
  `request_id` VARCHAR(100) NULL COMMENT '관련 HTTP 요청 추적 ID',
  `detection_key` VARCHAR(160) NOT NULL COMMENT '탐지 건 중복 저장 방지 키',
  `detection_type` VARCHAR(50) NOT NULL COMMENT '탐지 유형 코드',
  `detection_reason` TEXT NOT NULL COMMENT '탐지 사유',
  `evidence` JSON NULL COMMENT '탐지 근거',
  `occurred_at` DATETIME(6) NOT NULL COMMENT '탐지 대상 행위 발생 시각 UTC',
  `detected_at` DATETIME(6) NOT NULL COMMENT '탐지 시각 UTC',
  `review_status` ENUM('PENDING', 'ALLOWED', 'CONFIRMED') NOT NULL COMMENT 'PENDING=검토 전',
  `reviewed_at` DATETIME(6) NULL COMMENT 'ALLOWED·CONFIRMED에서 필수, PENDING에서는 NULL',
  `review_reason` TEXT NULL COMMENT 'ALLOWED·CONFIRMED에서 필수, PENDING에서는 NULL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_abuse_detection_key` (`detection_key`),
  CONSTRAINT `chk_abuse_review_shape` CHECK ((review_status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL) OR (review_status IN ('ALLOWED','CONFIRMED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_reason IS NOT NULL)),
  CONSTRAINT `chk_abuse_one_source` CHECK ((attendance_id IS NOT NULL) + (mission_submission_id IS NOT NULL) + (game_play_id IS NOT NULL) <= 1),
  CONSTRAINT `chk_abuse_source_evidence` CHECK ((attendance_id IS NOT NULL OR mission_submission_id IS NOT NULL OR game_play_id IS NOT NULL) OR (request_id IS NOT NULL AND evidence IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `events` (
  `id` BINARY(16) NOT NULL COMMENT '이벤트 ID',
  `title` VARCHAR(200) NOT NULL COMMENT '이벤트 제목',
  `description` TEXT NOT NULL COMMENT '상세 설명',
  `image_key` VARCHAR(500) NULL COMMENT '이미지 저장소 경로',
  `event_type` ENUM('NO_TICKET', 'TICKET') NOT NULL COMMENT 'NO_TICKET=응모권 미사용 / TICKET=응모권 사용',
  `weighting_enabled` BOOLEAN NOT NULL DEFAULT 0 COMMENT '추첨 가중치 적용 여부',
  `max_tickets_per_user` INT NULL COMMENT '사용자별 이벤트 누적 상한',
  `starts_at` DATETIME(6) NOT NULL COMMENT '응모 시작 시각 UTC',
  `ends_at` DATETIME(6) NOT NULL COMMENT '응모 마감 시각 UTC',
  `status` ENUM('SCHEDULED', 'OPEN', 'CLOSED', 'DRAW_CONFIRMED', 'PUBLISHED', 'CANCELED', 'REDRAWING', 'NO_ENTRANTS', 'NO_ELIGIBLE_ENTRANTS') NOT NULL COMMENT '이벤트 상태',
  `canceled_at` DATETIME(6) NULL COMMENT '취소 시각 UTC',
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  `updated_at` DATETIME(6) NOT NULL COMMENT '수정 시각 UTC',
  `deleted_at` DATETIME(6) NULL COMMENT '논리 삭제 시각 UTC',
  `membership_rule` ENUM('excellent', 'vip', 'vvip') NOT NULL COMMENT 'excellent=우수 / vip=VIP / vvip=VVIP',
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_event_ticket_limit` CHECK (max_tickets_per_user IS NULL OR max_tickets_per_user > 0),
  CONSTRAINT `chk_event_dates` CHECK (ends_at > starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `game_log_batches` (
  `id` BINARY(16) NOT NULL COMMENT '로그 묶음 ID. 같은 묶음 재전송 시 동일 ID 유지',
  `game_play_id` BINARY(16) NOT NULL COMMENT '원본 게임 플레이 ID',
  `payload_hash` CHAR(64) NOT NULL COMMENT '서버가 계산한 원본 내용 SHA-256',
  `event_count` INT NOT NULL COMMENT '묶음에 포함된 행동 이벤트 수',
  `first_sequence_no` BIGINT NOT NULL COMMENT '묶음 내 최소 행동 순번',
  `last_sequence_no` BIGINT NOT NULL COMMENT '묶음 내 최대 행동 순번',
  `s3_bucket` VARCHAR(63) NOT NULL COMMENT '원본을 저장할 S3 버킷',
  `s3_object_key` VARCHAR(1024) NOT NULL COMMENT '원본 객체 키. 경로 기록만으로 저장 완료를 의미하지 않음',
  `storage_status` ENUM('PENDING', 'STORED', 'FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '원본 저장 대기 / 저장 완료 / 저장 실패',
  `last_error_code` VARCHAR(100) NULL COMMENT '최근 원본 저장 실패 사유 코드',
  `received_at` DATETIME(6) NOT NULL COMMENT 'API 최초 수신 시각 UTC',
  `stored_at` DATETIME(6) NULL COMMENT 'S3 저장 성공 확인 시각 UTC',
  `created_at` DATETIME(6) NOT NULL COMMENT 'DB 기록 생성 시각 UTC',
  `updated_at` DATETIME(6) NOT NULL COMMENT '최근 상태 변경 시각 UTC',
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_batch_count` CHECK (event_count > 0 AND first_sequence_no <= last_sequence_no),
  CONSTRAINT `chk_batch_storage` CHECK ((storage_status = 'STORED' AND stored_at IS NOT NULL) OR (storage_status <> 'STORED' AND stored_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `game_play_analyses` (
  `id` BINARY(16) NOT NULL COMMENT '플레이 분석 ID',
  `game_play_id` BINARY(16) NOT NULL COMMENT '분석 대상 게임 플레이 ID',
  `analyzer_version` VARCHAR(50) NOT NULL COMMENT '분석 로직과 규칙 및 설정을 식별하는 버전',
  `input_hash` CHAR(64) NOT NULL COMMENT '정규화한 분석 입력 목록과 수집 종료 정보의 SHA-256',
  `input_manifest` JSON NOT NULL COMMENT '분석한 batchId와 payloadHash 목록 및 기대 최종 순번 등 입력 스냅샷',
  `status` ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '분석 작업 대기 / 처리 중 / 완료 / 실패',
  `data_completeness` ENUM('UNKNOWN', 'PARTIAL', 'COMPLETE') NOT NULL DEFAULT 'UNKNOWN' COMMENT '로그 수집 완전성. 어뷰징 판정과 별개',
  `verdict` ENUM('NORMAL', 'ABUSE') NULL COMMENT '정상 / 어뷰징. 분석 전과 실패 시 NULL',
  `event_count` INT NULL COMMENT '중복 제거 후 분석한 행동 이벤트 수',
  `duplicate_event_count` INT NULL COMMENT '분석 입력에서 확인한 중복 이벤트 수',
  `missing_sequence_count` BIGINT NULL COMMENT '누락 순번 수. 최종 범위를 모르면 NULL',
  `metrics` JSON NULL COMMENT '클릭 수와 입력 간격 및 반복 패턴 등 분석 지표',
  `detection_reasons` JSON NULL COMMENT '자동 판정 근거. 규칙 코드와 관측값 및 기준값',
  `last_error_code` VARCHAR(100) NULL COMMENT '최근 분석 작업 실패 사유 코드',
  `started_at` DATETIME(6) NULL COMMENT '최근 분석 시도 시작 시각 UTC',
  `completed_at` DATETIME(6) NULL COMMENT '분석 성공 완료 시각 UTC',
  `created_at` DATETIME(6) NOT NULL COMMENT '분석 작업 생성 시각 UTC',
  `updated_at` DATETIME(6) NOT NULL COMMENT '최근 상태 변경 시각 UTC',
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_analysis_result` CHECK ((status = 'COMPLETED' AND verdict IS NOT NULL AND completed_at IS NOT NULL) OR (status <> 'COMPLETED' AND verdict IS NULL AND completed_at IS NULL)),
  CONSTRAINT `chk_analysis_counts` CHECK ((event_count IS NULL OR event_count >= 0) AND (duplicate_event_count IS NULL OR duplicate_event_count >= 0) AND (missing_sequence_count IS NULL OR missing_sequence_count >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `tickets` (
  `id` BINARY(16) NOT NULL COMMENT '응모권 한 장 ID. 반환과 재사용 후에도 유지',
  `user_id` BINARY(16) NOT NULL COMMENT '소유 사용자 ID. 최초 확정 후 변경 금지',
  `attendance_reward_claim_id` BINARY(16) NULL COMMENT '최초 출석 지급 근거 ID. 다른 보상 종류이면 NULL. 같은 청구로 여러 장 지급 가능. 확정 후 변경 금지',
  `mission_reward_claim_id` BINARY(16) NULL COMMENT '최초 미션 지급 근거 ID. 다른 보상 종류이면 NULL. 청구당 티켓 1장. 확정 후 변경 금지',
  `game_reward_claim_id` BINARY(16) NULL COMMENT '최초 게임 지급 근거 ID. 다른 보상 종류이면 NULL. 청구당 티켓 1장. 확정 후 변경 금지',
  `grade` ENUM('BRONZE', 'SILVER', 'GOLD') NULL COMMENT '최초 지급 등급. 이후 불변. NULL은 기존 데이터 전환 등 미결정 영역이며 무등급 사용 허용을 뜻하지 않음',
  `status` ENUM('AVAILABLE', 'RETURNED', 'SPENT', 'EXPIRED') NOT NULL COMMENT '현재 상태. 최초 사용 가능 / 반환 후 사용 가능 / 사용됨 / 만료 처리됨',
  `expires_at` DATETIME(6) NOT NULL COMMENT '현재 만료 시각 UTC. 반환 성공 시 갱신',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '현재 변경 순번. 최초 지급 1, 변경마다 1 증가. 마지막 이력 버전과 일치',
  `created_at` DATETIME(6) NOT NULL COMMENT '최초 지급 시각 UTC. 변경 금지',
  `updated_at` DATETIME(6) NOT NULL COMMENT '최근 업무 처리 시각 UTC. 현재값 갱신과 함께 반영',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_tickets_1` (`mission_reward_claim_id`),
  UNIQUE KEY `uq_tickets_2` (`game_reward_claim_id`),
  CONSTRAINT `chk_ticket_source` CHECK ((attendance_reward_claim_id IS NOT NULL) + (mission_reward_claim_id IS NOT NULL) + (game_reward_claim_id IS NOT NULL) = 1),
  CONSTRAINT `chk_ticket_version` CHECK (version >= 1),
  CONSTRAINT `chk_ticket_times` CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ticket_histories` (
  `id` BINARY(16) NOT NULL COMMENT '응모권 한 장의 처리 이력 ID. 확정 후 수정·삭제 없이 보존',
  `ticket_id` BINARY(16) NOT NULL COMMENT '처리 대상 응모권 ID. 한 티켓에 여러 버전의 이력이 누적됨',
  `event_entry_id` BINARY(16) NULL COMMENT 'USE 개별 응모 ID. 한 응모에 여러 티켓 사용 가능. 다른 처리 NULL',
  `operation_type` ENUM('GRANT', 'USE', 'REFUND', 'EXPIRE', 'CORRECTION') NOT NULL COMMENT '지급 / 사용 / 반환 / 만료 / 정정. 반환·정정의 원본 이력 연결은 보완 필요',
  `ticket_version` BIGINT NOT NULL COMMENT '처리 후 티켓 버전. 최초 GRANT 1, 이후 1씩 증가하는 이력 순서',
  `status` ENUM('AVAILABLE', 'RETURNED', 'SPENT', 'EXPIRED') NOT NULL COMMENT '처리 결과 상태. 해당 버전 처리 완료 당시 값이며 이후 변경하지 않음',
  `expires_at` DATETIME(6) NOT NULL COMMENT '처리 결과 만료 시각 UTC. 해당 버전 완료 당시 값이며 이후 변경하지 않음',
  `reason` TEXT NOT NULL COMMENT '처리 사유. 원문 예외·개인정보·비밀값 저장 금지',
  `created_at` DATETIME(6) NOT NULL COMMENT '실제 처리 시각 UTC. 반환 만료 계산의 기준',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ticket_histories_1` (`ticket_id`, `ticket_version`),
  UNIQUE KEY `uq_ticket_histories_2` (`event_entry_id`, `ticket_id`),
  CONSTRAINT `chk_history_version` CHECK ((operation_type = 'GRANT' AND ticket_version = 1) OR (operation_type <> 'GRANT' AND ticket_version >= 2)),
  CONSTRAINT `chk_history_entry` CHECK ((operation_type = 'USE' AND event_entry_id IS NOT NULL) OR (operation_type <> 'USE' AND event_entry_id IS NULL)),
  CONSTRAINT `chk_history_state` CHECK ((operation_type = 'GRANT' AND status = 'AVAILABLE') OR (operation_type = 'USE' AND status = 'SPENT') OR (operation_type = 'REFUND' AND status = 'RETURNED') OR (operation_type = 'EXPIRE' AND status = 'EXPIRED') OR operation_type = 'CORRECTION')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `event_prizes` ADD CONSTRAINT `fk_event_prizes_1`
  FOREIGN KEY (`event_id`) REFERENCES `events` (`id`);

ALTER TABLE `user_game_stats` ADD CONSTRAINT `fk_user_game_stats_2`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `user_game_stats` ADD CONSTRAINT `fk_user_game_stats_3`
  FOREIGN KEY (`game_id`) REFERENCES `games` (`id`);

ALTER TABLE `mission_quiz_questions` ADD CONSTRAINT `fk_mission_quiz_questions_4`
  FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`);

ALTER TABLE `game_plays` ADD CONSTRAINT `fk_game_plays_5`
  FOREIGN KEY (`game_id`) REFERENCES `games` (`id`);

ALTER TABLE `game_plays` ADD CONSTRAINT `fk_game_plays_6`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `attendance_streak_policies` ADD CONSTRAINT `fk_attendance_streak_policies_7`
  FOREIGN KEY (`policy_set_id`) REFERENCES `attendance_streak_policy_sets` (`id`);

ALTER TABLE `event_participants` ADD CONSTRAINT `fk_event_participants_8`
  FOREIGN KEY (`event_id`) REFERENCES `events` (`id`);

ALTER TABLE `event_participants` ADD CONSTRAINT `fk_event_participants_9`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `mission_reward_claims` ADD CONSTRAINT `fk_mission_reward_claims_10`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `mission_reward_claims` ADD CONSTRAINT `fk_mission_reward_claims_11`
  FOREIGN KEY (`reward_policy_id`) REFERENCES `reward_policies` (`id`);

ALTER TABLE `mission_reward_claims` ADD CONSTRAINT `fk_mission_reward_claims_12`
  FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`);

ALTER TABLE `mission_reward_claims` ADD CONSTRAINT `fk_mission_reward_claims_13`
  FOREIGN KEY (`mission_submission_id`) REFERENCES `mission_submissions` (`id`);

ALTER TABLE `draw_results` ADD CONSTRAINT `fk_draw_results_14`
  FOREIGN KEY (`draw_run_id`) REFERENCES `draw_runs` (`id`);

ALTER TABLE `draw_results` ADD CONSTRAINT `fk_draw_results_15`
  FOREIGN KEY (`event_prize_id`) REFERENCES `event_prizes` (`id`);

ALTER TABLE `reward_policies` ADD CONSTRAINT `fk_reward_policies_16`
  FOREIGN KEY (`game_id`) REFERENCES `games` (`id`);

ALTER TABLE `reward_policies` ADD CONSTRAINT `fk_reward_policies_17`
  FOREIGN KEY (`created_by`) REFERENCES `users` (`id`);

ALTER TABLE `draw_candidates` ADD CONSTRAINT `fk_draw_candidates_18`
  FOREIGN KEY (`draw_run_id`) REFERENCES `draw_runs` (`id`);

ALTER TABLE `draw_candidates` ADD CONSTRAINT `fk_draw_candidates_19`
  FOREIGN KEY (`participant_id`) REFERENCES `event_participants` (`id`);

ALTER TABLE `missions` ADD CONSTRAINT `fk_missions_20`
  FOREIGN KEY (`reward_policy_id`) REFERENCES `reward_policies` (`id`);

ALTER TABLE `missions` ADD CONSTRAINT `fk_missions_21`
  FOREIGN KEY (`created_by`) REFERENCES `users` (`id`);

ALTER TABLE `attendances` ADD CONSTRAINT `fk_attendances_22`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `mission_submissions` ADD CONSTRAINT `fk_mission_submissions_23`
  FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`);

ALTER TABLE `mission_submissions` ADD CONSTRAINT `fk_mission_submissions_24`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `mission_submissions` ADD CONSTRAINT `fk_mission_submissions_25`
  FOREIGN KEY (`reward_policy_id`) REFERENCES `reward_policies` (`id`);

ALTER TABLE `award_cancellations` ADD CONSTRAINT `fk_award_cancellations_26`
  FOREIGN KEY (`draw_result_id`) REFERENCES `draw_results` (`id`);

ALTER TABLE `award_cancellations` ADD CONSTRAINT `fk_award_cancellations_27`
  FOREIGN KEY (`canceled_by`) REFERENCES `users` (`id`);

ALTER TABLE `notification_jobs` ADD CONSTRAINT `fk_notification_jobs_28`
  FOREIGN KEY (`event_id`) REFERENCES `events` (`id`);

ALTER TABLE `notification_jobs` ADD CONSTRAINT `fk_notification_jobs_29`
  FOREIGN KEY (`source_job_id`) REFERENCES `notification_jobs` (`id`);

ALTER TABLE `notifications` ADD CONSTRAINT `fk_notifications_30`
  FOREIGN KEY (`job_id`) REFERENCES `notification_jobs` (`id`);

ALTER TABLE `notifications` ADD CONSTRAINT `fk_notifications_31`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `notifications` ADD CONSTRAINT `fk_notifications_32`
  FOREIGN KEY (`event_id`) REFERENCES `events` (`id`);

ALTER TABLE `event_entries` ADD CONSTRAINT `fk_event_entries_33`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `draw_runs` ADD CONSTRAINT `fk_draw_runs_34`
  FOREIGN KEY (`event_id`) REFERENCES `events` (`id`);

ALTER TABLE `abuse_cases` ADD CONSTRAINT `fk_abuse_cases_35`
  FOREIGN KEY (`event_id`) REFERENCES `events` (`id`);

ALTER TABLE `abuse_cases` ADD CONSTRAINT `fk_abuse_cases_36`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `abuse_cases` ADD CONSTRAINT `fk_abuse_cases_37`
  FOREIGN KEY (`attendance_id`) REFERENCES `attendances` (`id`);

ALTER TABLE `abuse_cases` ADD CONSTRAINT `fk_abuse_cases_38`
  FOREIGN KEY (`mission_submission_id`) REFERENCES `mission_submissions` (`id`);

ALTER TABLE `abuse_cases` ADD CONSTRAINT `fk_abuse_cases_39`
  FOREIGN KEY (`game_play_id`) REFERENCES `game_plays` (`id`);

ALTER TABLE `abuse_cases` ADD CONSTRAINT `fk_abuse_cases_40`
  FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`id`);

ALTER TABLE `audit_logs` ADD CONSTRAINT `fk_audit_logs_41`
  FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`);

ALTER TABLE `attendance_streaks` ADD CONSTRAINT `fk_attendance_streaks_42`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `attendance_streaks` ADD CONSTRAINT `fk_attendance_streaks_43`
  FOREIGN KEY (`policy_set_id`) REFERENCES `attendance_streak_policy_sets` (`id`);

ALTER TABLE `quiz_question_options` ADD CONSTRAINT `fk_quiz_question_options_44`
  FOREIGN KEY (`quiz_question_id`) REFERENCES `mission_quiz_questions` (`id`);

ALTER TABLE `quiz_answers` ADD CONSTRAINT `fk_quiz_answers_45`
  FOREIGN KEY (`submission_id`, `mission_id`) REFERENCES `mission_submissions` (`id`, `mission_id`);

ALTER TABLE `quiz_answers` ADD CONSTRAINT `fk_quiz_answers_46`
  FOREIGN KEY (`quiz_question_id`, `mission_id`) REFERENCES `mission_quiz_questions` (`id`, `mission_id`);

ALTER TABLE `quiz_answers` ADD CONSTRAINT `fk_quiz_answers_47`
  FOREIGN KEY (`selected_option_id`, `quiz_question_id`) REFERENCES `quiz_question_options` (`id`, `quiz_question_id`);

ALTER TABLE `quiz_question_progress` ADD CONSTRAINT `fk_quiz_question_progress_48`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `quiz_question_progress` ADD CONSTRAINT `fk_quiz_question_progress_49`
  FOREIGN KEY (`quiz_question_id`) REFERENCES `mission_quiz_questions` (`id`);

ALTER TABLE `quiz_question_progress` ADD CONSTRAINT `fk_quiz_question_progress_50`
  FOREIGN KEY (`correct_answer_id`) REFERENCES `quiz_answers` (`id`);

ALTER TABLE `survey_questions` ADD CONSTRAINT `fk_survey_questions_51`
  FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`);

ALTER TABLE `survey_question_options` ADD CONSTRAINT `fk_survey_question_options_52`
  FOREIGN KEY (`survey_question_id`) REFERENCES `survey_questions` (`id`);

ALTER TABLE `survey_answers` ADD CONSTRAINT `fk_survey_answers_53`
  FOREIGN KEY (`submission_id`, `mission_id`) REFERENCES `mission_submissions` (`id`, `mission_id`);

ALTER TABLE `survey_answers` ADD CONSTRAINT `fk_survey_answers_54`
  FOREIGN KEY (`survey_question_id`, `mission_id`) REFERENCES `survey_questions` (`id`, `mission_id`);

ALTER TABLE `survey_answers` ADD CONSTRAINT `fk_survey_answers_55`
  FOREIGN KEY (`selected_option_id`, `survey_question_id`) REFERENCES `survey_question_options` (`id`, `survey_question_id`);

ALTER TABLE `attendance_streak_policy_sets` ADD CONSTRAINT `fk_attendance_streak_policy_sets_56`
  FOREIGN KEY (`created_by`) REFERENCES `users` (`id`);

ALTER TABLE `attendance_reward_claims` ADD CONSTRAINT `fk_attendance_reward_claims_57`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `attendance_reward_claims` ADD CONSTRAINT `fk_attendance_reward_claims_58`
  FOREIGN KEY (`attendance_id`) REFERENCES `attendances` (`id`);

ALTER TABLE `attendance_reward_claims` ADD CONSTRAINT `fk_attendance_reward_claims_59`
  FOREIGN KEY (`reward_policy_id`) REFERENCES `reward_policies` (`id`);

ALTER TABLE `attendance_reward_claims` ADD CONSTRAINT `fk_attendance_reward_claims_60`
  FOREIGN KEY (`attendance_streak_policy_id`) REFERENCES `attendance_streak_policies` (`id`);

ALTER TABLE `game_reward_claims` ADD CONSTRAINT `fk_game_reward_claims_61`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `game_reward_claims` ADD CONSTRAINT `fk_game_reward_claims_62`
  FOREIGN KEY (`game_id`) REFERENCES `games` (`id`);

ALTER TABLE `game_reward_claims` ADD CONSTRAINT `fk_game_reward_claims_63`
  FOREIGN KEY (`reward_policy_id`) REFERENCES `reward_policies` (`id`);

ALTER TABLE `game_reward_claims` ADD CONSTRAINT `fk_game_reward_claims_64`
  FOREIGN KEY (`game_play_id`, `user_id`, `game_id`) REFERENCES `game_plays` (`id`, `user_id`, `game_id`);

ALTER TABLE `event_entries` ADD CONSTRAINT `fk_event_entries_65`
  FOREIGN KEY (`participant_id`, `user_id`) REFERENCES `event_participants` (`id`, `user_id`);

ALTER TABLE `award_cancellations` ADD CONSTRAINT `fk_award_cancellations_66`
  FOREIGN KEY (`draw_run_id`) REFERENCES `draw_runs` (`id`);

ALTER TABLE `draw_run_candidates` ADD CONSTRAINT `fk_draw_run_candidates_67`
  FOREIGN KEY (`draw_run_id`) REFERENCES `draw_runs` (`id`);

ALTER TABLE `draw_run_candidates` ADD CONSTRAINT `fk_draw_run_candidates_68`
  FOREIGN KEY (`candidate_id`) REFERENCES `draw_candidates` (`id`);

ALTER TABLE `draw_results` ADD CONSTRAINT `fk_draw_results_69`
  FOREIGN KEY (`draw_run_id`, `candidate_id`) REFERENCES `draw_run_candidates` (`draw_run_id`, `candidate_id`);

ALTER TABLE `draw_publications` ADD CONSTRAINT `fk_draw_publications_70`
  FOREIGN KEY (`event_id`) REFERENCES `events` (`id`);

ALTER TABLE `draw_publications` ADD CONSTRAINT `fk_draw_publications_71`
  FOREIGN KEY (`source_draw_id`) REFERENCES `draw_runs` (`id`);

ALTER TABLE `draw_publications` ADD CONSTRAINT `fk_draw_publications_72`
  FOREIGN KEY (`confirmed_by`) REFERENCES `users` (`id`);

ALTER TABLE `draw_failures` ADD CONSTRAINT `fk_draw_failures_73`
  FOREIGN KEY (`draw_run_id`) REFERENCES `draw_runs` (`id`);

ALTER TABLE `draw_checks` ADD CONSTRAINT `fk_draw_checks_74`
  FOREIGN KEY (`draw_run_id`) REFERENCES `draw_runs` (`id`);

ALTER TABLE `draw_checks` ADD CONSTRAINT `fk_draw_checks_75`
  FOREIGN KEY (`checked_by`) REFERENCES `users` (`id`);

ALTER TABLE `game_log_batches` ADD CONSTRAINT `fk_game_log_batches_76`
  FOREIGN KEY (`game_play_id`) REFERENCES `game_plays` (`id`);

ALTER TABLE `game_play_analyses` ADD CONSTRAINT `fk_game_play_analyses_77`
  FOREIGN KEY (`game_play_id`) REFERENCES `game_plays` (`id`);

ALTER TABLE `tickets` ADD CONSTRAINT `fk_tickets_78`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

ALTER TABLE `tickets` ADD CONSTRAINT `fk_tickets_79`
  FOREIGN KEY (`attendance_reward_claim_id`) REFERENCES `attendance_reward_claims` (`id`);

ALTER TABLE `tickets` ADD CONSTRAINT `fk_tickets_80`
  FOREIGN KEY (`mission_reward_claim_id`) REFERENCES `mission_reward_claims` (`id`);

ALTER TABLE `tickets` ADD CONSTRAINT `fk_tickets_81`
  FOREIGN KEY (`game_reward_claim_id`) REFERENCES `game_reward_claims` (`id`);

ALTER TABLE `ticket_histories` ADD CONSTRAINT `fk_ticket_histories_82`
  FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`);

ALTER TABLE `ticket_histories` ADD CONSTRAINT `fk_ticket_histories_83`
  FOREIGN KEY (`event_entry_id`) REFERENCES `event_entries` (`id`);
