-- V2__add_idempotency_key_and_update_status.sql
ALTER TABLE payments ADD (idempotency_key VARCHAR2(255));

-- Drop existing status constraint and add new one
ALTER TABLE payments DROP CONSTRAINT ck_payment_status;
ALTER TABLE payments ADD CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'PENDING_RETRY'));

CREATE INDEX idx_pay_idem_key ON payments(idempotency_key);
