-- 티켓 현재값 + 장별 이력 2개 테이블의 ERDCloud 가져오기용 MySQL DDL.
-- 작성: Codex / 2026-10-07 / 상태: 검토 대기 — 담당자 확인 전.
-- 사용자 제공 tickets_1 / ticket_histories 중심. 기존 Flyway·코드·전체 ERD는 수정하지 않는다.
-- histories.id2는 ticket_id로 정리했다. 이름 변경 시 ERDCloud에서도 같은 이름을 사용한다.
-- 회수 기능 제외 요청에 따라 REVOKE/REVOKED는 제외했다. 공용 스펙에는 아직 미반영이다.
-- 문서 기준: getddo-spec main d42b795. 출석은 BRONZE 고정, 게임·미션은 등급 1장.
-- tickets.grade는 사용자 제안대로 최초 지급 후 불변이다. 반환 등급 정책과의 최종 합의는 확인 대상이다.
-- 외부 5개 테이블은 관계/업무 UNIQUE 표시용 축약본이다. 메인 ERD의 실제 필드·제약을 대체하지 않는다.
-- 메인에 가져갈 핵심은 tickets_1과 ticket_histories다. 외부 참조에는 필요한 UNIQUE를 병합한다.
--
-- [DB 제약]
-- PK: 티켓과 이력의 ID. UNIQUE(ticket_id,ticket_version): 같은 티켓의 같은 버전 중복 금지.
-- GRANT는 버전 1, 다른 처리는 버전 2 이상. 한 티켓의 최초 지급 이력은 최대 1행.
-- UNIQUE(event_entry_id,ticket_id): 같은 응모에 동일 티켓을 중복 사용하지 못한다.
-- 미션/게임은 지급 1장 정책이므로 각 보상 청구 FK UNIQUE로 해당 청구의 지급 이력을 1행으로 제한한다.
-- 출석 청구는 여러 장을 지급할 수 있으므로 attendance_reward_claim_id 단독 UNIQUE를 두지 않는다.
-- GRANT는 보상 청구 정확히 하나, USE는 응모 필수. 다른 처리의 보상/응모 연결은 NULL.
-- FK는 원본 존재만 보장한다. 원본 유형·접수 완료·소유 사용자 일치는 서비스 검증 대상이다.
--
-- [서비스 구현에서 보장할 조건]
-- 지급 전 차단 판정과 보상 청구/지급을 연계한다. 등급 추첨 재시도는 확정된 결과를 반환한다.
-- 출석 청구를 잠그고 기존 GRANT 전체를 먼저 조회한다. 여러 장의 지급을 한 트랜잭션으로 확정한다.
-- 청구의 확정 ticket_count와 지급한 티켓 장수를 확인한다. 새 티켓 ID를 생성하는 재지급은 버전 UNIQUE만으로 막지 못한다.
-- 한 티켓을 잠그고 현재값 변경과 history INSERT를 함께 처리한다. versions는 1부터 1씩 증가하며 누락 금지.
-- 현재 tickets.version의 history.status/expires_at은 tickets 현재값과 일치해야 한다.
-- history는 확정 후 수정·삭제하지 않는다. UPDATE/DELETE 금지는 서비스 및 DB 계정 권한에서 구현한다.
-- USE는 AVAILABLE/RETURNED 및 미만료 상태에서만 허용한다. 이벤트 누적 상한과 취소 경합도 직렬화한다.
-- REFUND는 현재 SPENT를 만든 미반환 USE에만 허용하며 그 USE에 대한 중복 반환은 금지한다.
-- 이 초안에는 반환/정정 원본 연결 컬럼이 없다. 원본 USE/정정 이력 식별 방식은 적용 전 보완 대상이다.
-- CORRECTION을 실제로 제공한다면 원본 연결을 추가한다. 원본 수정이나 임의 지급/회수의 우회 용도가 아니다.
-- EXPIRE는 만료된 AVAILABLE/RETURNED만 처리한다. 이미 사용된 응모를 무효화하지 않는다.
-- 반환 만료는 실제 반환 성공 KST 월 기준. 이벤트 취소와 반환은 함께 확정한다.
-- grade/소유자/최초 지급 시각은 변경하지 않는다. grade NULL의 기존 데이터 전환은 미결정이다.
-- updated_at은 사용자 제공 정의대로 유지했다. 최신 history.created_at으로 대체할 때는 제거 가능하다.
-- 이력 당시 가중치는 별도 필드 없이 서비스에서 계산한다는 사용자 제안이다. 추첨 스냅샷에는 실제 가중치를 보존한다.
-- ERDCloud는 생성 컬럼·UNIQUE·CHECK를 화면에 모두 표시하는지 실제 가져오기 후 확인한다.

