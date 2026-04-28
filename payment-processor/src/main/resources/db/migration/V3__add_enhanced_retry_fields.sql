-- V3__add_enhanced_retry_fields.sql
-- Add new status 'LOCKED_PENDING_RETRY' and 'SENT_AWAITING_RESPONSE' to the constraint
ALTER TABLE payments DROP CONSTRAINT ck_payment_status;
ALTER TABLE payments ADD CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'PENDING_RETRY', 'LOCKED_PENDING_RETRY', 'SENT_AWAITING_RESPONSE'));

-- Add enhanced retry tracking fields (next_retry_at already exists from V1)
ALTER TABLE payments ADD (
    retry_reason VARCHAR2(50),
    last_failed_at TIMESTAMP,
    locked_contention_count NUMBER DEFAULT 0
);

-- Index for scheduler performance
CREATE INDEX idx_payment_retry_lookup ON payments(status, next_retry_at);
