-- GD-135: 만료 시각이 지났지만 아직 사용 가능 상태인 응모권을 찾는 만료 워커 조회용 인덱스.
-- 조건은 status IN ('AVAILABLE', 'RETURNED')과 expires_at <= 기준 시각이다. 대상이 없을 때나 대부분 사용·만료된 상태일 때도
-- 사용 가능 상태의 지난 만료 시각 구간만 읽도록 status를 앞에 둔다.
CREATE INDEX `ix_tickets_status_expiry` ON `tickets` (`status`, `expires_at`);
