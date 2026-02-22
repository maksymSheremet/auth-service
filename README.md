# Auth Service

## Overview

Spring Boot microservice providing authentication and authorization using JWT, OAuth2, and RBAC.
Communicates with **user-service** via Kafka using the Transactional Outbox Pattern for reliable event delivery.

## Features

- **JWT Authentication** — access + refresh tokens with DB-level revocation
- **OAuth2 Integration** — Google, GitHub
- **RBAC** — `USER`, `ADMIN`, `MANAGER` roles
- **Transactional Outbox Pattern** — reliable Kafka event publishing (at-least-once delivery)
- **Password Management** — BCrypt hashing, change password with token revocation
- **Database Migrations** — Flyway with versioned SQL scripts
- **Health Checks** — Spring Actuator with liveness/readiness probes

## Tech Stack

- **Java** 25
- **Gradle** 9.1.0
- **Spring Boot** 3.5.7
- **Spring Cloud** 2025.0.0
- **Spring Security** — OAuth2 Client, JWT (jjwt)
- **PostgreSQL** 15 + Flyway
- **Apache Kafka** — async messaging via outbox
- **Docker Compose** — local development

## Project Structure

```
my.code.auth
├── config/security/     — SecurityConfig, JwtProperties, BcryptProperties
├── config/              — ApplicationConfig, OAuth2AuthenticationSuccessHandler
├── controller/          — AuthController
├── database/entity/     — User, Token, Role, TokenType, OutboxEvent
├── database/repository/ — UserRepository, TokenRepository, OutboxEventRepository
├── dto/                 — RegisterRequest, AuthenticationRequest/Response (records)
├── event/               — UserRegisteredEvent
├── exception/           — GlobalExceptionHandler, custom exceptions
├── filter/              — JwtAuthenticationFilter
├── kafka/               — OutboxEventPublisher, OutboxProcessor
├── service/             — AuthenticationService, JwtService, TokenService, ...
└── util/                — AuthUtils, OAuth2Provider
```

## API Endpoints

| Method | Path                       | Auth     | Description              |
|--------|----------------------------|----------|--------------------------|
| POST   | `/api/auth/register`       | Public   | Register new user        |
| POST   | `/api/auth/authenticate`   | Public   | Login with email/password|
| POST   | `/api/auth/refresh`        | Public   | Refresh token pair       |
| POST   | `/api/auth/logout`         | Public   | Revoke tokens            |
| POST   | `/api/auth/change-password`| Bearer   | Change password          |
| GET    | `/api/auth/me`             | Bearer   | Current user info        |

## Kafka Events

| Event              | Topic                      | Trigger            |
|--------------------|----------------------------|--------------------|
| `USER_REGISTERED`  | `user-registered-events`   | Register / OAuth2  |

Events are published via the **Transactional Outbox Pattern**:
1. Business operation + outbox insert in a single DB transaction
2. `OutboxProcessor` polls every 5s and sends to Kafka
3. Processed events are cleaned up daily (7-day retention)

## Database Migrations

| Version | Description                     |
|---------|---------------------------------|
| V1      | Create `users` table            |
| V2      | Create `tokens` table           |
| V3      | Insert default admin user       |
| V4      | Create `outbox_events` table    |

## Setup

### Prerequisites
- Java 25
- Docker & Docker Compose

### Run locally

```bash
# Start infrastructure
docker-compose up -d

# Run the service
./gradlew bootRun
```

### Environment Variables

```env
DATA_URL=jdbc:postgresql://localhost:5432/authdb
DATA_USER=postgres
DATA_PASSWORD=postgres
YOUR_JWT_SECRET_KEY=<base64-encoded-256bit-key>
BCRYPT_STRENGTH=12
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
YOUR_GOOGLE_CLIENT_ID=...
YOUR_GOOGLE_CLIENT_SECRET=...
YOUR_GITHUB_CLIENT_ID=...
YOUR_GITHUB_CLIENT_SECRET=...
YOUR_GITHUB_INFO_URI=https://api.github.com/user
```
