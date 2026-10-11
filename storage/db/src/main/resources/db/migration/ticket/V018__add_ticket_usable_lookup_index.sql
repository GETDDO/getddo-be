-- GD-133: 응모권 차감이 사용자의 사용 가능한 응모권을 등급별로 만료가 이른 순(expires_at, id)으로 찾는 조회용 인덱스.
-- 조건은 user_id, grade, status, expires_at 범위다. 버전 번호는 열린 PR(GD-59)의 V017과 겹치지 않게 V018을 쓴다.
CREATE INDEX `ix_tickets_user_status_expiry` ON `tickets` (`user_id`, `status`, `expires_at`, `grade`);
