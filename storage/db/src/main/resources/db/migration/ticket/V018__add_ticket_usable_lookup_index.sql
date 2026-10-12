-- GD-133: 응모권 차감이 사용자의 사용 가능한 응모권을 등급별로 만료가 이른 순(expires_at, id)으로 찾는 조회용 인덱스.
-- user_id, grade 동등 조건 뒤에 expires_at을 두면 InnoDB가 PK(id)를 뒤에 붙여 (expires_at, id) 순서로 읽혀 정렬이 필요 없다.
-- status를 인덱스에 넣으면 expires_at과 id 사이에 끼어 같은 만료 시각의 순서가 깨지므로 넣지 않는다(상태는 행에서 거른다).
-- 버전 번호는 열린 PR(GD-59)의 V017과 겹치지 않게 V018을 쓴다.
CREATE INDEX `ix_tickets_user_grade_expiry` ON `tickets` (`user_id`, `grade`, `expires_at`);
