-- Rename Gateway Txn ID and Payment ID
ALTER TABLE payments RENAME COLUMN gateway_transaction_id TO bank_reference_id;
ALTER TABLE payment_transaction_logs RENAME COLUMN payment_id TO payment_gateway_id;
ALTER TABLE webhook_logs RENAME COLUMN payment_id TO payment_gateway_id;
