-- Rename existing table to cache
ALTER TABLE bank_accounts RENAME TO bank_accounts_cache;

-- Add new columns for optimistic locking and sync
ALTER TABLE bank_accounts_cache ADD account_version NUMBER(19) DEFAULT 0 NOT NULL;
ALTER TABLE bank_accounts_cache ADD last_synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE bank_accounts_cache ADD sync_status VARCHAR2(20) DEFAULT 'IN_SYNC' NOT NULL;

-- Create Accounting Entries table
CREATE TABLE accounting_entries (
    entry_id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    payment_id RAW(16) NOT NULL,
    sender_account_id VARCHAR2(255) NOT NULL,
    recipient_account_id VARCHAR2(255) NOT NULL,
    amount NUMBER(19,4) NOT NULL,
    pg_name VARCHAR2(100) NOT NULL,
    entry_type VARCHAR2(20) NOT NULL,
    entry_status VARCHAR2(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    committed_at TIMESTAMP,
    entry_hash VARCHAR2(500),
    
    CONSTRAINT ck_acc_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT', 'REVERSAL')),
    CONSTRAINT ck_acc_entry_status CHECK (entry_status IN ('PENDING', 'COMMITTED', 'FAILED'))
);

CREATE INDEX idx_acc_entries_payment ON accounting_entries(payment_id);
CREATE INDEX idx_acc_entries_sender ON accounting_entries(sender_account_id);
