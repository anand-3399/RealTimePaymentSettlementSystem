-- V3__create_idempotency_keys.sql
CREATE TABLE idempotency_keys (
    id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    idempotency_key VARCHAR2(255) NOT NULL UNIQUE,
    transaction_id RAW(16),
    response_body CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_idem_tx FOREIGN KEY (transaction_id) REFERENCES transactions(id)
);

-- CREATE INDEX idx_idem_key ON idempotency_keys(idempotency_key);
