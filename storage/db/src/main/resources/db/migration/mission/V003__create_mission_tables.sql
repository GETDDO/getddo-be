-- Source: docs/03-database/schema.dbml
-- Initial tables and foreign keys owned by mission.

CREATE TABLE `missions` (
  `id` BINARY(16) NOT NULL,
  `reward_policy_id` BINARY(16) NOT NULL,
  `created_by` BINARY(16) NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `description` TEXT NOT NULL,
  `mission_type` ENUM('SURVEY', 'QUIZ') NOT NULL,
  `starts_at` DATETIME(6) NOT NULL,
  `ends_at` DATETIME(6) NOT NULL,
  `status` ENUM('DRAFT', 'ACTIVE', 'ENDED') NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `image_key` VARCHAR(500),
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_mission_dates` CHECK (`ends_at` > `starts_at`),
  CONSTRAINT `fk_missions_1` FOREIGN KEY (`reward_policy_id`) REFERENCES `reward_policies` (`id`),
  CONSTRAINT `fk_missions_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `mission_submissions` (
  `id` BINARY(16) NOT NULL,
  `mission_id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `reward_policy_id` BINARY(16) NOT NULL,
  `idempotency_key` VARCHAR(100) NOT NULL,
  `received_at` DATETIME(6) NOT NULL,
  `is_completed` BOOLEAN NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mission_submissions_1` (`user_id`, `mission_id`, `idempotency_key`),
  UNIQUE KEY `uq_mission_submissions_2` (`id`, `mission_id`),
  CONSTRAINT `fk_mission_submissions_1` FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`),
  CONSTRAINT `fk_mission_submissions_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_mission_submissions_3` FOREIGN KEY (`reward_policy_id`) REFERENCES `reward_policies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `mission_reward_claims` (
  `id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `reward_policy_id` BINARY(16) NOT NULL,
  `mission_id` BINARY(16) NOT NULL,
  `mission_submission_id` BINARY(16) NOT NULL,
  `source_key` VARCHAR(160) NOT NULL,
  `ticket_count` INT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_reward_claims_1` (`user_id`, `mission_id`),
  CONSTRAINT `chk_mission_claim_ticket` CHECK (`ticket_count` >= 1),
  CONSTRAINT `fk_reward_claims_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_reward_claims_2` FOREIGN KEY (`reward_policy_id`) REFERENCES `reward_policies` (`id`),
  CONSTRAINT `fk_reward_claims_3` FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`),
  CONSTRAINT `fk_reward_claims_4` FOREIGN KEY (`mission_submission_id`) REFERENCES `mission_submissions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `mission_quiz_questions` (
  `id` BINARY(16) NOT NULL,
  `mission_id` BINARY(16) NOT NULL,
  `question_type` ENUM('OX', 'SINGLE_CHOICE', 'SHORT_ANSWER') NOT NULL,
  `question_text` TEXT NOT NULL,
  `display_order` INT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `correct_answer` TEXT,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mission_quizzes_1` (`mission_id`, `display_order`),
  CONSTRAINT `chk_quiz_correct_answer` CHECK (
    (`question_type` = 'SHORT_ANSWER' AND `correct_answer` IS NOT NULL)
    OR (`question_type` <> 'SHORT_ANSWER' AND `correct_answer` IS NULL)
  ),
  UNIQUE KEY `uq_mission_quiz_questions_2` (`id`, `mission_id`),
  CONSTRAINT `fk_mission_quizzes_1` FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `quiz_question_options` (
  `id` BINARY(16) NOT NULL,
  `quiz_question_id` BINARY(16) NOT NULL,
  `option_text` TEXT NOT NULL,
  `display_order` INT NOT NULL,
  `is_correct` BOOLEAN NOT NULL,
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_quiz_question_options_1` (`quiz_question_id`, `display_order`),
  UNIQUE KEY `uq_quiz_question_options_2` (`id`, `quiz_question_id`),
  CONSTRAINT `fk_quiz_question_options_1` FOREIGN KEY (`quiz_question_id`) REFERENCES `mission_quiz_questions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `quiz_answers` (
  `id` BINARY(16) NOT NULL,
  `submission_id` BINARY(16) NOT NULL,
  `mission_id` BINARY(16) NOT NULL COMMENT '제출과 문항의 소속 미션',
  `quiz_question_id` BINARY(16) NOT NULL,
  `selected_option_id` BINARY(16),
  `answer_text` TEXT,
  `is_correct` BOOLEAN NOT NULL,
  `graded_at` DATETIME(6) NOT NULL,
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_quiz_answers_1` (`submission_id`, `quiz_question_id`),
  CONSTRAINT `chk_quiz_answer_shape` CHECK (
    `selected_option_id` IS NOT NULL OR `answer_text` IS NOT NULL
  ),
  CONSTRAINT `fk_quiz_answers_1` FOREIGN KEY (`submission_id`, `mission_id`) REFERENCES `mission_submissions` (`id`, `mission_id`),
  CONSTRAINT `fk_quiz_answers_2` FOREIGN KEY (`quiz_question_id`, `mission_id`) REFERENCES `mission_quiz_questions` (`id`, `mission_id`),
  CONSTRAINT `fk_quiz_answers_3` FOREIGN KEY (`selected_option_id`, `quiz_question_id`) REFERENCES `quiz_question_options` (`id`, `quiz_question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `quiz_question_progress` (
  `user_id` BINARY(16) NOT NULL,
  `quiz_question_id` BINARY(16) NOT NULL,
  `correct_answer_id` BINARY(16) NOT NULL,
  `completed_at` DATETIME(6) NOT NULL,
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  `id` BINARY(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_quiz_question_progress_1` (`user_id`, `quiz_question_id`),
  CONSTRAINT `fk_quiz_question_progress_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_quiz_question_progress_2` FOREIGN KEY (`quiz_question_id`) REFERENCES `mission_quiz_questions` (`id`),
  CONSTRAINT `fk_quiz_question_progress_3` FOREIGN KEY (`correct_answer_id`) REFERENCES `quiz_answers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `survey_questions` (
  `id` BINARY(16) NOT NULL,
  `mission_id` BINARY(16) NOT NULL,
  `question_type` ENUM('SINGLE_CHOICE', 'FREE_TEXT') NOT NULL,
  `question_text` TEXT NOT NULL,
  `is_required` BOOLEAN NOT NULL,
  `display_order` INT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_survey_questions_1` (`mission_id`, `display_order`),
  UNIQUE KEY `uq_survey_questions_2` (`id`, `mission_id`),
  CONSTRAINT `fk_survey_questions_1` FOREIGN KEY (`mission_id`) REFERENCES `missions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `survey_question_options` (
  `id` BINARY(16) NOT NULL,
  `survey_question_id` BINARY(16) NOT NULL,
  `option_text` TEXT NOT NULL,
  `display_order` INT NOT NULL,
  `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_survey_question_options_1` (`survey_question_id`, `display_order`),
  UNIQUE KEY `uq_survey_question_options_2` (`id`, `survey_question_id`),
  CONSTRAINT `fk_survey_question_options_1` FOREIGN KEY (`survey_question_id`) REFERENCES `survey_questions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `survey_answers` (
  `id` BINARY(16) NOT NULL,
  `submission_id` BINARY(16) NOT NULL,
  `mission_id` BINARY(16) NOT NULL COMMENT '제출과 문항의 소속 미션',
  `survey_question_id` BINARY(16) NOT NULL,
  `selected_option_id` BINARY(16),
  `answer_text` TEXT,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_survey_answers_1` (`submission_id`, `survey_question_id`),
  CONSTRAINT `fk_survey_answers_1` FOREIGN KEY (`submission_id`, `mission_id`) REFERENCES `mission_submissions` (`id`, `mission_id`),
  CONSTRAINT `fk_survey_answers_2` FOREIGN KEY (`survey_question_id`, `mission_id`) REFERENCES `survey_questions` (`id`, `mission_id`),
  CONSTRAINT `fk_survey_answers_3` FOREIGN KEY (`selected_option_id`, `survey_question_id`) REFERENCES `survey_question_options` (`id`, `survey_question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
