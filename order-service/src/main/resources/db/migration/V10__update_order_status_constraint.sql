-- V10__update_order_status_constraint.sql
ALTER TABLE payment_orders DROP CONSTRAINT ck_order_status;
ALTER TABLE payment_orders ADD CONSTRAINT ck_order_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'COMPLETED'));
