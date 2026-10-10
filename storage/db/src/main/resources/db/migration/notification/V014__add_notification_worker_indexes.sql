-- V012 and V013 were used by removed migrations; do not reuse those versions.
CREATE INDEX ix_notification_job_pending
    ON notification_jobs (status, scheduled_at, next_attempt_at);

CREATE INDEX ix_notification_job_lease
    ON notification_jobs (status, lease_until);

CREATE INDEX ix_notification_delivery_retry
    ON notifications (mock_delivery_status, next_delivery_attempt_at);
