CREATE TABLE users
(
    id        BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    firstname VARCHAR(255) NOT NULL,
    lastname  VARCHAR(255),
    email     VARCHAR(255) NOT NULL UNIQUE,
    password  VARCHAR(255),
    provider  VARCHAR(50)  NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    role      VARCHAR(50)  NOT NULL,
    CONSTRAINT unique_provider_id UNIQUE (provider_id)
);

CREATE INDEX idx_users_provider_provider_id ON users (provider, provider_id);
CREATE INDEX idx_users_email ON users (email);