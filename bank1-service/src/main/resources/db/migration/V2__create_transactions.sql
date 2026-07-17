-- V2__create_transactions.sql
CREATE TABLE transactions (
    id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    payment_processor_id RAW(16),
    idempotency_key VARCHAR2(255) UNIQUE,
    sender_account_number VARCHAR2(20) NOT NULL,
    recipient_account_number VARCHAR2(20) NOT NULL,
    amount NUMBER(19,4) NOT NULL,
    currency VARCHAR2(3) DEFAULT 'INR' NOT NULL,
    status VARCHAR2(50) NOT NULL,
    failure_reason VARCHAR2(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    
    CONSTRAINT ck_tx_amount CHECK (amount > 0)
);

CREATE INDEX idx_tx_sender_acc ON transactions(sender_account_number);
CREATE INDEX idx_tx_recipient_acc ON transactions(recipient_account_number);
CREATE INDEX idx_tx_status ON transactions(status);
