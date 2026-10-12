-- GD-87: 실행 제어는 기존 실행 행에 저장하며 실패 횟수는 이력에서 조회한다.
ALTER TABLE draw_runs
    ADD COLUMN attempt_id binary(16) NULL COMMENT '진행 중 시도와 멱등 실패 기록 ID',
    ADD COLUMN lease_until datetime(6) NULL COMMENT 'UTC. 중단 복구용 소유권 만료 시각',
    ADD COLUMN retry_at datetime(6) NULL COMMENT 'UTC. FAILED에서 NULL이면 운영자 대기',
    ADD COLUMN retry_cycle_started_at datetime(6) NULL COMMENT 'UTC. 자동 재시도 구간의 시작 경계',
    ADD CONSTRAINT chk_draw_attempt CHECK ((attempt_id IS NULL AND lease_until IS NULL) OR
        (attempt_id IS NOT NULL AND lease_until IS NOT NULL AND retry_cycle_started_at IS NOT NULL
         AND status IN ('PREPARING','READY','RUNNING'))),
    ADD CONSTRAINT chk_draw_retry CHECK (retry_at IS NULL OR
        (status = 'FAILED' AND attempt_id IS NULL AND retry_cycle_started_at IS NOT NULL)),
    ADD INDEX draw_retry_due_idx (status, retry_at),
    ADD INDEX draw_lease_due_idx (status, lease_until);
CREATE INDEX draw_failure_history_idx ON draw_failures (draw_run_id, failed_at, id);
