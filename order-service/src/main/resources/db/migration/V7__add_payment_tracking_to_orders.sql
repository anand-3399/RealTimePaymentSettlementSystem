ALTER TABLE payment_orders ADD (
    payment_id RAW(16),
    gateway_transaction_id VARCHAR2(255),
    processed_at TIMESTAMP
);
