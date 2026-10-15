-- Each UNION ALL branch bounds its scan by recruitment status and its corresponding UTC deadline.
CREATE INDEX `idx_events_lifecycle_start` ON `events` (`status`, `deleted_at`, `starts_at`, `id`);
CREATE INDEX `idx_events_lifecycle_end` ON `events` (`status`, `deleted_at`, `ends_at`, `id`);
