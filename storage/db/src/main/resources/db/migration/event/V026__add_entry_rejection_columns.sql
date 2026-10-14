-- 거절된 응모 이력(GD-145)을 event_entries에 함께 저장한다.
-- 접수 완료(ACCEPTED)는 기존과 같고, 업무 거절(REJECTED)은 응모자·차감 없이 거절 코드와 요청 장수만 남긴다.
-- 이 마이그레이션이 처음 적용되는 DB의 기존 행은 모두 ACCEPTED다.

ALTER TABLE `event_entries`
  ADD COLUMN `event_id` binary(16) NULL COMMENT '응모 대상 이벤트 ID. 거절 건은 응모자가 없어 직접 보관한다' AFTER `id`,
  ADD COLUMN `status` ENUM ('ACCEPTED', 'REJECTED') NOT NULL DEFAULT 'ACCEPTED' COMMENT '접수 결과. ACCEPTED는 응모자·차감이 있고 REJECTED는 업무 규칙 거절 이력이다' AFTER `user_id`,
  ADD COLUMN `rejection_code` varchar(20) NULL COMMENT '거절 건의 오류 코드(예: ENTRY-005, TICKET-006). 접수 건은 NULL' AFTER `status`,
  ADD COLUMN `rejection_reason` varchar(200) NULL COMMENT '거절 건의 사용자 안내 사유. 원문 예외·개인정보 저장 금지. 접수 건은 NULL' AFTER `rejection_code`,
  ADD COLUMN `requested_bronze_count` int NOT NULL DEFAULT 0 COMMENT '요청한 브론즈 응모권 장수' AFTER `deducted_ticket_count`,
  ADD COLUMN `requested_silver_count` int NOT NULL DEFAULT 0 COMMENT '요청한 실버 응모권 장수' AFTER `requested_bronze_count`,
  ADD COLUMN `requested_gold_count` int NOT NULL DEFAULT 0 COMMENT '요청한 골드 응모권 장수' AFTER `requested_silver_count`;

-- 기존 접수 건: 이벤트는 응모자에서, 등급별 요청 장수는 접수된 응모가 요청한 만큼 차감하므로 사용 이력에서 가져온다.
UPDATE `event_entries` e
  JOIN `event_participants` p ON p.id = e.participant_id
  SET e.event_id = p.event_id;

UPDATE `event_entries` e SET
  e.requested_bronze_count = (SELECT COUNT(*) FROM `ticket_histories` h JOIN `tickets` t ON t.id = h.ticket_id
    WHERE h.event_entry_id = e.id AND h.operation_type = 'USE' AND t.grade = 'BRONZE'),
  e.requested_silver_count = (SELECT COUNT(*) FROM `ticket_histories` h JOIN `tickets` t ON t.id = h.ticket_id
    WHERE h.event_entry_id = e.id AND h.operation_type = 'USE' AND t.grade = 'SILVER'),
  e.requested_gold_count = (SELECT COUNT(*) FROM `ticket_histories` h JOIN `tickets` t ON t.id = h.ticket_id
    WHERE h.event_entry_id = e.id AND h.operation_type = 'USE' AND t.grade = 'GOLD');

ALTER TABLE `event_entries`
  MODIFY COLUMN `event_id` binary(16) NOT NULL COMMENT '응모 대상 이벤트 ID. 거절 건은 응모자가 없어 직접 보관한다',
  MODIFY COLUMN `participant_id` binary(16) NULL COMMENT '접수 완료된 응모의 이벤트 참여자 ID. 거절 건은 NULL',
  ADD CONSTRAINT `fk_event_entries_event` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  ADD CONSTRAINT `fk_event_entries_participant_event` FOREIGN KEY (`participant_id`, `event_id`, `user_id`)
    REFERENCES `event_participants` (`id`, `event_id`, `user_id`),
  ADD CONSTRAINT `chk_entry_requested_grades` CHECK (requested_bronze_count >= 0 AND requested_silver_count >= 0
    AND requested_gold_count >= 0),
  ADD CONSTRAINT `chk_entry_status` CHECK (
    (status = 'ACCEPTED' AND participant_id IS NOT NULL AND rejection_code IS NULL AND rejection_reason IS NULL)
    OR (status = 'REJECTED' AND participant_id IS NULL AND rejection_code IS NOT NULL AND rejection_reason IS NOT NULL
        AND deducted_ticket_count = 0)),
  ADD INDEX `idx_event_entries_user_created` (`user_id`, `created_at`, `id`),
  ADD INDEX `idx_event_entries_user_event_created` (`user_id`, `event_id`, `created_at`, `id`),
  ADD INDEX `idx_event_entries_event` (`event_id`);
