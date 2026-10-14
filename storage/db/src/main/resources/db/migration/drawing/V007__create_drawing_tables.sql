-- Source: docs/03-database/schema.dbml
-- GD-93: Initial schema; version numbers retained by user request.
-- Initial tables and foreign keys owned by drawing.

CREATE TABLE `draw_runs` (
  `id` binary(16) NOT NULL COMMENT '추첨 실행 ID',
  `event_id` binary(16) NOT NULL COMMENT '추첨 대상 이벤트 ID',
  `run_number` int NOT NULL COMMENT '이벤트별 추첨 차수. 이벤트별 최초 0, 새 재추첨마다 증가. 실패 재시도는 유지.',
  `draw_type` ENUM ('INITIAL', 'REPLACEMENT') NOT NULL COMMENT '추첨 종류. INITIAL=최초 추첨, REPLACEMENT=당첨 취소에 따른 재추첨',
  `status` ENUM ('PREPARING', 'READY', 'RUNNING', 'CONFIRMED', 'FAILED') NOT NULL COMMENT '실행 상태. PREPARING=준비 중, READY=후보·조건 확정, RUNNING=실행 중, CONFIRMED=결과 확정, FAILED=실패',
  `algorithm_version` varchar(100) COMMENT 'READY 이후 필수·불변.',
  `rules_snapshot` json COMMENT 'READY 이후 필수·불변. 경품·자리·가중치 기준·유지 당첨 결과 ID·대상 자리.',
  `snapshot_fixed_at` datetime(6) COMMENT 'UTC. 확정 전 NULL',
  `started_at` datetime(6) COMMENT 'UTC. 시작 전 NULL',
  `confirmed_at` datetime(6) COMMENT 'UTC. 확정 전 NULL',
  `created_at` datetime(6) NOT NULL COMMENT '추첨 실행 및 작업 등록 시각 UTC',
  CONSTRAINT `chk_draw_number` CHECK ((draw_type = 'INITIAL' AND run_number = 0) OR (draw_type = 'REPLACEMENT' AND run_number >= 1)),
  CONSTRAINT `chk_draw_fixed_input` CHECK (status NOT IN ('READY','RUNNING','CONFIRMED') OR (algorithm_version IS NOT NULL AND rules_snapshot IS NOT NULL AND snapshot_fixed_at IS NOT NULL)),
  CONSTRAINT `chk_draw_confirmed_time` CHECK (status <> 'CONFIRMED' OR confirmed_at IS NOT NULL),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_draw_runs_event` (`id`, `event_id`),
  UNIQUE KEY `uq_draw_runs_1` (`event_id`, `run_number`),
  CONSTRAINT `fk_draw_runs_1` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_candidates` (
  `id` binary(16) NOT NULL COMMENT '최초 추첨 후보 ID. 재추첨에서도 같은 후보 정보를 재사용',
  `participant_id` binary(16) NOT NULL COMMENT '원본 이벤트 응모자 관계 ID',
  `draw_run_id` binary(16) NOT NULL COMMENT '최초 후보 정보를 고정한 추첨 실행 ID. 재추첨 참여 실행은 draw_run_candidates에 연결',
  `ticket_count` bigint NOT NULL COMMENT '해당 이벤트에서 접수 완료된 응모의 실제 누적 차감 수량. 재추첨은 최초 추첨 당시 값',
  `weight` bigint NOT NULL COMMENT '해당 응모자의 추첨 가중치. 현재 선형 가중치 정책의 정수 값. 추가 배율 없음.',
  `entry_snapshot` json NOT NULL COMMENT '확정 응모 근거 스냅샷. 응모 ID·실제 장수·접수 시각·등급별 사용량·적용 가중치. 재추첨 시 최초 값 유지',
  `eligibility_snapshot` json NOT NULL COMMENT '후보 확정 당시 응모 자격과 제외 판단 스냅샷. 명단 확정 당시 자격·제외 판단 및 재추첨이면 최초 후보 ID.',
  `created_at` datetime(6) NOT NULL COMMENT '후보 스냅샷 생성 시각 UTC',
  CONSTRAINT `chk_candidate_counts` CHECK (ticket_count >= 0 AND weight >= 1),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_draw_candidates_1` (`draw_run_id`, `participant_id`),
  CONSTRAINT `fk_draw_candidates_1` FOREIGN KEY (`draw_run_id`) REFERENCES `draw_runs` (`id`),
  CONSTRAINT `fk_draw_candidates_2` FOREIGN KEY (`participant_id`) REFERENCES `event_participants` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_run_candidates` (
  `draw_run_id` binary(16) NOT NULL COMMENT '후보가 참여한 추첨 실행 ID. 최초 및 재추첨 실행',
  `candidate_id` binary(16) NOT NULL COMMENT '재사용하는 최초 추첨 후보 ID',
  PRIMARY KEY (`draw_run_id`, `candidate_id`),
  CONSTRAINT `fk_draw_run_candidates_1` FOREIGN KEY (`draw_run_id`) REFERENCES `draw_runs` (`id`),
  CONSTRAINT `fk_draw_run_candidates_2` FOREIGN KEY (`candidate_id`) REFERENCES `draw_candidates` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_results` (
  `id` binary(16) NOT NULL COMMENT 'SELECTED 결과의 ID가 API의 winId',
  `draw_run_id` binary(16) NOT NULL COMMENT '결과를 생성한 추첨 실행 ID',
  `event_prize_id` binary(16) NOT NULL COMMENT '선정 또는 미충원된 이벤트 경품 ID',
  `slot_number` int NOT NULL COMMENT '경품별 당첨 자리 번호. 1부터 해당 경품 당첨 인원까지',
  `result_type` ENUM ('SELECTED', 'UNFILLED') NOT NULL COMMENT '결과 종류. SELECTED=당첨자 선정, UNFILLED=후보 부족으로 미충원',
  `candidate_id` binary(16) COMMENT '선정된 추첨 대상자 ID. UNFILLED는 NULL',
  `selection_order` int COMMENT '실행 내 실제 당첨 선정 순서. 상위 등수부터 선정하며 UNFILLED는 NULL',
  `created_at` datetime(6) NOT NULL COMMENT '선정·미충원 결과 기록 시각 UTC',
  CONSTRAINT `chk_draw_result_shape` CHECK ((result_type = 'UNFILLED' AND candidate_id IS NULL AND selection_order IS NULL) OR (result_type = 'SELECTED' AND candidate_id IS NOT NULL AND selection_order IS NOT NULL)),
  CONSTRAINT `chk_draw_slot` CHECK (slot_number >= 1),
  CONSTRAINT `chk_draw_selection_order` CHECK (selection_order IS NULL OR selection_order >= 1),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_draw_results_run` (`id`, `draw_run_id`),
  UNIQUE KEY `uq_draw_results_1` (`draw_run_id`, `event_prize_id`, `slot_number`),
  UNIQUE KEY `uq_draw_results_2` (`draw_run_id`, `candidate_id`),
  UNIQUE KEY `uq_draw_results_3` (`draw_run_id`, `selection_order`),
  CONSTRAINT `fk_draw_results_1` FOREIGN KEY (`draw_run_id`) REFERENCES `draw_runs` (`id`),
  CONSTRAINT `fk_draw_results_2` FOREIGN KEY (`event_prize_id`) REFERENCES `event_prizes` (`id`),
  CONSTRAINT `fk_draw_results_3` FOREIGN KEY (`draw_run_id`, `candidate_id`) REFERENCES `draw_run_candidates` (`draw_run_id`, `candidate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `award_cancellations` (
  `id` binary(16) NOT NULL COMMENT '당첨 취소 ID',
  `draw_result_id` binary(16) NOT NULL COMMENT '취소한 SELECTED 추첨 결과 ID',
  `draw_run_id` binary(16) COMMENT '취소 저장 시 NULL, 재추첨 등록 시 설정 후 변경·해제 금지. 여러 취소가 같은 실행 참조 가능',
  `canceled_by` binary(16) NOT NULL COMMENT '당첨 취소를 확정한 관리자 ID',
  `reason` text NOT NULL COMMENT '당첨 취소 사유',
  `canceled_at` datetime(6) NOT NULL COMMENT '당첨 취소 확정 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_award_cancellations_1` (`draw_result_id`),
  CONSTRAINT `fk_award_cancellations_1` FOREIGN KEY (`draw_result_id`) REFERENCES `draw_results` (`id`),
  CONSTRAINT `fk_award_cancellations_2` FOREIGN KEY (`canceled_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_award_cancellations_3` FOREIGN KEY (`draw_run_id`) REFERENCES `draw_runs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_publications` (
  `id` binary(16) NOT NULL COMMENT '공개 명단 버전 ID',
  `event_id` binary(16) NOT NULL COMMENT '명단을 공개한 이벤트 ID',
  `source_draw_id` binary(16) NOT NULL COMMENT '이번 공개·갱신의 원인이 된 추첨 실행 ID. 명단에는 이전 실행 결과도 포함 가능',
  `revision` int NOT NULL COMMENT '이벤트별 공개 명단 버전 번호. 최초 1, 갱신마다 증가',
  `publication_type` ENUM ('INITIAL', 'UPDATE') NOT NULL COMMENT '공개 종류. INITIAL=최초 자동 공개, UPDATE=관리자 확인 후 명단 갱신',
  `reason` text COMMENT '공개 명단 변경 사유. UPDATE는 필수. 정상 최초 자동 공개는 NULL 가능',
  `confirmed_by` binary(16) COMMENT '공개 전 재추첨 결과 확인 관리자 ID. UPDATE는 필수. 정상 최초 추첨만 NULL 가능. 실행별 확인은 draw_result_confirmations에서 보존',
  `confirmed_at` datetime(6) COMMENT '관리자 공개 확인 시각 UTC. UPDATE는 필수. 정상 최초 추첨만 NULL 가능. 실제 공개 시각과 구분',
  `published_at` datetime(6) NOT NULL COMMENT '해당 명단 버전을 실제 공개에 반영한 시각 UTC',
  CONSTRAINT `chk_publication_type_revision` CHECK ((publication_type = 'INITIAL' AND revision = 1) OR (publication_type = 'UPDATE' AND revision >= 2)),
  CONSTRAINT `chk_publication_revision` CHECK (revision >= 1),
  CONSTRAINT `chk_publication_confirmation_pair` CHECK ((confirmed_by IS NULL AND confirmed_at IS NULL) OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)),
  CONSTRAINT `chk_publication_update` CHECK (publication_type <> 'UPDATE' OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL AND reason IS NOT NULL AND TRIM(reason) <> '')),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_draw_publications_event` (`id`, `event_id`),
  UNIQUE KEY `uq_draw_publications_1` (`event_id`, `revision`),
  CONSTRAINT `fk_draw_publications_1` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `fk_draw_publications_2` FOREIGN KEY (`source_draw_id`, `event_id`) REFERENCES `draw_runs` (`id`, `event_id`),
  CONSTRAINT `fk_draw_publications_3` FOREIGN KEY (`confirmed_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_failures` (
  `id` binary(16) NOT NULL COMMENT '같은 실패 기록의 저장 재시도에는 같은 ID 사용',
  `draw_run_id` binary(16) NOT NULL COMMENT '동일 실행의 여러 실패를 연결',
  `failure_code` varchar(50) NOT NULL COMMENT '실패 분류 코드. 원문 예외 메시지나 개인정보를 저장하지 않음',
  `failed_at` datetime(6) NOT NULL COMMENT 'UTC. 기록 저장 시각과 구분',
  `trace_id` varchar(100) COMMENT '선택적인 상세 진단 기록 연결 ID. 발급·검색 시스템 연동 전에는 NULL 가능',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_draw_failures_1` FOREIGN KEY (`draw_run_id`) REFERENCES `draw_runs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_checks` (
  `id` binary(16) NOT NULL COMMENT '정합성 검증 기록 ID',
  `draw_run_id` binary(16) NOT NULL COMMENT '검증한 확정 추첨 실행 ID',
  `checked_by` binary(16) NOT NULL COMMENT '검증을 요청한 관리자 ID',
  `passed` boolean NOT NULL COMMENT '전체 검증 통과 여부. true=통과, false=불통과',
  `checks_snapshot` json NOT NULL COMMENT '항목별 정합성 검증 결과 스냅샷. 검증 버전·검증 범위·항목 코드·통과 여부·메시지.',
  `checked_at` datetime(6) NOT NULL COMMENT '검증 완료·기록 시각 UTC',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_draw_checks_1` FOREIGN KEY (`draw_run_id`) REFERENCES `draw_runs` (`id`),
  CONSTRAINT `fk_draw_checks_2` FOREIGN KEY (`checked_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_result_confirmations` (
  `id` binary(16) NOT NULL COMMENT '재추첨 결과의 관리자 확인 이력 ID. 확정 후 수정·삭제 금지',
  `draw_run_id` binary(16) NOT NULL COMMENT '관리자가 확인한 REPLACEMENT 실행 ID. 최초 공개 전에도 기록',
  `confirmed_by` binary(16) NOT NULL COMMENT '실제 확인 관리자 ID',
  `confirmed_at` datetime(6) NOT NULL COMMENT '관리자 확인 시각 UTC. 실행 결과 확정 시각 및 실제 공개 시각과 구분',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_draw_result_confirmations_run` (`draw_run_id`),
  UNIQUE KEY `uq_draw_result_confirmations_id_run` (`id`, `draw_run_id`),
  CONSTRAINT `fk_draw_result_confirmations_1` FOREIGN KEY (`draw_run_id`) REFERENCES `draw_runs` (`id`),
  CONSTRAINT `fk_draw_result_confirmations_2` FOREIGN KEY (`confirmed_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `draw_publication_results` (
  `publication_id` binary(16) NOT NULL COMMENT '실제 공개 명단 버전 ID',
  `draw_result_id` binary(16) NOT NULL COMMENT '이 버전에 실제 포함한 SELECTED 결과 ID. 이전 실행 유지 결과와 새 재추첨 결과 모두 포함',
  `event_id` binary(16) NOT NULL COMMENT '공개 버전과 결과 실행의 이벤트 일치를 복합 FK로 보장',
  `draw_run_id` binary(16) NOT NULL COMMENT '포함 결과의 원본 실행 ID. 공개 계기 실행과 다를 수 있음',
  `confirmation_id` binary(16) COMMENT 'REPLACEMENT 결과의 관리자 확인 이력. INITIAL 결과는 NULL. 유지 재추첨 결과는 기존 확인 재사용',
  PRIMARY KEY (`publication_id`, `draw_result_id`),
  CONSTRAINT `fk_draw_publication_results_1` FOREIGN KEY (`publication_id`, `event_id`) REFERENCES `draw_publications` (`id`, `event_id`),
  CONSTRAINT `fk_draw_publication_results_2` FOREIGN KEY (`draw_result_id`, `draw_run_id`) REFERENCES `draw_results` (`id`, `draw_run_id`),
  CONSTRAINT `fk_draw_publication_results_3` FOREIGN KEY (`draw_run_id`, `event_id`) REFERENCES `draw_runs` (`id`, `event_id`),
  CONSTRAINT `fk_draw_publication_results_4` FOREIGN KEY (`confirmation_id`, `draw_run_id`) REFERENCES `draw_result_confirmations` (`id`, `draw_run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
