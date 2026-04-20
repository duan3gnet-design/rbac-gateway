# 🔐 RBAC Gateway

A production-ready **microservices backend** with Role-Based Access Control, JWT authentication, and an API Gateway built on Spring Boot 4 + Spring Cloud.

---

## 📐 Architecture Overview

```
                        ┌─────────────────────────────────┐
                        │           API Gateway            │
                        │   (WebFlux · Port 8080)          │
                        │                                  │
                        │  ┌──────────┐  ┌─────────────┐  │
                        │  │  Route   │  │  JWT Filter │  │
                        │  │  Config  │  │  (Global)   │  │
                        │  └──────────┘  └─────────────┘  │
                        │                                  │
                        │  ┌──────────────────────────┐    │
                        │  │   Circuit Breaker        │    │
                        │  │   (Resilience4j)         │    │
                        │  └──────────────────────────┘    │
                        └──────────┬──────────┬────────────┘
                                   │          │
                     ┌─────────────┘          └─────────────┐
                     ▼                                       ▼
          ┌──────────────────┐                  ┌──────────────────────┐
          │   Auth Service   │                  │   Resource Service   │
          │   (Port 8081)    │                  │   (Port 8082)        │
          │                  │                  │                      │
          │  - Login/Logout  │                  │  - Protected APIs    │
          │  - Refresh Token │                  │  - RBAC Enforcement  │
          │  - Revoke Token  │                  │  - Permission Check  │
          │  - Google OAuth2 │                  │    (resource:action) │
          └────────┬─────────┘                  └──────────┬───────────┘
                   │                                        │
                   └──────────────┬─────────────────────────┘
                                  ▼
                        ┌──────────────────┐
                        │   PostgreSQL DB  │
                        │                 │
                        │  (Flyway        │
                        │   Migration)    │
                        └──────────────────┘
```

---

## 🏗️ Modules

| Module | Port | Description |
|---|---|---|
| `api-gateway` | `8080` | Spring Cloud Gateway (WebFlux) — routing, JWT validation, Circuit Breaker |
| `auth-service` | `8081` | Authentication — login, refresh token, token revocation, Google OAuth2 |
| `resource-service` | `8082` | Protected resources — RBAC enforcement, permission-based access control |
| `db-migration` | — | Standalone Flyway module — schema versioning & seeding |

---

## 🛠️ Tech Stack

| Category | Technology |
|---|---|
| Framework | Spring Boot `4.0.5` |
| Cloud | Spring Cloud `Oakwood` |
| Gateway | Spring Cloud Gateway (WebFlux / Reactive) |
| Security | Spring Security, JJWT |
| OAuth2 | Google OAuth2 (Redirect flow + SDK flow) |
| Resilience | Resilience4j (Circuit Breaker) |
| Database | PostgreSQL |
| Migration | Flyway (separate module) |
| Java | Java 21 |

---

## ✅ Implemented Features

### Authentication (`auth-service`)
- [x] Login / Logout
- [x] JWT Access Token + Refresh Token
- [x] Token Revocation (blacklist / DB-backed)
- [x] Google OAuth2 — Redirect flow
- [x] Google OAuth2 — SDK flow (token exchange)

### Authorization
- [x] Role-Based Access Control (RBAC) in `resource-service`
- [x] Advanced RBAC — permissions encoded as `resource:action` (e.g., `user:read`, `order:delete`) embedded in JWT claims
- [x] API Gateway — JWT validation filter (global)

### Infrastructure
- [x] Spring Cloud Gateway routing
- [x] Flyway as a standalone `db-migration` module
- [x] Circuit Breaker with Resilience4j (api-gateway)

---

## 🔑 Permission Model

Permissions follow the `resource:action` convention and are embedded directly in the JWT payload:

```json
{
  "sub": "user-uuid",
  "roles": ["ROLE_ADMIN", "ROLE_USER"],
  "permissions": [
    "user:read",
    "user:write",
    "order:read",
    "product:delete"
  ],
  "exp": 1712345678
}
```

The `resource-service` validates these permissions using Spring Security method-level annotations or a custom `PermissionEvaluator`.

---

## 🚀 Getting Started

### Prerequisites

- Java 21
- Docker & Docker Compose
- PostgreSQL (or use the provided Compose file)

### Run with Docker Compose

```bash
docker compose up -d
```

### Run individually

```bash
# 1. Run database migrations first
cd db-migration && ./mvnw spring-boot:run

# 2. Start auth-service
cd auth-service && ./mvnw spring-boot:run

# 3. Start resource-service
cd resource-service && ./mvnw spring-boot:run

# 4. Start api-gateway
cd api-gateway && ./mvnw spring-boot:run
```

---

## 📡 API Endpoints

