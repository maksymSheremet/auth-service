CREATE TABLE outbox_events
(
    id           BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    processed    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

CREATE INDEX idx_outbox_unprocessed ON outbox_events (processed, created_at) WHERE processed = FALSE;
CREATE INDEX idx_outbox_cleanup ON outbox_events (processed, processed_at) WHERE processed = TRUE;