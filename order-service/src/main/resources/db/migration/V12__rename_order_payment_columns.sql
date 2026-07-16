-- Rename columns to match updated terminology
ALTER TABLE payment_orders RENAME COLUMN payment_id TO payment_gateway_id;
ALTER TABLE payment_orders RENAME COLUMN gateway_transaction_id TO bank_reference_id;