### Auth Service (via Gateway → `POST /auth/**`)

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/login` | Authenticate and receive tokens |
| `POST` | `/auth/refresh` | Exchange refresh token for new access token |
| `POST` | `/auth/logout` | Revoke current refresh token |
| `GET` | `/auth/oauth2/google` | Initiate Google OAuth2 redirect flow |
| `POST` | `/auth/oauth2/google/sdk` | Exchange Google ID token (SDK flow) |

### Resource Service (via Gateway → `GET /api/**`)

> All endpoints require a valid Bearer token.

| Method | Path | Required Permission |
|---|---|---|
| `GET` | `/api/users` | `user:read` |
| `POST` | `/api/users` | `user:write` |
| `DELETE` | `/api/users/{id}` | `user:delete` |
| *(add your own routes)* | | |

---

## ⚙️ Configuration

### `api-gateway` — `application.yml` (key sections)

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/auth/**
        - id: resource-service
          uri: lb://resource-service
          predicates:
            - Path=/api/**
          filters:
            - name: CircuitBreaker
              args:
                name: resourceServiceCB
                fallbackUri: forward:/fallback

resilience4j:
  circuitbreaker:
    instances:
      resourceServiceCB:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
```

### `auth-service` — JWT & OAuth2

```yaml
app:
  jwt:
    secret: ${JWT_SECRET}
    access-token-expiry: 900        # 15 minutes
    refresh-token-expiry: 604800    # 7 days

spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
```

---

## 🗂️ Database Migration

Flyway migrations live in `db-migration/src/main/resources/db/migration/`:

```
V1__create_users_table.sql
V2__create_roles_table.sql
V3__create_permissions_table.sql
V4__seed_default_roles.sql
...
```

Run migrations standalone:

```bash
cd db-migration
./mvnw flyway:migrate
```

---

## 🧱 Project Structure

```
rbac-gateway/
├── api-gateway/
│   ├── src/main/java/.../
│   │   ├── filter/         # JWT global filter
│   │   ├── config/         # Route & security config
│   │   └── fallback/       # Circuit breaker fallback controller
│   └── src/main/resources/application.yml
│
├── auth-service/
│   ├── src/main/java/.../
│   │   ├── controller/     # Login, refresh, OAuth2 endpoints
│   │   ├── service/        # Token service, OAuth2 service
│   │   ├── security/       # JWT provider, token revocation
│   │   └── entity/         # User, Role, RefreshToken
│   └── src/main/resources/application.yml
│
├── resource-service/
│   ├── src/main/java/.../
│   │   ├── controller/     # Protected resource endpoints
│   │   ├── security/       # PermissionEvaluator, RBAC config
│   │   └── entity/
│   └── src/main/resources/application.yml
│
├── db-migration/
│   └── src/main/resources/db/migration/
│       └── V*.sql
│
└── docker-compose.yml
```

---

## 🔒 Security Flow

```
Client
  │
  │  POST /auth/login  {username, password}
  ▼
API Gateway ──► Auth Service
                  │  validates credentials
                  │  returns { accessToken, refreshToken }
  ◄──────────────
  │
  │  GET /api/resource   Authorization: Bearer <accessToken>
  ▼
API Gateway
  │  JWT Filter: validates signature + expiry
  │  extracts roles & permissions from claims
  ▼
Resource Service
  │  @PreAuthorize("hasPermission('user', 'read')")
  │  or custom PermissionEvaluator checks JWT claims
  ▼
  Response 200 OK  (or 403 Forbidden)
```

---

## 🌍 Environment Variables

Copy `.env.example` to `.env` and fill in your values before running.

### Database

| Variable | Description | Example |
|---|---|---|
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `rbac_db` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `secret` |

### JWT (`auth-service`)

| Variable | Description | Example |
|---|---|---|
| `JWT_SECRET` | HMAC signing secret (min 256-bit) | `your-256-bit-secret-key` |
| `JWT_ACCESS_TOKEN_EXPIRY` | Access token TTL in seconds | `900` (15 min) |
| `JWT_REFRESH_TOKEN_EXPIRY` | Refresh token TTL in seconds | `604800` (7 days) |

### Google OAuth2 (`auth-service`)

| Variable | Description | Where to get |
|---|---|---|
| `GOOGLE_CLIENT_ID` | OAuth2 client ID | [Google Cloud Console](https://console.cloud.google.com/) |
| `GOOGLE_CLIENT_SECRET` | OAuth2 client secret | Google Cloud Console |
| `GOOGLE_REDIRECT_URI` | Redirect URI after OAuth2 login | `http://localhost:8080/auth/oauth2/google/callback` |

### Service URLs (`api-gateway`)

| Variable | Description | Default |
|---|---|---|
| `AUTH_SERVICE_URL` | Auth service base URL | `http://localhost:8081` |
| `RESOURCE_SERVICE_URL` | Resource service base URL | `http://localhost:8082` |

### Circuit Breaker (`api-gateway`)

| Variable | Description | Default |
|---|---|---|
| `CB_SLIDING_WINDOW_SIZE` | Number of calls in sliding window | `10` |
| `CB_FAILURE_RATE_THRESHOLD` | Failure % to open circuit | `50` |
| `CB_WAIT_DURATION_OPEN` | Time in open state before half-open | `10s` |

---

## 📄 License

MIT
