-- Source: docs/03-database/schema.dbml
-- GD-93: Initial schema; version numbers retained by user request.
-- Initial tables and foreign keys owned by audit.

CREATE TABLE `audit_logs` (
  `id` binary(16) NOT NULL COMMENT '운영 감사 로그 ID',
  `actor_id` binary(16) COMMENT '처리자 사용자 ID',
  `action` varchar(60) NOT NULL COMMENT '감사 대상 작업 코드',
  `target_type` varchar(60) NOT NULL COMMENT '감사 대상 테이블 또는 업무 객체 종류',
  `target_id` binary(16) NOT NULL COMMENT '감사 대상 객체 UUID',
  `reason` text COMMENT '처리 사유',
  `before_data` json COMMENT '변경 전 데이터',
  `after_data` json COMMENT '변경 후 데이터',
  `request_id` varchar(100) COMMENT '관련 HTTP 요청 추적 ID',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  PRIMARY KEY (`id`),
  KEY `audit_target_time_idx` (`target_type`, `target_id`, `created_at`),
  CONSTRAINT `fk_audit_logs_1` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
