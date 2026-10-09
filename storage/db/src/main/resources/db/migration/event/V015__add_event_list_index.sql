-- Shared user/admin list: deleted_at IS NULL, created_at DESC, id DESC.
-- MySQL can scan the ascending sort keys backward for the descending order.
CREATE INDEX `idx_events_list` ON `events` (`deleted_at`, `created_at`, `id`);
