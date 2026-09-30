-- Source: docs/03-database/schema.dbml
-- Initial tables and foreign keys owned by user.

CREATE TABLE `users` (
  `id` BINARY(16) NOT NULL,
  `name` VARCHAR(20) NOT NULL,
  `role` ENUM('USER', 'ADMIN') NOT NULL,
  `status` ENUM('ACTIVE', 'INACTIVE') NOT NULL,
  `membership` ENUM('EXCELLENT', 'VIP', 'VVIP'),
  `phone_num` VARCHAR(11),
  `email` VARCHAR(255),
  `updated_at` DATETIME(6) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `suspended_at` DATETIME(6),
  `suspension_reason` TEXT,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_users_1` (`email`),
  UNIQUE KEY `uq_users_2` (`phone_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
