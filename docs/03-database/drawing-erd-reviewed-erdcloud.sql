-- 추첨 ERD 리뷰 반영안 — ERDCloud 가져오기용 MySQL DDL.
-- 작성일: 2026-10-04 / 상태: 검토 대기, 담당자 확인 전.
-- 기준: erd-review-agent.md에 따른 검토 결과와 사용자 테이블별 검토.
-- 수정일: 2026-10-05 / 실패·취소 관계 정리 및 최초 후보 재사용을 위한 실행별 후보 연결 추가.
-- 추첨 영역 8개와 외부 참조 4개 테이블. 모든 컬럼과 테이블에 한글 COMMENT 포함.
-- 실제 DB 적용용 마이그레이션이 아니며, 기존 파일·API·확정 정책을 변경하지 않는다.
-- 외부 테이블은 관계 표시를 위한 축약 참조이며 실제 운영 구조로 대체할 수 없다.
-- 원격 최신 조회는 두 저장소 모두 GitHub DNS 오류로 실패했다. 로컬 문서 기준 제안이다.
--
-- [반영 1] draw_runs.requested_by/reason 제거: award_cancellations.draw_run_id로 취소별 처리자·사유 조회.
-- 취소 요청에 따른 실행만 제공하므로 연결된 취소 사유 목록을 재추첨 업무 사유로 조회.
-- 최초 자동 실행의 처리자는 NULL로 산출. 독립적인 재추첨 사유 입력이 필요하면 재검토.
-- [반영 2] draw_publication_items 제거: 공개 이력과 불변 결과의 Query로 명단을 구성.
-- 한 실행의 모든 결과를 한 번에 공개한다. 일부 결과만 공개하는 기능은 이 제안에 포함하지 않음.
-- 공개 버전까지 반영된 실행의 결과 중 경품·자리별 가장 최근 공개 결과를 선택한다.
-- 전체 실행 중 최신 결과나 가장 큰 run_number를 선택하면 미확인 결과가 노출되므로 금지.
-- 과거 revision의 명단과 이전 revision의 명단을 비교해 변경 전후를 조회한다.
-- 기존 후보·결과·취소·검증·공개 확인자·사유·시각·공개 버전은 보존한다.
-- 감사 역할을 겸하는 테이블을 포함하며 업무 데이터만의 4개 테이블 버전과 다르다.
-- [반영 4] runs의 failure_count/last_failure_code/last_failed_at/last_failure_trace_id 제거.
-- draw_failures 한 행은 동일 실행의 실패 한 번. 실패 횟수는 COUNT(*), 최근 실패는
-- failed_at DESC, id DESC로 조회한다. 같은 시각이면 ID 순으로 결정적으로 조회한다.
-- 실패 이력 재저장은 같은 id를 사용해 중복을 방지하며 실패 재시도는 새 runs 행을 만들지 않음.
-- 실패 이력은 복구·관리자 조회에 사용할 DB 운영 기록. 원문 예외·스택 트레이스는 저장하지 않음.
-- 자동 재시도 횟수·간격 및 삭제·보관 정책은 이 변경으로 새로 확정하지 않는다.
-- [반영 5] draw_runs.cancellation_id 및 별도 연결 테이블 제거. 취소에 nullable draw_run_id 추가.
-- 최초 실행에는 취소 연결이 없고, 재추첨 실행 하나에는 취소 한 건 이상을 연결한다.
-- 취소별 사유·처리자·시각과 원래 결과는 각각 보존. 같은 이벤트의 취소만 묶는다.
-- 취소 하나의 draw_run_id는 실행 하나만 참조. NULL에서 최초 연결만 허용하고 재연결·해제 금지.
-- 취소를 먼저 저장할 때 draw_run_id=NULL. 재추첨 등록 시 해당 취소들의 실행 ID를 함께 설정한다.
-- NULL은 실행 미등록 상태를 뜻하며, 즉시 재추첨 정책을 무기한 대기 정책으로 변경하지 않는다.
-- 묶음은 실행 등록 시 확정하며 이후 취소를 추가·교체하지 않는다. 후속 취소는 새 실행.
-- 취소 결과의 경품·자리 합집합만 재추첨 대상으로 삼아 rules_snapshot에 고정한다.
-- 최초·이전 실행은 연결된 취소 -> 원래 결과 -> 원래 실행으로 추적한다.
-- 서로 다른 원래 실행의 결과라도 같은 이벤트의 현재 유효 당첨만 취소 대상으로 인정한다.
--
-- [반영 3: 서비스 처리 계약 제안, DDL만으로 보장되지 않음]
-- A. 취소 사실은 실행 등록 전에 저장할 수 있다. 재추첨 등록 시 이벤트·취소 행을 잠근다.
-- PREPARING 실행 하나의 INSERT와 선택한 미연결 취소들의 draw_run_id UPDATE를 함께 커밋한다.
-- 동일 이벤트·REPLACEMENT·준비 단계 및 각 취소가 미연결인지 검증한다. FK 변경·해제는 금지.
-- 실행 등록 실패 시 전체 연결 변경을 롤백하며 NULL 취소는 미등록 상태로 조회한다.
-- 같은 취소 묶음 재요청은 기존 실행을 조회하며 새 실행으로 중복 처리하지 않는다.
-- 기존 묶음과 일부만 겹치는 요청의 오류·응답 계약은 API 검토 대상. 부분 성공으로 저장하지 않음.
-- B. 동일 이벤트의 후보 확정부터 결과 확정까지 실행을 직렬화한다.
-- 구현 후보: 이벤트 행 잠금을 획득한 단일 트랜잭션에서 후보·조건 고정, 선정, 결과 저장,
-- CONFIRMED 전환을 함께 커밋한다. 후속 실행은 앞 실행 확정 전 후보를 READY로 고정하지 않음.
-- 기존 READY/RUNNING 또는 고정 스냅샷을 가진 FAILED가 있으면 그 실행의 재개를 먼저 처리한다.
-- C. 장애로 트랜잭션이 롤백되면 부분 후보·결과는 남기지 않는다. 실패 기록은 별도 트랜잭션.
-- 실행 잠금·현재 상태를 재검증한 뒤 FAILED 전환과 draw_failures INSERT를 함께 커밋한다.
-- 다른 작업자가 이미 확정한 실행을 뒤늦은 실패 기록으로 FAILED로 덮어쓰지 않는다.
-- 이미 고정된 스냅샷이 있는 실행은 같은 값을 사용하며, 확정 실행은 다시 선정하지 않는다.
-- D. 후보 고정 시 공개 여부와 무관한 내부 유효 당첨자·취소자·확정 제외자를 제거한다.
-- E. 공개는 동일 이벤트 잠금 안에서 최신 revision을 읽고 헤더 하나를 INSERT한다.
-- source_draw_id의 UNIQUE로 재요청은 기존 버전 반환. 확인·실제 반영은 같은 트랜잭션.
-- 최신 내부 당첨·현재 제외·취소를 재검증하고 이미 취소된 새 결과는 공개하지 않는다.
-- F. 초기 후보·조건 확정과 제외 판단·이벤트 취소의 잠금 순서는 D/C/E와 적용 전 공동 확인.
-- 다중 서버·강제 종료·재시도 시 검증 필요. SQL 작성만으로 동시성 해결 완료를 뜻하지 않음.
-- 알림 연동과 중복 방지는 기존 C 영역 계약을 별도로 따른다.
--
-- [미확정: 사용자 선택 없이 구현하지 않음]
-- 취소부터 재추첨 결과 공개까지 기존 당첨자의 유지/취소 표시/숨김 정책.
-- 최초 공개 전 취소·재추첨의 관리자 확인 및 최초 자동 공개 명단 구성 방식.
-- 진행 중 빈자리를 후보 부족 UNFILLED 결과로 임의 기록하지 않는다.
-- 아래 Query는 실제 공개된 선정 이력을 구성한다. 현재 취소자의 화면 표시를 결정하지 않는다.
-- 마스킹·개인정보 파기 방식과 로그 추적 ID 발급·검색 연동은 기존 후속 확인 사항.
-- 메모의 CHECK 및 서비스 조건은 SQL CHECK로 추가하지 않았다. 실제 적용 전 검증 필요.

