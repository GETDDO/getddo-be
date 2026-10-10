-- GD-133: 응모권 차감이 사용자의 사용 가능한 응모권을 만료 임박순으로 찾는 조회용 인덱스.
-- 조건은 user_id, status, expires_at 범위이고 정렬은 expires_at, grade, id(PK)다.
CREATE INDEX `ix_tickets_user_status_expiry` ON `tickets` (`user_id`, `status`, `expires_at`, `grade`);
