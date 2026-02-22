CREATE TABLE tokens
(
    id         BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    token      TEXT         NOT NULL UNIQUE,
    token_type VARCHAR(50)  NOT NULL,
    expired    BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    user_id    BIGINT       NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_tokens_user_id ON tokens (user_id);
CREATE INDEX idx_tokens_expired_revoked ON tokens (user_id, expired, revoked);