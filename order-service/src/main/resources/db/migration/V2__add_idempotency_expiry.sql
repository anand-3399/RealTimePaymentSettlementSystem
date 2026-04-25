-- Add Expiration Column to Idempotency Keys
ALTER TABLE idempotency_keys ADD (expires_at TIMESTAMP DEFAULT (CURRENT_TIMESTAMP + INTERVAL '24' HOUR) NOT NULL);

-- Index for cleanup job
CREATE INDEX idx_idem_expires_at ON idempotency_keys(expires_at);
