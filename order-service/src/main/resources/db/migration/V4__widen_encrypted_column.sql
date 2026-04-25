-- Widen recipient_bank_account to hold AES-256 encrypted (Base64) values
ALTER TABLE payment_orders MODIFY (recipient_bank_account VARCHAR2(255));