-- [반영 6] 최초 후보 상세 정보는 draw_candidates에 한 번 저장한다.
-- draw_run_candidates의 복합 PK(draw_run_id, candidate_id)로 최초·재추첨 명단을 고정한다.
-- 결과의 복합 FK는 draw_run_candidates를 참조해 실행에 등록된 후보만 선정하도록 한다.
-- 후보 원본 실행 INITIAL·동일 이벤트·최초 후보 중복 생성 방지는 서비스에서 검증한다.
-- READY 이후 연결 추가·수정·삭제 금지 및 실행 재시도 명단 재사용은 서비스에서 보장한다.
-- 재추첨 후보는 모든 과거 SELECTED 당첨자(취소 포함)와 이후 제외 확정자를 제거한다.

-- 이벤트 (외부 참조)
CREATE TABLE `events` (
  `id` BINARY(16) NOT NULL COMMENT '이벤트 ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='이벤트 (외부 참조) - E/C 담당 이벤트. 기간·상태·응모 조건은 기존 영역에서 조회.';

-- 사용자 (외부 참조)
CREATE TABLE `users` (
  `id` BINARY(16) NOT NULL COMMENT '사용자 ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자 (외부 참조) - A 담당 사용자. 관리자 처리자와 응모자 식별에 사용.';

-- 이벤트 경품 (외부 참조)
CREATE TABLE `event_prizes` (
  `id` BINARY(16) NOT NULL COMMENT '이벤트 경품 ID',
  `event_id` BINARY(16) NOT NULL COMMENT '경품이 속한 이벤트 ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='이벤트 경품 (외부 참조) - E 담당 경품 구성. 등수·당첨 인원·설명은 실행 조건 스냅샷으로 보존.';

-- 이벤트 응모자 (외부 참조)
CREATE TABLE `event_participants` (
  `id` BINARY(16) NOT NULL COMMENT '이벤트 응모자 관계 ID',
  `event_id` BINARY(16) NOT NULL COMMENT '응모한 이벤트 ID',
  `user_id` BINARY(16) NOT NULL COMMENT '응모한 사용자 ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_event_participants_1` (`event_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='이벤트 응모자 (외부 참조) - D 담당 이벤트 응모자 관계. 접수 완료 기록과 확정 제외 정보를 함께 대조.';

-- 추첨 실행
CREATE TABLE `draw_runs` (
  `id` BINARY(16) NOT NULL COMMENT '추첨 실행 ID',
  `event_id` BINARY(16) NOT NULL COMMENT '추첨 대상 이벤트 ID',
  `run_number` INT NOT NULL COMMENT '이벤트별 추첨 차수. 이벤트별 최초 0, 새 재추첨마다 증가. 실패 재시도는 유지.',
  `draw_type` ENUM('INITIAL', 'REPLACEMENT') NOT NULL COMMENT '추첨 종류. INITIAL=최초 추첨, REPLACEMENT=당첨 취소에 따른 재추첨',
  `status` ENUM('PREPARING', 'READY', 'RUNNING', 'CONFIRMED', 'FAILED') NOT NULL COMMENT '실행 상태. PREPARING=준비 중, READY=후보·조건 확정, RUNNING=실행 중, CONFIRMED=결과 확정, FAILED=실패',
  `algorithm_version` VARCHAR(100) NULL COMMENT '사용한 추첨 알고리즘 버전. READY 이후 필수·불변.',
  `rules_snapshot` JSON NULL COMMENT '해당 실행에서 사용한 추첨 조건 스냅샷. READY 이후 필수·불변. 경품·자리·가중치 기준·유지 당첨 결과 ID·대상 자리.',
  `snapshot_fixed_at` DATETIME(6) NULL COMMENT '후보·조건 스냅샷 확정 시각 UTC. 확정 전 NULL',
  `started_at` DATETIME(6) NULL COMMENT '추첨 선정 작업 시작 시각 UTC. 시작 전 NULL',
  `confirmed_at` DATETIME(6) NULL COMMENT '추첨 결과 확정 시각 UTC. 확정 전 NULL',
  `created_at` DATETIME(6) NOT NULL COMMENT '추첨 실행 및 작업 등록 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_draw_runs_1` (`event_id`, `run_number`),
  KEY `idx_draw_runs_2` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='추첨 실행 - 한 행은 최초 추첨 또는 한 건 이상의 당첨 취소에 따른 새 재추첨 하나. PREPARING은 작업 등록 상태. 최초는 차수 0·취소 연결 없음, 재추첨은 차수 양수·취소 연결 필수. 처리자·사유 목록은 연결 취소에서 조회. 관계 개수·이벤트 일치·직렬화는 서비스 검증.';

-- 추첨 실행 실패 이력
CREATE TABLE `draw_failures` (
  `id` BINARY(16) NOT NULL COMMENT '실패 기록 ID. 같은 실패 기록의 저장 재시도에는 같은 ID 사용',
  `draw_run_id` BINARY(16) NOT NULL COMMENT '실패가 발생한 추첨 실행 ID. 동일 실행의 여러 실패를 연결',
  `failure_code` VARCHAR(50) NOT NULL COMMENT '실패 분류 코드. 원문 예외 메시지나 개인정보를 저장하지 않음',
  `failed_at` DATETIME(6) NOT NULL COMMENT '해당 실패가 발생한 시각 UTC. 기록 저장 시각과 구분',
  `trace_id` VARCHAR(100) NULL COMMENT '선택적인 상세 진단 기록 연결 ID. 발급·검색 시스템 연동 전에는 NULL 가능',
  PRIMARY KEY (`id`),
  KEY `idx_draw_failures_run_time` (`draw_run_id`, `failed_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='추첨 실행 실패 이력 - 한 행은 추첨 실행의 실패 한 번. 재시도 성공 후에도 과거 실패를 보존. 실패 횟수와 최근 실패는 이 테이블에서 조회. 운영 기록이며 상세 진단 로그와 구분.';

-- 추첨 대상자
CREATE TABLE `draw_candidates` (
  `id` BINARY(16) NOT NULL COMMENT '최초 추첨 후보 ID. 재추첨 실행에서도 재사용',
  `draw_run_id` BINARY(16) NOT NULL COMMENT '후보 정보를 최초로 고정한 INITIAL 실행 ID',
  `participant_id` BINARY(16) NOT NULL COMMENT '원본 이벤트 응모자 관계 ID',
  `ticket_count` BIGINT NOT NULL COMMENT '해당 이벤트에서 접수 완료된 응모의 실제 누적 차감 수량. 재추첨은 최초 추첨 당시 값',
  `weight` BIGINT NOT NULL COMMENT '해당 응모자의 추첨 가중치. 현재 선형 가중치 정책의 정수 값. 추가 배율 없음.',
  `entry_snapshot` JSON NOT NULL COMMENT '가중치 산출 근거가 된 응모 기록 스냅샷. 근거 접수 건 ID·실제 차감량·접수 시각.',
  `eligibility_snapshot` JSON NOT NULL COMMENT '최초 후보 확정 당시 응모 자격과 제외 판단 스냅샷. 재추첨 시 복사하지 않음.',
  `created_at` DATETIME(6) NOT NULL COMMENT '후보 스냅샷 생성 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_draw_candidates_1` (`draw_run_id`, `participant_id`),
  UNIQUE KEY `idx_draw_candidates_2` (`id`, `draw_run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='추첨 대상자 - 최초 추첨 후보 한 명의 불변 스냅샷. 실행별 참여 명단은 draw_run_candidates로 보존. CHECK: ticket_count>=0, weight>0. 재추첨은 최초 후보의 수량·가중치를 사용하고 현재 잔액으로 재계산하지 않음.';

-- 추첨 실행별 후보
CREATE TABLE `draw_run_candidates` (
  `draw_run_id` BINARY(16) NOT NULL COMMENT '후보가 참여한 추첨 실행 ID. 최초 및 재추첨 실행',
  `candidate_id` BINARY(16) NOT NULL COMMENT '재사용하는 최초 추첨 후보 ID',
  PRIMARY KEY (`draw_run_id`, `candidate_id`),
  KEY `idx_draw_run_candidates_candidate` (`candidate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='추첨 실행별 후보 - 실행 하나에 참여한 최초 후보 하나의 연결. 상세 스냅샷을 복사하지 않음. 확정 후 연결 추가·수정·삭제 금지는 서비스에서 보장.';

-- 추첨 결과
CREATE TABLE `draw_results` (
  `id` BINARY(16) NOT NULL COMMENT '추첨 결과 ID. SELECTED 결과의 ID가 API의 winId',
  `draw_run_id` BINARY(16) NOT NULL COMMENT '결과를 생성한 추첨 실행 ID',
  `event_prize_id` BINARY(16) NOT NULL COMMENT '선정 또는 미충원된 이벤트 경품 ID',
  `slot_number` INT NOT NULL COMMENT '경품별 당첨 자리 번호. 1부터 해당 경품 당첨 인원까지',
  `result_type` ENUM('SELECTED', 'UNFILLED') NOT NULL COMMENT '결과 종류. SELECTED=당첨자 선정, UNFILLED=후보 부족으로 미충원',
  `candidate_id` BINARY(16) NULL COMMENT '선정된 추첨 대상자 ID. UNFILLED는 NULL',
  `selection_order` INT NULL COMMENT '실행 내 실제 당첨 선정 순서. 상위 등수부터 선정하며 UNFILLED는 NULL',
  `created_at` DATETIME(6) NOT NULL COMMENT '선정·미충원 결과 기록 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_draw_results_1` (`draw_run_id`, `event_prize_id`, `slot_number`),
  UNIQUE KEY `idx_draw_results_2` (`draw_run_id`, `candidate_id`),
  UNIQUE KEY `idx_draw_results_3` (`draw_run_id`, `selection_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='추첨 결과 - 확정 결과의 취소와 공개는 별도 기록에서 조회. 한 실행에서 경품 자리 하나를 선정한 결과 또는 미충원 사실. CHECK: SELECTED는 후보·선정 순서 필수, UNFILLED는 둘 다 NULL. slot_number/selection_order>0. 확정 후 불변. 취소와 공개 상태는 별도 사실로 조회.';

-- 당첨 취소 내역
CREATE TABLE `award_cancellations` (
  `id` BINARY(16) NOT NULL COMMENT '당첨 취소 ID',
  `draw_result_id` BINARY(16) NOT NULL COMMENT '취소한 SELECTED 추첨 결과 ID',
  `draw_run_id` BINARY(16) NULL COMMENT '빈자리를 보충할 REPLACEMENT 실행 ID. 취소 저장 시 NULL, 재추첨 등록 시 설정 후 변경·해제 금지. 여러 취소가 같은 실행 참조 가능',
  `canceled_by` BINARY(16) NOT NULL COMMENT '당첨 취소를 확정한 관리자 ID',
  `reason` TEXT NOT NULL COMMENT '당첨 취소 사유',
  `canceled_at` DATETIME(6) NOT NULL COMMENT '당첨 취소 확정 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_award_cancellations_draw_result_id` (`draw_result_id`),
  KEY `idx_award_cancellations_draw_run` (`draw_run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='당첨 취소 내역 - 선정된 당첨 결과 하나의 취소 사실과 개별 처리 근거. 원본 결과를 삭제하지 않음. 취소 후 실행 등록 전에는 실행 ID NULL. 실행 등록과 취소들의 FK 연결을 함께 커밋. 서비스가 취소 가능·현재 유효 당첨·같은 이벤트·재추첨 종류·연결 불변을 검증.';

-- 추첨 정합성 검증 내역
CREATE TABLE `draw_checks` (
  `id` BINARY(16) NOT NULL COMMENT '정합성 검증 기록 ID',
  `draw_run_id` BINARY(16) NOT NULL COMMENT '검증한 확정 추첨 실행 ID',
  `checked_by` BINARY(16) NOT NULL COMMENT '검증을 요청한 관리자 ID',
  `passed` BOOLEAN NOT NULL COMMENT '전체 검증 통과 여부. true=통과, false=불통과',
  `checks_snapshot` JSON NOT NULL COMMENT '항목별 정합성 검증 결과 스냅샷. 검증 버전·검증 범위·항목 코드·통과 여부·메시지.',
  `checked_at` DATETIME(6) NOT NULL COMMENT '검증 완료·기록 시각 UTC',
  PRIMARY KEY (`id`),
  KEY `idx_draw_checks_1` (`draw_run_id`, `checked_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='추첨 정합성 검증 내역 - 감사·운영 이력. 관리자가 요청한 정합성 검증 한 번의 감사·운영 기록. 정상적인 불통과도 저장. 원본 결과와 스냅샷을 수정하지 않음.';

-- 당첨 명단 공개 이력
CREATE TABLE `draw_publications` (
  `id` BINARY(16) NOT NULL COMMENT '공개 명단 버전 ID',
  `event_id` BINARY(16) NOT NULL COMMENT '명단을 공개한 이벤트 ID',
  `revision` INT NOT NULL COMMENT '이벤트별 공개 명단 버전 번호. 최초 1, 갱신마다 증가',
  `source_draw_id` BINARY(16) NOT NULL COMMENT '이번 공개·갱신의 원인이 된 추첨 실행 ID. 명단에는 이전 실행 결과도 포함 가능',
  `publication_type` ENUM('INITIAL', 'UPDATE') NOT NULL COMMENT '공개 종류. INITIAL=최초 자동 공개, UPDATE=관리자 확인 후 명단 갱신',
  `reason` TEXT NULL COMMENT '공개 명단 변경 사유. 관리자 갱신은 필수, 최초 자동 공개는 NULL.',
  `confirmed_by` BINARY(16) NULL COMMENT '재추첨 결과 공개를 확인한 관리자 ID. 최초 자동 공개는 NULL, 관리자 갱신은 필수.',
  `confirmed_at` DATETIME(6) NULL COMMENT '관리자의 공개 확인 시각 UTC. 최초 자동 공개는 NULL, 관리자 갱신은 필수.',
  `published_at` DATETIME(6) NOT NULL COMMENT '해당 명단 버전을 실제 공개에 반영한 시각 UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_draw_publications_source_draw_id` (`source_draw_id`),
  UNIQUE KEY `idx_draw_publications_1` (`event_id`, `revision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='당첨 명단 공개 이력 - 공개 기준 업무 데이터와 확인·변경 감사 이력. 실제로 공개에 반영한 명단 버전 한 번. revision=1 최초, 이후 증가. 확인과 공개 헤더 저장을 한 트랜잭션으로 처리하고 기존 버전은 불변. 전체 명단은 공개된 불변 결과 Query로 구성. 같은 source_draw_id 재반영은 기존 버전 반환. CHECK: INITIAL은 revision=1/처리자·확인 시각·사유 NULL, UPDATE는 revision>1/세 값 필수.';

-- 테이블 생성 뒤 관계를 추가한다. 취소의 nullable 실행 FK로 여러 취소를 하나의 재추첨에 연결한다.

ALTER TABLE `event_prizes`
  ADD CONSTRAINT `fk_drawing_review_01` FOREIGN KEY (`event_id`)
  REFERENCES `events` (`id`);

ALTER TABLE `event_participants`
  ADD CONSTRAINT `fk_drawing_review_02` FOREIGN KEY (`event_id`)
  REFERENCES `events` (`id`);

ALTER TABLE `event_participants`
  ADD CONSTRAINT `fk_drawing_review_03` FOREIGN KEY (`user_id`)
  REFERENCES `users` (`id`);

ALTER TABLE `draw_runs`
  ADD CONSTRAINT `fk_drawing_review_04` FOREIGN KEY (`event_id`)
  REFERENCES `events` (`id`);

ALTER TABLE `award_cancellations`
  ADD CONSTRAINT `fk_drawing_review_20` FOREIGN KEY (`draw_run_id`)
  REFERENCES `draw_runs` (`id`);

ALTER TABLE `draw_failures`
  ADD CONSTRAINT `fk_drawing_review_19` FOREIGN KEY (`draw_run_id`)
  REFERENCES `draw_runs` (`id`);

ALTER TABLE `draw_candidates`
  ADD CONSTRAINT `fk_drawing_review_07` FOREIGN KEY (`draw_run_id`)
  REFERENCES `draw_runs` (`id`);

ALTER TABLE `draw_candidates`
  ADD CONSTRAINT `fk_drawing_review_08` FOREIGN KEY (`participant_id`)
  REFERENCES `event_participants` (`id`);

ALTER TABLE `draw_results`
  ADD CONSTRAINT `fk_drawing_review_09` FOREIGN KEY (`draw_run_id`)
  REFERENCES `draw_runs` (`id`);

ALTER TABLE `draw_results`
  ADD CONSTRAINT `fk_drawing_review_10` FOREIGN KEY (`event_prize_id`)
  REFERENCES `event_prizes` (`id`);

ALTER TABLE `draw_results`
  ADD CONSTRAINT `fk_drawing_review_11` FOREIGN KEY (`draw_run_id`, `candidate_id`)
  REFERENCES `draw_run_candidates` (`draw_run_id`, `candidate_id`);

ALTER TABLE `award_cancellations`
  ADD CONSTRAINT `fk_drawing_review_12` FOREIGN KEY (`draw_result_id`)
  REFERENCES `draw_results` (`id`);

ALTER TABLE `award_cancellations`
  ADD CONSTRAINT `fk_drawing_review_13` FOREIGN KEY (`canceled_by`)
  REFERENCES `users` (`id`);

ALTER TABLE `draw_checks`
  ADD CONSTRAINT `fk_drawing_review_14` FOREIGN KEY (`draw_run_id`)
  REFERENCES `draw_runs` (`id`);

ALTER TABLE `draw_checks`
  ADD CONSTRAINT `fk_drawing_review_15` FOREIGN KEY (`checked_by`)
  REFERENCES `users` (`id`);

ALTER TABLE `draw_publications`
  ADD CONSTRAINT `fk_drawing_review_16` FOREIGN KEY (`event_id`)
  REFERENCES `events` (`id`);

ALTER TABLE `draw_publications`
  ADD CONSTRAINT `fk_drawing_review_17` FOREIGN KEY (`source_draw_id`)
  REFERENCES `draw_runs` (`id`);

ALTER TABLE `draw_publications`
  ADD CONSTRAINT `fk_drawing_review_18` FOREIGN KEY (`confirmed_by`)
  REFERENCES `users` (`id`);



-- [명단 Query 예시: 설명용이며 ERDCloud에 실행 객체를 추가하지 않는다]
-- :event_id = 조회 이벤트, :revision = 조회 공개 버전. 현재 명단은 이벤트 MAX(revision).
-- WITH ranked_results AS (
--   SELECT r.id, r.event_prize_id, r.slot_number, r.result_type, r.candidate_id,
--          p.revision,
--          ROW_NUMBER() OVER (
--            PARTITION BY r.event_prize_id, r.slot_number ORDER BY p.revision DESC
--          ) AS rn
--   FROM draw_publications p
--   JOIN draw_runs d ON d.id = p.source_draw_id AND d.event_id = p.event_id
--   JOIN draw_results r ON r.draw_run_id = d.id
--   WHERE p.event_id = :event_id AND p.revision <= :revision
--     AND d.status = 'CONFIRMED'
-- )
-- SELECT * FROM ranked_results WHERE rn = 1;
-- UNFILLED도 자리 하나의 결과이므로 SELECTED만 먼저 필터링하지 않는다.
-- 당첨자 식별: 결과.candidate_id -> draw_candidates.participant_id
-- -> event_participants.user_id. 공개 응답에는 사용자 ID·원본 개인정보를 노출하지 않음.
-- 경품의 당시 이름·등수·인원은 해당 결과 실행의 rules_snapshot에서 조회한다.
-- 변경 전후: revision N과 N-1의 명단을 경품·자리로 연결해 결과 ID 차이를 비교한다.
-- 취소자는 award_cancellations로 식별하되 현재 취소와 과거 공개 명단 사실을 혼동하지 않음.

ALTER TABLE `draw_run_candidates`
  ADD CONSTRAINT `fk_drawing_links_run` FOREIGN KEY (`draw_run_id`)
  REFERENCES `draw_runs` (`id`);

ALTER TABLE `draw_run_candidates`
  ADD CONSTRAINT `fk_drawing_links_candidate` FOREIGN KEY (`candidate_id`)
  REFERENCES `draw_candidates` (`id`);
