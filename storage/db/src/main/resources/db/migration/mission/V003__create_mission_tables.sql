-- Source: docs/03-database/schema.dbml
-- GD-93: Initial schema; version numbers retained by user request.
-- Initial tables and foreign keys owned by mission.

CREATE TABLE `missions` (
  `id` binary(16) NOT NULL COMMENT '미션 ID',
  `reward_policy_id` binary(16) NOT NULL COMMENT '적용 보상 정책 ID',
  `created_by` binary(16) NOT NULL COMMENT '등록 관리자 ID',
  `title` varchar(200) NOT NULL COMMENT '미션 제목',
  `description` text NOT NULL COMMENT '상세 설명',
  `mission_type` ENUM ('SURVEY', 'QUIZ') NOT NULL COMMENT 'SURVEY=설문 / QUIZ=퀴즈',
  `starts_at` datetime(6) NOT NULL COMMENT '미션 운영 시작 시각 UTC',
  `ends_at` datetime(6) NOT NULL COMMENT '미션 운영 종료 시각 UTC',
  `status` ENUM ('ACTIVE', 'ENDED') NOT NULL COMMENT 'ACTIVE=운영 중 / ENDED=종료',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각 UTC',
  `image_key` varchar(500) COMMENT '미션 설명 이미지 한 개의 저장소 경로',
  CONSTRAINT `chk_mission_dates` CHECK (ends_at > starts_at),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_missions_1` FOREIGN KEY (`reward_policy_id`) REFERENCES `reward_policies` (`id`),
  CONSTRAINT `fk_missions_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `mission_submissions` (
  `id` binary(16) NOT NULL COMMENT '미션 제출 ID',
  `mission_id` binary(16) NOT NULL COMMENT '미션 ID',
  `reward_policy_id` binary(16) NOT NULL COMMENT '적용 보상 정책 ID',
  `user_id` binary(16) NOT NULL COMMENT '사용자 ID',
  `is_completed` boolean NOT NULL COMMENT 'true=완료 / false=미완료',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mission_submissions_2` (`id`, `mission_id`),
  CONSTRAINT `fk_mission_submissions_1` FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`),
  CONSTRAINT `fk_mission_submissions_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_mission_submissions_3` FOREIGN KEY (`reward_policy_id`) REFERENCES `reward_policies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `mission_quiz_questions` (
  `id` binary(16) NOT NULL COMMENT '퀴즈 문항 ID',
  `mission_id` binary(16) NOT NULL COMMENT '퀴즈 미션 ID',
  `question_type` ENUM ('OX', 'SINGLE_CHOICE', 'SHORT_ANSWER') NOT NULL COMMENT 'OX / 단일 선택 / 단답형',
  `question_text` text NOT NULL COMMENT '문제 내용',
  `display_order` int NOT NULL COMMENT '문항 표시 순서',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  `correct_answer` text COMMENT '단답형 정답 하나',
  CONSTRAINT `chk_quiz_correct_answer` CHECK ((question_type = 'SHORT_ANSWER' AND correct_answer IS NOT NULL) OR (question_type <> 'SHORT_ANSWER' AND correct_answer IS NULL)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mission_quiz_questions_1` (`mission_id`, `display_order`),
  UNIQUE KEY `uq_mission_quiz_questions_2` (`id`, `mission_id`),
  CONSTRAINT `fk_mission_quiz_questions_1` FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `quiz_question_options` (
  `id` binary(16) NOT NULL COMMENT '퀴즈 선택지 ID',
  `quiz_question_id` binary(16) NOT NULL COMMENT '퀴즈 문항 ID',
  `option_text` text NOT NULL COMMENT '선택지 내용',
  `display_order` int NOT NULL COMMENT '선택지 순서',
  `is_correct` boolean NOT NULL COMMENT '정답 여부',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_quiz_question_options_1` (`quiz_question_id`, `display_order`),
  UNIQUE KEY `uq_quiz_question_options_2` (`id`, `quiz_question_id`),
  CONSTRAINT `fk_quiz_question_options_1` FOREIGN KEY (`quiz_question_id`) REFERENCES `mission_quiz_questions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `quiz_answers` (
  `id` binary(16) NOT NULL COMMENT '문항 답변 및 채점 기록 ID',
  `submission_id` binary(16) NOT NULL COMMENT '제출 요청 ID',
  `quiz_question_id` binary(16) NOT NULL COMMENT '응답 문항 ID',
  `selected_option_id` binary(16) COMMENT 'OX 및 단일 선택에서 필수, 단답형은 NULL',
  `mission_id` binary(16) NOT NULL COMMENT '제출과 문항의 소속 미션',
  `answer_text` text COMMENT '단답형 입력, 선택형은 NULL',
  `is_correct` boolean NOT NULL COMMENT '서버 채점 결과',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  CONSTRAINT `chk_quiz_answer_shape` CHECK (selected_option_id IS NOT NULL OR answer_text IS NOT NULL),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_quiz_answers_1` (`submission_id`, `quiz_question_id`),
  CONSTRAINT `fk_quiz_answers_1` FOREIGN KEY (`submission_id`, `mission_id`) REFERENCES `mission_submissions` (`id`, `mission_id`),
  CONSTRAINT `fk_quiz_answers_2` FOREIGN KEY (`quiz_question_id`, `mission_id`) REFERENCES `mission_quiz_questions` (`id`, `mission_id`),
  CONSTRAINT `fk_quiz_answers_3` FOREIGN KEY (`selected_option_id`, `quiz_question_id`) REFERENCES `quiz_question_options` (`id`, `quiz_question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `quiz_question_progress` (
  `id` binary(16) NOT NULL COMMENT '문항 정답 진행 ID',
  `quiz_question_id` binary(16) NOT NULL COMMENT '정답으로 완료한 퀴즈 문항 ID',
  `correct_answer_id` binary(16) NOT NULL COMMENT '정답 판정의 근거 답변 ID',
  `user_id` binary(16) NOT NULL COMMENT '사용자 ID',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_quiz_question_progress_1` (`user_id`, `quiz_question_id`),
  CONSTRAINT `fk_quiz_question_progress_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_quiz_question_progress_2` FOREIGN KEY (`quiz_question_id`) REFERENCES `mission_quiz_questions` (`id`),
  CONSTRAINT `fk_quiz_question_progress_3` FOREIGN KEY (`correct_answer_id`) REFERENCES `quiz_answers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `survey_questions` (
  `id` binary(16) NOT NULL COMMENT '설문 문항 ID',
  `mission_id` binary(16) NOT NULL COMMENT '설문 미션 ID',
  `question_type` ENUM ('SINGLE_CHOICE', 'FREE_TEXT') NOT NULL COMMENT '단일 선택 또는 자유 입력',
  `question_text` text NOT NULL COMMENT '질문 내용',
  `is_required` boolean NOT NULL COMMENT '필수 응답 여부',
  `display_order` int NOT NULL COMMENT '문항 순서',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  `updated_at` datetime(6) NOT NULL COMMENT '마지막 수정 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_survey_questions_1` (`mission_id`, `display_order`),
  UNIQUE KEY `uq_survey_questions_2` (`id`, `mission_id`),
  CONSTRAINT `fk_survey_questions_1` FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `survey_question_options` (
  `id` binary(16) NOT NULL COMMENT '설문 선택지 ID',
  `survey_question_id` binary(16) NOT NULL COMMENT '단일 선택 설문 문항 ID',
  `option_text` text NOT NULL COMMENT '선택지 내용',
  `display_order` int NOT NULL COMMENT '선택지 순서',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_survey_question_options_1` (`survey_question_id`, `display_order`),
  UNIQUE KEY `uq_survey_question_options_2` (`id`, `survey_question_id`),
  CONSTRAINT `fk_survey_question_options_1` FOREIGN KEY (`survey_question_id`) REFERENCES `survey_questions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `survey_answers` (
  `id` binary(16) NOT NULL COMMENT '설문 응답 ID',
  `submission_id` binary(16) NOT NULL COMMENT '최종 제출 ID',
  `mission_id` binary(16) NOT NULL COMMENT '제출과 문항의 소속 미션',
  `survey_question_id` binary(16) NOT NULL COMMENT '설문 문항 ID',
  `selected_option_id` binary(16) COMMENT '단일 선택 답변',
  `answer_text` text COMMENT '자유 입력 답변',
  `created_at` datetime(6) NOT NULL COMMENT '응답 저장 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_survey_answers_1` (`submission_id`, `survey_question_id`),
  CONSTRAINT `fk_survey_answers_1` FOREIGN KEY (`submission_id`, `mission_id`) REFERENCES `mission_submissions` (`id`, `mission_id`),
  CONSTRAINT `fk_survey_answers_2` FOREIGN KEY (`survey_question_id`, `mission_id`) REFERENCES `survey_questions` (`id`, `mission_id`),
  CONSTRAINT `fk_survey_answers_3` FOREIGN KEY (`selected_option_id`, `survey_question_id`) REFERENCES `survey_question_options` (`id`, `survey_question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `mission_reward_claims` (
  `id` binary(16) NOT NULL COMMENT '미션 보상 지급 ID',
  `reward_policy_id` binary(16) NOT NULL COMMENT '적용 미션 보상 정책 ID',
  `mission_id` binary(16) NOT NULL COMMENT '완료 미션 ID',
  `mission_submission_id` binary(16) NOT NULL COMMENT '최종 완료 근거 제출 ID',
  `user_id` binary(16) NOT NULL COMMENT '보상 대상 사용자 ID',
  `source_key` varchar(160) NOT NULL COMMENT '사용자별 미션 보상 식별 키',
  `ticket_count` int NOT NULL COMMENT '실제 지급 수량',
  `created_at` datetime(6) NOT NULL COMMENT '보상 지급 이력 생성 시각 UTC',
  CONSTRAINT `chk_mission_claim_ticket` CHECK (ticket_count = 1),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mission_reward_claims_owner` (`id`, `user_id`),
  UNIQUE KEY `uq_mission_reward_claims_1` (`user_id`, `mission_id`),
  CONSTRAINT `fk_mission_reward_claims_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_mission_reward_claims_2` FOREIGN KEY (`reward_policy_id`) REFERENCES `reward_policies` (`id`),
  CONSTRAINT `fk_mission_reward_claims_3` FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`),
  CONSTRAINT `fk_mission_reward_claims_4` FOREIGN KEY (`mission_submission_id`) REFERENCES `mission_submissions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
