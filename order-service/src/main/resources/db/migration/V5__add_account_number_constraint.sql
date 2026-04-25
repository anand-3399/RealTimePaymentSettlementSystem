-- Add CHECK constraint to enforce exactly 10-digit account numbers
ALTER TABLE users ADD CONSTRAINT ck_account_number_format 
    CHECK (REGEXP_LIKE(account_number, '^[0-9]{10}$'));
