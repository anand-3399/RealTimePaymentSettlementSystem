-- V8__create_outbox_events.sql
CREATE TABLE outbox_events (
    id RAW(16) DEFAULT SYS_GUID() PRIMARY KEY,
    event_type VARCHAR2(100) NOT NULL,
    payload CLOB NOT NULL,
    status VARCHAR2(50) DEFAULT 'PENDING' NOT NULL,
    retry_count NUMBER DEFAULT 0 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

CREATE INDEX idx_outbox_status ON outbox_events(status);
CREATE INDEX idx_outbox_created_at ON outbox_events(created_at);

COMMENT ON COLUMN outbox_events.status IS 'PENDING, PUBLISHED, FAILED';
