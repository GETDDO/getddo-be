-- 출석·미션 정합성 보강과 시각 정밀도 통일
-- 담당: 윤태형 (응모권·일회성 미션·게임·출석·응모)
-- 근거: getddo-spec/02-domain/{attendance,mission,entry}.md
-- 기존 V001~V012는 수정하지 않고 이 마이그레이션으로 추가한다.
-- 검증: MySQL 8.0.46에 V001~V013 순서로 적용 확인

-- ===============================================================
-- 1. 이름 통일
--    users_id는 이 두 테이블에만 있었고 나머지는 모두 user_id다.
--    보상 지급 이력은 attendance_reward_claims, game_reward_claims와
--    형제 관계이므로 미션임을 이름에 드러낸다.
--    문항 테이블 이름과 참조 컬럼명(quiz_question_id)을 맞춘다.
-- ===============================================================
ALTER TABLE `attendances`        RENAME COLUMN `users_id` TO `user_id`, ALGORITHM=INPLACE;
ALTER TABLE `attendance_streaks` RENAME COLUMN `users_id` TO `user_id`, ALGORITHM=INPLACE;

RENAME TABLE `reward_claims`   TO `mission_reward_claims`;
RENAME TABLE `mission_quizzes` TO `mission_quiz_questions`;

-- FK에 참여하는 컬럼은 제약을 떼고 개명한 뒤 다시 붙인다.
ALTER TABLE `ticket_ledger` DROP FOREIGN KEY `fk_ticket_ledger_3`;
ALTER TABLE `ticket_ledger` RENAME COLUMN `reward_claim_id` TO `mission_reward_claim_id`;
ALTER TABLE `ticket_ledger`
  ADD CONSTRAINT `fk_ticket_ledger_3` FOREIGN KEY (`mission_reward_claim_id`)
    REFERENCES `mission_reward_claims` (`id`);

-- ===============================================================
-- 2. 출석 기준일과 일일 중복 방지
--    attendance.md: 같은 사용자의 같은 출석 기준일에는 출석을 한 번만
--    인정한다. 연타·새로고침·동시 요청·날짜 변경 경계에서도 한 번만
--    반영한다. created_at은 UTC 시각이라 KST 업무일을 표현하지 못한다.
-- ===============================================================
ALTER TABLE `attendances`
  ADD COLUMN `attendance_date` DATE NOT NULL COMMENT '출석 기준 날짜 KST' AFTER `user_id`,
  ADD UNIQUE KEY `uq_attendances_1` (`user_id`, `attendance_date`);

-- ===============================================================
-- 3. 출석 보상 지급 형태와 연속 출석 단계 범위
--    DAILY는 기본 출석 정책으로, STREAK는 연속 출석 단계로 지급한다.
--    attendance.md: 단계 일수는 1~28일 범위이며 월 단위로 초기화하므로
--    28일을 초과하는 단계는 두지 않는다.
-- ===============================================================
ALTER TABLE `attendance_reward_claims`
  ADD CONSTRAINT `chk_attendance_claim_shape` CHECK (
    (`reward_type` = 'DAILY'
       AND `reward_policy_id` IS NOT NULL
       AND `attendance_streak_policy_id` IS NULL
       AND `milestone_days` IS NULL)
    OR
    (`reward_type` = 'STREAK'
       AND `attendance_streak_policy_id` IS NOT NULL
       AND `milestone_days` IS NOT NULL
       AND `reward_policy_id` IS NULL)
  ),
  ADD CONSTRAINT `chk_attendance_claim_ticket` CHECK (`ticket_count` >= 0);

ALTER TABLE `attendance_streak_policies`
  ADD CONSTRAINT `chk_streak_policy_milestone` CHECK (`milestone_days` BETWEEN 1 AND 28),
  ADD CONSTRAINT `chk_streak_policy_reward` CHECK (`reward_ticket_count` >= 0);

ALTER TABLE `attendance_streaks`
  ADD CONSTRAINT `chk_streak_days` CHECK (`consecutive_days` BETWEEN 0 AND 28);