CREATE TABLE `users` (
  `id` BINARY(16) NOT NULL COMMENT '사용자 ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자 - 외부 참조. 티켓 소유자와 관리자 처리자.';

CREATE TABLE `event_entries` (
  `id` BINARY(16) NOT NULL COMMENT '개별 이벤트 응모 ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='개별 응모 - 외부 참조. USE의 접수 완료 응모.';

CREATE TABLE `attendance_reward_claims` (
  `id` BINARY(16) NOT NULL COMMENT '출석 보상 청구 ID',
  `user_id` BINARY(16) NOT NULL COMMENT '보상 대상 사용자 ID',
  `reward_type` ENUM('DAILY', 'STREAK') NOT NULL COMMENT '기본 출석 / 연속 출석 단계 보상',
  `reward_date` DATE NOT NULL COMMENT '보상 근거 출석 날짜 KST. 정책 변경으로 중복 보상 허용하지 않음',
  `milestone_days` INT NULL COMMENT 'STREAK의 달성 단계 1~28일. DAILY는 NULL',
  `daily_reward_date` DATE GENERATED ALWAYS AS (CASE WHEN reward_type = 'DAILY' THEN reward_date ELSE NULL END) STORED COMMENT '기본 출석 중복 방지용 생성 컬럼. 연속 출석은 NULL',
  `streak_reward_month` DATE GENERATED ALWAYS AS (CASE WHEN reward_type = 'STREAK' THEN DATE_SUB(reward_date, INTERVAL (DAYOFMONTH(reward_date) - 1) DAY) ELSE NULL END) STORED COMMENT '연속 출석 중복 방지용 KST 월 1일 생성 컬럼. 기본 출석은 NULL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_attendance_daily_business` (`user_id`, `daily_reward_date`),
  UNIQUE KEY `uq_attendance_streak_business` (`user_id`, `streak_reward_month`, `milestone_days`),
  CONSTRAINT `chk_attendance_reward_kind` CHECK (
    (reward_type = 'DAILY' AND milestone_days IS NULL)
    OR (reward_type = 'STREAK' AND milestone_days IS NOT NULL AND milestone_days BETWEEN 1 AND 28)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='출석 보상 청구 - 업무 UNIQUE 설명용 외부 참조. 기본은 사용자별 하루 1회, 연속은 사용자별 월별 단계당 1회.';

CREATE TABLE `mission_reward_claims` (
  `id` BINARY(16) NOT NULL COMMENT '미션 보상 청구 ID',
  `user_id` BINARY(16) NOT NULL COMMENT '보상 대상 사용자 ID',
  `mission_id` BINARY(16) NOT NULL COMMENT '완료한 미션 ID. 미션 테이블 상세는 외부 영역',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mission_reward_business` (`user_id`, `mission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='미션 보상 청구 - 외부 참조. 사용자별 미션당 최초 보상 1회, 재제출/정책 변경으로 재지급하지 않음.';

CREATE TABLE `game_reward_claims` (
  `id` BINARY(16) NOT NULL COMMENT '게임 보상 청구 ID',
  `user_id` BINARY(16) NOT NULL COMMENT '보상 대상 사용자 ID',
  `game_id` BINARY(16) NOT NULL COMMENT '보상 대상 게임 ID. 게임 테이블 상세는 외부 영역',
  `reward_date` DATE NOT NULL COMMENT '게임 보상 기준 날짜 KST',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_game_reward_business` (`user_id`, `game_id`, `reward_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='게임 보상 청구 - 외부 참조. 사용자별 게임별 KST 하루 1회, 플레이/정책이 바뀌어도 제한 유지.';


CREATE TABLE `tickets_1` (
  `id` BINARY(16) NOT NULL COMMENT '응모권 한 장 ID. 반환과 재사용 후에도 유지',
  `user_id` BINARY(16) NOT NULL COMMENT '소유 사용자 ID. 최초 확정 후 변경 금지',
  `grade` ENUM('BRONZE', 'SILVER', 'GOLD') NULL COMMENT '최초 지급 등급. 사용자 제안에 따라 이후 불변. NULL은 기존 데이터 전환 등 미결정 영역이며 무등급 사용 허용을 뜻하지 않음',
  `status` ENUM('AVAILABLE', 'RETURNED', 'SPENT', 'EXPIRED') NOT NULL COMMENT '현재 상태. 최초 사용 가능 / 반환 후 사용 가능 / 사용됨 / 만료 처리됨',
  `expires_at` DATETIME(6) NOT NULL COMMENT '현재 만료 시각 UTC. 반환 성공 시 갱신',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '현재 변경 순번. 최초 지급 1, 변경마다 1 증가. 마지막 이력 버전과 일치',
  `created_at` DATETIME(6) NOT NULL COMMENT '최초 지급 시각 UTC. 변경 금지',
  `updated_at` DATETIME(6) NOT NULL COMMENT '최근 업무 처리 시각 UTC. 현재값 갱신과 함께 반영',
  PRIMARY KEY (`id`),
  KEY `idx_tickets_1_owner_available` (`user_id`, `status`, `expires_at`, `grade`),
  KEY `idx_tickets_1_expiry` (`status`, `expires_at`, `id`),
  CONSTRAINT `chk_tickets_1_version` CHECK (version >= 1),
  CONSTRAINT `chk_tickets_1_updated` CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='응모권 - 한 행은 실제 한 장의 기본 정보와 현재값. 과거 변경은 ticket_histories에 누적';

CREATE TABLE `ticket_histories` (
  `id` BINARY(16) NOT NULL COMMENT '응모권 한 장의 처리 이력 ID. 확정 후 수정·삭제 없이 보존',
  `ticket_id` BINARY(16) NOT NULL COMMENT '처리 대상 응모권 ID. 사용자 제공 id2를 의미에 맞게 변경',
  `operation_type` ENUM('GRANT', 'USE', 'REFUND', 'EXPIRE', 'CORRECTION') NOT NULL COMMENT '지급 / 사용 / 반환 / 만료 / 정정. CORRECTION 실제 제공 시 원본 연결 보완 필요',
  `ticket_version` BIGINT NOT NULL COMMENT '처리 후 티켓 버전. 최초 GRANT 1, 이후 1씩 증가하는 이력 순서',
  `status` ENUM('AVAILABLE', 'RETURNED', 'SPENT', 'EXPIRED') NOT NULL COMMENT '처리 결과 상태. 해당 버전 처리 완료 당시 값이며 이후 변경하지 않음',
  `expires_at` DATETIME(6) NOT NULL COMMENT '처리 결과 만료 시각 UTC. 해당 버전 완료 당시 값이며 이후 변경하지 않음',
  `attendance_reward_claim_id` BINARY(16) NULL COMMENT '출석 GRANT 원본 청구 ID. 같은 청구로 여러 티켓 지급 가능, 다른 처리 NULL',
  `mission_reward_claim_id` BINARY(16) NULL COMMENT '미션 GRANT 원본 청구 ID. 1장 지급 정책으로 청구당 이력 1행, 다른 처리 NULL',
  `game_reward_claim_id` BINARY(16) NULL COMMENT '게임 GRANT 원본 청구 ID. 1장 지급 정책으로 청구당 이력 1행, 다른 처리 NULL',
  `event_entry_id` BINARY(16) NULL COMMENT 'USE 개별 응모 ID. 한 응모에 여러 티켓 사용 가능, 다른 처리 NULL',
  `reason` TEXT NOT NULL COMMENT '처리 사유. 원문 예외·개인정보·비밀값 저장 금지',
  `created_at` DATETIME(6) NOT NULL COMMENT '실제 처리 시각 UTC. 반환 만료 계산의 기준',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ticket_history_version` (`ticket_id`, `ticket_version`),
  UNIQUE KEY `uq_ticket_history_entry_ticket` (`event_entry_id`, `ticket_id`),
  UNIQUE KEY `uq_ticket_history_mission_grant` (`mission_reward_claim_id`),
  UNIQUE KEY `uq_ticket_history_game_grant` (`game_reward_claim_id`),
  KEY `idx_ticket_history_attendance` (`attendance_reward_claim_id`, `ticket_id`),
  KEY `idx_ticket_history_time` (`created_at`, `id`),
  CONSTRAINT `chk_ticket_history_version` CHECK (
    (operation_type = 'GRANT' AND ticket_version = 1)
    OR (operation_type <> 'GRANT' AND ticket_version > 1)
  ),
  CONSTRAINT `chk_ticket_history_source` CHECK (
    (operation_type = 'GRANT' AND
      ((attendance_reward_claim_id IS NOT NULL) + (mission_reward_claim_id IS NOT NULL) + (game_reward_claim_id IS NOT NULL)) = 1)
    OR (operation_type <> 'GRANT' AND attendance_reward_claim_id IS NULL AND mission_reward_claim_id IS NULL AND game_reward_claim_id IS NULL)
  ),
  CONSTRAINT `chk_ticket_history_entry` CHECK (
    (operation_type = 'USE' AND event_entry_id IS NOT NULL)
    OR (operation_type <> 'USE' AND event_entry_id IS NULL)
  ),
  CONSTRAINT `chk_ticket_history_result` CHECK (
    (operation_type = 'GRANT' AND status = 'AVAILABLE')
    OR (operation_type = 'USE' AND status = 'SPENT')
    OR (operation_type = 'REFUND' AND status = 'RETURNED')
    OR (operation_type = 'EXPIRE' AND status = 'EXPIRED')
    OR operation_type = 'CORRECTION'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='응모권 처리 이력 - 티켓 한 장당 변경 버전별 한 행. 현재값과 별도로 처리 완료 당시 상태·만료를 보존';

-- 모든 테이블 생성 후 FK 추가. CASCADE 삭제를 사용하지 않는다.
ALTER TABLE `tickets_1` ADD CONSTRAINT `fk_tickets_1_owner`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);
ALTER TABLE `ticket_histories` ADD CONSTRAINT `fk_ticket_history_ticket`
  FOREIGN KEY (`ticket_id`) REFERENCES `tickets_1` (`id`);
ALTER TABLE `ticket_histories` ADD CONSTRAINT `fk_ticket_history_attendance`
  FOREIGN KEY (`attendance_reward_claim_id`) REFERENCES `attendance_reward_claims` (`id`);
ALTER TABLE `ticket_histories` ADD CONSTRAINT `fk_ticket_history_mission`
  FOREIGN KEY (`mission_reward_claim_id`) REFERENCES `mission_reward_claims` (`id`);
ALTER TABLE `ticket_histories` ADD CONSTRAINT `fk_ticket_history_game`
  FOREIGN KEY (`game_reward_claim_id`) REFERENCES `game_reward_claims` (`id`);
ALTER TABLE `ticket_histories` ADD CONSTRAINT `fk_ticket_history_entry`
  FOREIGN KEY (`event_entry_id`) REFERENCES `event_entries` (`id`);
ALTER TABLE `attendance_reward_claims` ADD CONSTRAINT `fk_history_attendance_owner`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);
ALTER TABLE `mission_reward_claims` ADD CONSTRAINT `fk_history_mission_owner`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);
ALTER TABLE `game_reward_claims` ADD CONSTRAINT `fk_history_game_owner`
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

