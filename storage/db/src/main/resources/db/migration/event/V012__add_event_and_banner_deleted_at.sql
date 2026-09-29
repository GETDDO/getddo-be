-- Source: docs/03-database/schema.dbml
-- Preserve event and banner rows while marking them as deleted.

ALTER TABLE `events`
  ADD COLUMN `deleted_at` DATETIME(6) NULL AFTER `updated_at`;

ALTER TABLE `banners`
  ADD COLUMN `deleted_at` DATETIME(6) NULL AFTER `updated_at`;