-- ===============================================================
-- 4. 미션 운영 기간과 정답 보관 위치
--    events에는 chk_event_dates가 있으나 missions에는 없었다.
--    단답형 정답은 correct_answer 한 개이며 OX·객관식 정답은
--    quiz_question_options.is_correct에서 관리한다.
-- ===============================================================
ALTER TABLE `missions`
  ADD CONSTRAINT `chk_mission_dates` CHECK (`ends_at` > `starts_at`);

ALTER TABLE `mission_quiz_questions`
  ADD CONSTRAINT `chk_quiz_correct_answer` CHECK (
    (`question_type` = 'SHORT_ANSWER' AND `correct_answer` IS NOT NULL)
    OR (`question_type` <> 'SHORT_ANSWER' AND `correct_answer` IS NULL)
  );

-- ===============================================================
-- 5. 답변의 소속 검증 (복합 외래 키)
--    단일 컬럼 FK는 대상의 존재만 확인하므로 다른 미션의 문항이나
--    다른 문항의 보기를 가리키는 답변을 막지 못한다.
--    V012의 ticket_ledger·game_reward_claims·event_entries와 같은 방식이다.
--    제출 행은 그대로 누적되며 오답 재도전에는 영향이 없다.
-- ===============================================================
ALTER TABLE `mission_submissions`     ADD UNIQUE KEY `uq_mission_submissions_2` (`id`, `mission_id`);
ALTER TABLE `mission_quiz_questions`  ADD UNIQUE KEY `uq_mission_quiz_questions_2` (`id`, `mission_id`);
ALTER TABLE `quiz_question_options`   ADD UNIQUE KEY `uq_quiz_question_options_2` (`id`, `quiz_question_id`);
ALTER TABLE `survey_questions`        ADD UNIQUE KEY `uq_survey_questions_2` (`id`, `mission_id`);
ALTER TABLE `survey_question_options` ADD UNIQUE KEY `uq_survey_question_options_2` (`id`, `survey_question_id`);

ALTER TABLE `quiz_answers`
  ADD COLUMN `mission_id` BINARY(16) NOT NULL COMMENT '제출과 문항의 소속 미션' AFTER `submission_id`;
ALTER TABLE `quiz_answers`
  DROP FOREIGN KEY `fk_quiz_answers_1`,
  DROP FOREIGN KEY `fk_quiz_answers_2`,
  DROP FOREIGN KEY `fk_quiz_answers_3`;
ALTER TABLE `quiz_answers`
  ADD CONSTRAINT `fk_quiz_answers_1` FOREIGN KEY (`submission_id`, `mission_id`)
    REFERENCES `mission_submissions` (`id`, `mission_id`),
  ADD CONSTRAINT `fk_quiz_answers_2` FOREIGN KEY (`quiz_question_id`, `mission_id`)
    REFERENCES `mission_quiz_questions` (`id`, `mission_id`),
  ADD CONSTRAINT `fk_quiz_answers_3` FOREIGN KEY (`selected_option_id`, `quiz_question_id`)
    REFERENCES `quiz_question_options` (`id`, `quiz_question_id`);

ALTER TABLE `survey_answers`
  ADD COLUMN `mission_id` BINARY(16) NOT NULL COMMENT '제출과 문항의 소속 미션' AFTER `submission_id`;
ALTER TABLE `survey_answers`
  DROP FOREIGN KEY `fk_survey_answers_1`,
  DROP FOREIGN KEY `fk_survey_answers_2`,
  DROP FOREIGN KEY `fk_survey_answers_3`;
ALTER TABLE `survey_answers`
  ADD CONSTRAINT `fk_survey_answers_1` FOREIGN KEY (`submission_id`, `mission_id`)
    REFERENCES `mission_submissions` (`id`, `mission_id`),
  ADD CONSTRAINT `fk_survey_answers_2` FOREIGN KEY (`survey_question_id`, `mission_id`)
    REFERENCES `survey_questions` (`id`, `mission_id`),
  ADD CONSTRAINT `fk_survey_answers_3` FOREIGN KEY (`selected_option_id`, `survey_question_id`)
    REFERENCES `survey_question_options` (`id`, `survey_question_id`);

