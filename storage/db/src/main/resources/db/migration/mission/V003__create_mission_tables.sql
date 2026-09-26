-- Source: docs/03-database/schema.dbml
-- Tables owned by mission. Foreign keys are added in V012.

CREATE TABLE `missions` (
  `id` BINARY(16) NOT NULL,
  `reward_policy_id` BINARY(16) NOT NULL,
  `created_by` BINARY(16) NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `description` TEXT NOT NULL,
  `mission_type` ENUM('SURVEY', 'QUIZ') NOT NULL,
  `starts_at` DATETIME NOT NULL,
  `ends_at` DATETIME NOT NULL,
  `status` ENUM('DRAFT', 'ACTIVE', 'ENDED') NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `image_key` VARCHAR(500),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `mission_submissions` (
  `id` BINARY(16) NOT NULL,
  `mission_id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `reward_policy_id` BINARY(16) NOT NULL,
  `idempotency_key` VARCHAR(100) NOT NULL,
  `received_at` DATETIME NOT NULL,
  `is_completed` BOOLEAN NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mission_submissions_1` (`user_id`, `mission_id`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `reward_claims` (
  `id` BINARY(16) NOT NULL,
  `user_id` BINARY(16) NOT NULL,
  `reward_policy_id` BINARY(16) NOT NULL,
  `mission_id` BINARY(16) NOT NULL,
  `mission_submission_id` BINARY(16) NOT NULL,
  `source_key` VARCHAR(160) NOT NULL,
  `ticket_count` INT NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_reward_claims_1` (`user_id`, `mission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `mission_quizzes` (
  `id` BINARY(16) NOT NULL,
  `mission_id` BINARY(16) NOT NULL,
  `question_type` ENUM('OX', 'SINGLE_CHOICE', 'SHORT_ANSWER') NOT NULL,
  `question_text` TEXT NOT NULL,
  `display_order` INT NOT NULL,
  `created_at` DATETIME NOT NULL,
  `correct_answer` TEXT,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mission_quizzes_1` (`mission_id`, `display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `quiz_question_options` (
  `id` BINARY(16) NOT NULL,
  `quiz_question_id` BINARY(16) NOT NULL,
  `option_text` TEXT NOT NULL,
  `display_order` INT NOT NULL,
  `is_correct` BOOLEAN NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_quiz_question_options_1` (`quiz_question_id`, `display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `quiz_answers` (
  `id` BINARY(16) NOT NULL,
  `submission_id` BINARY(16) NOT NULL,
  `quiz_question_id` BINARY(16) NOT NULL,
  `selected_option_id` BINARY(16),
  `answer_text` TEXT,
  `is_correct` BOOLEAN NOT NULL,
  `graded_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_quiz_answers_1` (`submission_id`, `quiz_question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `quiz_question_progress` (
  `user_id` BINARY(16) NOT NULL,
  `quiz_question_id` BINARY(16) NOT NULL,
  `correct_answer_id` BINARY(16) NOT NULL,
  `completed_at` DATETIME NOT NULL,
  `id` BINARY(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_quiz_question_progress_1` (`user_id`, `quiz_question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `survey_questions` (
  `id` BINARY(16) NOT NULL,
  `mission_id` BINARY(16) NOT NULL,
  `question_type` ENUM('SINGLE_CHOICE', 'FREE_TEXT') NOT NULL,
  `question_text` TEXT NOT NULL,
  `is_required` BOOLEAN NOT NULL,
  `display_order` INT NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_survey_questions_1` (`mission_id`, `display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `survey_question_options` (
  `id` BINARY(16) NOT NULL,
  `survey_question_id` BINARY(16) NOT NULL,
  `option_text` TEXT NOT NULL,
  `display_order` INT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_survey_question_options_1` (`survey_question_id`, `display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `survey_answers` (
  `id` BINARY(16) NOT NULL,
  `submission_id` BINARY(16) NOT NULL,
  `survey_question_id` BINARY(16) NOT NULL,
  `selected_option_id` BINARY(16),
  `answer_text` TEXT,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_survey_answers_1` (`submission_id`, `survey_question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
