-- GD-138: 기존 취소 이력은 유지하고 신규 요청의 전역 멱등 키를 보존한다.
ALTER TABLE award_cancellations
  ADD COLUMN request_key binary(16) NULL COMMENT '신규 AD05 요청의 UUID 멱등 키. 과거 이력은 NULL',
  ADD UNIQUE KEY uq_award_cancellations_request (request_key);
