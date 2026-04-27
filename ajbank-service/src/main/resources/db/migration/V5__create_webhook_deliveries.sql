CREATE TABLE webhook_deliveries (
    id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    correlation_id VARCHAR2(100),
    payload CLOB,
    signature VARCHAR2(256),
    response_status NUMBER(3),
    retry_count NUMBER(3) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_correlation ON webhook_deliveries(correlation_id);
