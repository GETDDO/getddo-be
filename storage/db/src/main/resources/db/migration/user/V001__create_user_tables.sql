-- Source: docs/03-database/schema.dbml
-- GD-93: Initial schema; version numbers retained by user request.
-- Initial tables and foreign keys owned by user.

CREATE TABLE `users` (
  `id` binary(16) NOT NULL COMMENT '사용자 ID',
  `name` varchar(20) NOT NULL COMMENT '사용자 이름',
  `role` ENUM ('USER', 'ADMIN') NOT NULL COMMENT 'USER=일반 사용자 / ADMIN=관리자',
  `status` ENUM ('ACTIVE', 'INACTIVE') NOT NULL COMMENT 'ACTIVE=활성 / INACTIVE=비활성',
  `membership` ENUM ('EXCELLENT', 'VIP', 'VVIP') COMMENT 'EXCELLENT=우수 / VIP=VIP / VVIP=VVIP',
  `phone_num` varchar(11) COMMENT '핸드폰 번호',
  `email` varchar(255) COMMENT '이메일',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각 UTC',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  `suspended_at` datetime(6) COMMENT '사용자 정지 시작 시각 UTC',
  `suspension_reason` text COMMENT '사용자 정지 사유',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_users_1` (`email`),
  UNIQUE KEY `uq_users_2` (`phone_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
