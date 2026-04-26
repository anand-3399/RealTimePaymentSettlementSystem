-- V1__create_bank_accounts.sql
CREATE TABLE bank_accounts (
    id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    account_number VARCHAR2(20) NOT NULL UNIQUE,
    account_holder_name VARCHAR2(255) NOT NULL,
    balance NUMBER(19,4) DEFAULT 0 NOT NULL,
    currency VARCHAR2(3) DEFAULT 'INR' NOT NULL,
    status VARCHAR2(50) DEFAULT 'ACTIVE' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT ck_balance_positive CHECK (balance >= 0),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

-- CREATE INDEX idx_bank_account_number ON bank_accounts(account_number);

-- SEED DATA
INSERT INTO bank_accounts (account_number, account_holder_name, balance, currency, status)
VALUES ('0123456789', 'Anand Jaiswar', 1000000, 'INR', 'ACTIVE');

INSERT INTO bank_accounts (account_number, account_holder_name, balance, currency, status)
VALUES ('1234567890', 'Recipient Account', 1000000, 'INR', 'ACTIVE');

COMMIT;
