-- V9__add_next_retry_at_to_outbox.sql
ALTER TABLE outbox_events ADD next_retry_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_outbox_next_retry ON outbox_events(next_retry_at);
