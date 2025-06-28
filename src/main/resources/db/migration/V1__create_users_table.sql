CREATE TABLE users
(
    id        BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    firstname VARCHAR(255) NOT NULL,
    lastname  VARCHAR(255),
    email     VARCHAR(255) NOT NULL UNIQUE,
    password  VARCHAR(255),
    provider  VARCHAR(50)  NOT NULL,
    enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    role      VARCHAR(50)  NOT NULL
);