-- Payment Processor Schema (Oracle SQL)

-- Create Payments Table
CREATE TABLE payments (
    id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    order_id RAW(16) NOT NULL UNIQUE,
    user_id VARCHAR2(255) NOT NULL,
    sender_account VARCHAR2(255) NOT NULL,
    recipient_account VARCHAR2(255) NOT NULL,
    amount NUMBER(19,4) NOT NULL,
    currency VARCHAR2(3) DEFAULT 'INR' NOT NULL,
    status VARCHAR2(50) NOT NULL,
    gateway_transaction_id VARCHAR2(255) UNIQUE,
    gateway_response VARCHAR2(2000),
    retry_count NUMBER(10) DEFAULT 0,
    max_retries NUMBER(10) DEFAULT 3,
    last_retry_at TIMESTAMP,
    next_retry_at TIMESTAMP,
    correlation_id VARCHAR2(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    failed_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

-- Indexes for Payments
CREATE INDEX idx_pay_user_id ON payments(user_id);
CREATE INDEX idx_pay_status ON payments(status);
CREATE INDEX idx_pay_created_at ON payments(created_at);

-- Create Transaction Logs Table
CREATE TABLE payment_transaction_logs (
    id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    payment_id RAW(16) NOT NULL,
    action VARCHAR2(100) NOT NULL,
    from_status VARCHAR2(50),
    to_status VARCHAR2(50),
    message VARCHAR2(1000),
    correlation_id VARCHAR2(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_log_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

-- Indexes for Logs
CREATE INDEX idx_log_payment_id ON payment_transaction_logs(payment_id);
CREATE INDEX idx_log_created_at ON payment_transaction_logs(created_at);

-- Create Webhook Logs Table
CREATE TABLE webhook_logs (
    id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    payment_id RAW(16),
    event_type VARCHAR2(100),
    signature VARCHAR2(500),
    signature_valid NUMBER(1) DEFAULT 0, -- 0 for false, 1 for true
    request_body VARCHAR2(4000),
    response_status NUMBER(10),
    correlation_id VARCHAR2(255),
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed_at TIMESTAMP
);

-- Indexes for Webhooks
CREATE INDEX idx_web_payment_id ON webhook_logs(payment_id);
CREATE INDEX idx_web_received_at ON webhook_logs(received_at);

-- Create Reconciliation Table
CREATE TABLE payment_reconciliation (
    id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    reconciliation_date DATE NOT NULL UNIQUE,
    total_transactions NUMBER(20) DEFAULT 0,
    total_amount NUMBER(19,4) DEFAULT 0,
    successful_transactions NUMBER(20) DEFAULT 0,
    failed_transactions NUMBER(20) DEFAULT 0,
    orders_in_db NUMBER(20) DEFAULT 0,
    payments_in_db NUMBER(20) DEFAULT 0,
    webhooks_received NUMBER(20) DEFAULT 0,
    discrepancies NUMBER(20) DEFAULT 0,
    status VARCHAR2(50) DEFAULT 'PENDING' NOT NULL,
    notes VARCHAR2(1000),
    error_details VARCHAR2(2000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);
