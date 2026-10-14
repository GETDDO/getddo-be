-- GD-84: 정상 종료는 실패와 구분하며 동일 최초 실행을 다시 준비하지 않는다.
-- V012/V013은 삭제된 마이그레이션의 번호이므로 재사용하지 않는다.
ALTER TABLE draw_runs MODIFY COLUMN status
  ENUM ('PREPARING', 'READY', 'RUNNING', 'CONFIRMED', 'FAILED', 'NO_ENTRIES', 'NO_CANDIDATES') NOT NULL
  COMMENT '실행 상태. NO_ENTRIES=응모자 없음, NO_CANDIDATES=전원 제외 정상 종료';

ALTER TABLE draw_runs ADD CONSTRAINT chk_draw_empty_fixed_input CHECK
  (status NOT IN ('NO_ENTRIES', 'NO_CANDIDATES') OR
   (algorithm_version IS NOT NULL AND rules_snapshot IS NOT NULL AND snapshot_fixed_at IS NOT NULL));