-- ===============================================================
-- 6. 빈 퀴즈 답변 방지
--    채점 대상 답변은 선택지 또는 답변 문자열 중 하나를 가진다.
--    설문은 건너뛴 문항의 표현 방식이 미확정이라 제외한다.
-- ===============================================================
ALTER TABLE `quiz_answers`
  ADD CONSTRAINT `chk_quiz_answer_shape` CHECK (
    `selected_option_id` IS NOT NULL OR `answer_text` IS NOT NULL
  );

-- ===============================================================
-- 7. 공통 엔티티 정렬
--    BaseEntity는 id와 created_at을 요구한다. 아래 6개 테이블에는
--    created_at이 없어 공통 엔티티를 상속할 수 없었다.
--    기존 시각 컬럼(graded_at, completed_at)은 의미가 달라 유지한다.
-- ===============================================================
ALTER TABLE `attendance_streaks`
  ADD COLUMN `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC' AFTER `last_attendance_date`;
ALTER TABLE `quiz_answers`
  ADD COLUMN `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC' AFTER `graded_at`;
ALTER TABLE `quiz_question_progress`
  ADD COLUMN `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC' AFTER `completed_at`;
ALTER TABLE `quiz_question_options`
  ADD COLUMN `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC';
ALTER TABLE `survey_question_options`
  ADD COLUMN `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC';
ALTER TABLE `ticket_ledger_allocations`
  ADD COLUMN `created_at` DATETIME(6) NOT NULL COMMENT '생성 시각 UTC';

-- ===============================================================
-- 8. 시각 정밀도 통일 — DATETIME → DATETIME(6)
--    38개 테이블의 datetime 컬럼 79개가 초 단위였다.
--    entry.md는 "정확히 마감 시각부터는 응모할 수 없다"로 경계를 시각
--    기준으로 정하고, attendance.md는 날짜 변경 경계의 동시 요청을
--    한 번만 반영하도록 요구한다. 초 단위로는 같은 초에 도착한 요청의
--    순서를 구분할 수 없다.
-- ===============================================================
ALTER TABLE `abuse_cases` MODIFY COLUMN `occurred_at` DATETIME(6) NOT NULL;
ALTER TABLE `abuse_cases` MODIFY COLUMN `detected_at` DATETIME(6) NOT NULL;
ALTER TABLE `abuse_cases` MODIFY COLUMN `reviewed_at` DATETIME(6);
ALTER TABLE `attendance_reward_claims` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `attendance_streak_policies` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `attendance_streak_policy_sets` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `attendance_streaks` MODIFY COLUMN `updated_at` DATETIME(6) NOT NULL;
ALTER TABLE `attendances` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `audit_logs` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `award_cancellations` MODIFY COLUMN `canceled_at` DATETIME(6) NOT NULL;
ALTER TABLE `banners` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `banners` MODIFY COLUMN `updated_at` DATETIME(6) NOT NULL;
ALTER TABLE `current_awards` MODIFY COLUMN `assigned_at` DATETIME(6) NOT NULL;
ALTER TABLE `draw_candidates` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `draw_results` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `draw_runs` MODIFY COLUMN `snapshot_fixed_at` DATETIME(6);
ALTER TABLE `draw_runs` MODIFY COLUMN `started_at` DATETIME(6);
ALTER TABLE `draw_runs` MODIFY COLUMN `confirmed_at` DATETIME(6);
ALTER TABLE `draw_runs` MODIFY COLUMN `last_failed_at` DATETIME(6);
ALTER TABLE `draw_runs` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `event_entries` MODIFY COLUMN `requested_at` DATETIME(6) NOT NULL;
ALTER TABLE `event_entries` MODIFY COLUMN `accepted_at` DATETIME(6);
ALTER TABLE `event_entries` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `event_participants` MODIFY COLUMN `excluded_at` DATETIME(6);
ALTER TABLE `event_participants` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `event_prizes` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `event_prizes` MODIFY COLUMN `updated_at` DATETIME(6) NOT NULL;
ALTER TABLE `events` MODIFY COLUMN `starts_at` DATETIME(6) NOT NULL;
ALTER TABLE `events` MODIFY COLUMN `ends_at` DATETIME(6) NOT NULL;
ALTER TABLE `events` MODIFY COLUMN `suspended_at` DATETIME(6);
ALTER TABLE `events` MODIFY COLUMN `canceled_at` DATETIME(6);
ALTER TABLE `events` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `events` MODIFY COLUMN `updated_at` DATETIME(6) NOT NULL;
ALTER TABLE `game_plays` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `game_plays` MODIFY COLUMN `completed_at` DATETIME(6);
ALTER TABLE `game_reward_claims` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `games` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `games` MODIFY COLUMN `updated_at` DATETIME(6) NOT NULL;
ALTER TABLE `mission_quiz_questions` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `mission_submissions` MODIFY COLUMN `received_at` DATETIME(6) NOT NULL;
ALTER TABLE `mission_submissions` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `missions` MODIFY COLUMN `starts_at` DATETIME(6) NOT NULL;
ALTER TABLE `missions` MODIFY COLUMN `ends_at` DATETIME(6) NOT NULL;
ALTER TABLE `missions` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `missions` MODIFY COLUMN `updated_at` DATETIME(6) NOT NULL;
ALTER TABLE `notification_jobs` MODIFY COLUMN `scheduled_at` DATETIME(6) NOT NULL;
ALTER TABLE `notification_jobs` MODIFY COLUMN `next_attempt_at` DATETIME(6);
ALTER TABLE `notification_jobs` MODIFY COLUMN `lease_until` DATETIME(6);
ALTER TABLE `notification_jobs` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `notification_jobs` MODIFY COLUMN `completed_at` DATETIME(6);
ALTER TABLE `notifications` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `notifications` MODIFY COLUMN `mock_sent_at` DATETIME(6);
ALTER TABLE `notifications` MODIFY COLUMN `next_delivery_attempt_at` DATETIME(6);
ALTER TABLE `publications` MODIFY COLUMN `updated_at` DATETIME(6) NOT NULL;
ALTER TABLE `publications` MODIFY COLUMN `published_at` DATETIME(6) NOT NULL;
ALTER TABLE `quiz_answers` MODIFY COLUMN `graded_at` DATETIME(6) NOT NULL;
ALTER TABLE `quiz_question_progress` MODIFY COLUMN `completed_at` DATETIME(6) NOT NULL;
ALTER TABLE `mission_reward_claims` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `reward_policies` MODIFY COLUMN `effective_from` DATETIME(6) NOT NULL;
ALTER TABLE `reward_policies` MODIFY COLUMN `effective_until` DATETIME(6);
ALTER TABLE `reward_policies` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `survey_answers` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `survey_questions` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `survey_questions` MODIFY COLUMN `updated_at` DATETIME(6) NOT NULL;
ALTER TABLE `ticket_ledger` MODIFY COLUMN `expires_at` DATETIME(6);
ALTER TABLE `ticket_ledger` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `ticket_recovery_targets` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `ticket_refund_jobs` MODIFY COLUMN `target_expires_at` DATETIME(6);
ALTER TABLE `ticket_refund_jobs` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `ticket_refund_jobs` MODIFY COLUMN `completed_at` DATETIME(6);
ALTER TABLE `ticket_wallets` MODIFY COLUMN `valid_from` DATETIME(6) NOT NULL;
ALTER TABLE `ticket_wallets` MODIFY COLUMN `expires_at` DATETIME(6) NOT NULL;
ALTER TABLE `ticket_wallets` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `ticket_wallets` MODIFY COLUMN `updated_at` DATETIME(6) NOT NULL;
ALTER TABLE `user_game_stats` MODIFY COLUMN `updated_at` DATETIME(6) NOT NULL;
ALTER TABLE `user_game_stats` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `users` MODIFY COLUMN `updated_at` DATETIME(6) NOT NULL;
ALTER TABLE `users` MODIFY COLUMN `created_at` DATETIME(6) NOT NULL;
ALTER TABLE `users` MODIFY COLUMN `suspended_at` DATETIME(6);
