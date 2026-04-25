-- Add sender_bank_account column and expand recipient_bank_account for encryption overhead
ALTER TABLE payment_orders MODIFY (recipient_bank_account VARCHAR2(255));
ALTER TABLE payment_orders ADD (sender_bank_account VARCHAR2(255));

-- Populate existing orders from the users table
UPDATE payment_orders p
SET p.sender_bank_account = (SELECT u.account_number FROM users u WHERE u.username = p.username);

-- Set NOT NULL now that existing rows are populated
ALTER TABLE payment_orders MODIFY (sender_bank_account NOT NULL);
