# Auth Service

## Overview

The **Auth Service** is a Spring Boot-based microservice providing authentication and authorization functionality using JWT, OAuth2, and Role-Based Access Control (RBAC). It supports user registration, login, token refresh, logout, password change, and profile retrieval. The service uses Flyway for database migrations and is Docker-ready for easy deployment.

### Features
- **User Authentication**: Register and authenticate users with email and password.
- **JWT-based Authorization**: Issues access and refresh tokens (Bearer and Refresh types).
- **OAuth2 Support**: Integrates with external OAuth2 providers.
- **RBAC**: Role-based access control with `ROLE_USER` and `ROLE_ADMIN`.
- **Password Management**: Allows users to change passwords securely.
- **Profile Retrieval**: Returns user profile information (ID, email, role).
- **Database**: PostgreSQL with Flyway migrations.
- **API Documentation**: Swagger/OpenAPI integration.
- **Error Handling**: Centralized exception handling with `GlobalExceptionHandler`.

## Prerequisites

- **Java**: 21
- **Maven**: 3.9.x
- **PostgreSQL**: 15.x
- **Docker**: Latest version
- **Git**: Latest version

## Setup and Installation

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/your-repo/auth-service.git
   cd auth-service