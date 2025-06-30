CREATE TABLE tokens
(
    id         BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    token      TEXT        NOT NULL UNIQUE,
    token_type VARCHAR(50) NOT NULL,
    expired    BOOLEAN     NOT NULL DEFAULT FALSE,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    user_id    BIGINT      NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);