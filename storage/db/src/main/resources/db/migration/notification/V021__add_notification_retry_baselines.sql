-- 누적 선점 차수는 유지하고 관리자 복구 이후 자동 재시도 횟수만 새로 계산한다.
ALTER TABLE notification_jobs
    ADD COLUMN attempt_base int NOT NULL DEFAULT 0 COMMENT '현재 수동 복구 구간의 시작 선점 차수',
    ADD CONSTRAINT chk_notification_attempt_base CHECK (attempt_base >= 0 AND attempt_base <= attempt_count);

ALTER TABLE notifications
    ADD COLUMN delivery_attempt_base int NOT NULL DEFAULT 0 COMMENT '현재 수동 발송 복구 구간의 시작 선점 차수',
    ADD CONSTRAINT chk_delivery_attempt_base CHECK (delivery_attempt_base >= 0 AND delivery_attempt_base <= delivery_attempt_count);
