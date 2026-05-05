# 🔐 RBAC Gateway

A production-ready **microservices backend** with Role-Based Access Control, JWT authentication, dynamic rate limiting, service discovery, and an API Gateway built on Spring Boot 4 + Spring Cloud.

---

## 📐 Architecture Overview

```
                         ┌─────────────────────────┐
                         │      Eureka Server       │
                         │       (Port 8761)        │
                         │   Service Registry &     │
                         │      Discovery UI        │
                         └────────────┬────────────┘
                              ▲       │ fetch registry
                    register  │       │ (every 10s)
                    heartbeat │       ▼
                    ┌─────────┴────────────────────────────────┐
                    │              API Gateway                  │
                    │          (WebMVC · Port 8080)             │
                    │                                          │
                    │  ┌─────────────┐  ┌──────────────────┐  │
                    │  │ JWT Filter  │  │  Rate Limit      │  │
                    │  │  (Global)   │  │  Filter (Redis)  │  │
                    │  └─────────────┘  └──────────────────┘  │
                    │                                          │
                    │  ┌─────────────┐  ┌──────────────────┐  │
                    │  │   Dynamic   │  │  Circuit Breaker │  │
                    │  │   Routes    │  │  (Resilience4j)  │  │
                    │  │  (DB-backed)│  └──────────────────┘  │
                    │  └─────────────┘                        │
                    │                                          │
                    │  ┌──────────────────────────────────┐   │
                    │  │  Spring Cloud LoadBalancer        │   │
                    │  │  RoundRobin + Caffeine cache      │   │
                    │  │  lb://auth-service → IP:port      │   │
                    │  └──────────────────────────────────┘   │
                    └──────────────┬───────────┬──────────────┘
                                   │           │  lb:// resolved
                                   │           │  to live instance
                     ┌─────────────┘           └─────────────┐
                     ▼                                        ▼
          ┌──────────────────┐                  ┌──────────────────────┐
          │   Auth Service   │◄────register─────►   Resource Service   │
          │   (Port 8081)    │                  │   (Port 8082)        │
          │                  │                  │                      │
          │  · Login/Logout  │                  │  · Protected APIs    │
          │  · Refresh Token │                  │  · RBAC Enforcement  │
          │  · Revoke Token  │                  │  · Permission Check  │
          │  · Google OAuth2 │                  │    (resource:action) │
          └────────┬─────────┘                  └──────────┬───────────┘
                   │                                        │
                   └──────────────┬─────────────────────────┘
                                  ▼
                     ┌────────────────────────┐
                     │       PostgreSQL        │
                     │   (Flyway Migration)   │
                     └────────────────────────┘
                                  ▲
                     ┌────────────┴───────────┐
                     │          Redis          │
                     │  · Rate limit buckets  │
                     │  · Config cache        │
                     └────────────────────────┘
```

---

## 🏗️ Modules

| Module             | Port   | Description                                                                              |
|--------------------|--------|------------------------------------------------------------------------------------------|
| `eureka-server`    | `8761` | Spring Cloud Netflix Eureka — Service Registry & Discovery dashboard                    |
| `api-gateway`      | `8080` | Spring Cloud Gateway (WebMVC) — routing, JWT validation, rate limiting, load balancing, Circuit Breaker |
| `auth-service`     | `8081` | Authentication — login, refresh token, token revocation, Google OAuth2                   |
| `resource-service` | `8082` | Protected resources — RBAC enforcement, permission-based access control                  |
| `migration`        | —      | Standalone Flyway module — schema versioning & seeding                                   |
| `gateway-ui`       | `5173` | React admin console — route management, rate limit management                            |

---

## 🛠️ Tech Stack

| Category        | Technology                                                        |
|-----------------|-------------------------------------------------------------------|
| Framework       | Spring Boot `4.0.5`                                              |
| Cloud           | Spring Cloud `Oakwood` (`2025.1.1`)                              |
| Gateway         | Spring Cloud Gateway (WebMVC / Virtual Threads)                  |
| Service Discovery | Spring Cloud Netflix Eureka Server + Client                    |
| Load Balancing  | Spring Cloud LoadBalancer (RoundRobin + Caffeine cache)          |
| Security        | Spring Security, JJWT                                            |
| OAuth2          | Google OAuth2 (Redirect flow + SDK flow)                         |
| Resilience      | Resilience4j (Circuit Breaker + Time Limiter)                    |
| Rate Limiting   | Redis Token Bucket (Lua script, atomic)                          |
| Database        | PostgreSQL + Spring Data JDBC                                     |
| Migration       | Flyway (separate module)                                         |
| Cache           | Redis (`spring-boot-starter-data-redis`) + Caffeine (LB cache)  |
| Java            | Java 21 + Virtual Threads                                        |
| Frontend        | React 19 + Vite + MUI v7                                         |
| Code Quality    | SonarQube `2025.1` + JaCoCo                                      |

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
- [x] API Gateway — JWT validation filter (global, order `-100`)

### Rate Limiting (`api-gateway`)
- [x] **Token Bucket** algorithm — implemented as an atomic Redis Lua script (no race conditions)
- [x] **Per-User isolation** — each `sub` claim gets its own bucket (`rate_limit:user:{username}`)
- [x] **Anonymous fallback** — requests without a valid JWT are bucketed by IP (`rate_limit:ip:{ip}`)
- [x] **DB-backed config** — replenish rate & burst capacity stored in `rate_limit_config` table
- [x] **Per-User override** — individual users can have custom limits that override the global default
- [x] **Redis config cache** — configs cached with TTL 5 min, auto-invalidated on admin update
- [x] **Fail-open / fail-closed** — configurable behavior when Redis is unavailable
- [x] **Rate limit headers** — `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Replenish-Rate`
- [x] **429 JSON response** — structured error body with `timestamp`, `status`, `message`, `path`
- [x] **Excluded paths** — public endpoints (login, register, OAuth2) bypass rate limiting

### Service Discovery & Load Balancing
- [x] **Eureka Server** — standalone Service Registry, dashboard at `http://localhost:8761`
- [x] **Eureka Client** — all three services auto-register on startup, heartbeat every 10s
- [x] **Spring Cloud LoadBalancer** — `lb://service-name` in route URI, resolved at request time
- [x] **RoundRobin algorithm** — even traffic distribution across instances of the same service
- [x] **Caffeine-backed instance cache** — TTL 10s, prevents hitting Eureka on every request
- [x] **Health-aware routing** — only routes to instances with `/actuator/health` = UP
- [x] **Graceful no-instance handling** — throws `IllegalStateException` (caught by Circuit Breaker) instead of silently routing to wrong host

### Infrastructure
- [x] Spring Cloud Gateway — dynamic routes loaded from PostgreSQL (`gateway_routes` table)
- [x] Flyway as a standalone `migration` module
- [x] Circuit Breaker with Resilience4j (`api-gateway`)
- [x] Redis — rate limit state + config cache
- [x] Integration tests with Testcontainers (PostgreSQL + Redis)
- [x] SonarQube `2025.1` — static analysis + code coverage (JaCoCo)

### Admin Console (`gateway-ui`)
- [x] Route management — create, edit, delete, enable/disable, permission assignment
- [x] Rate limit management — view global default, create/edit/delete per-user overrides

---

## 🔑 Permission Model

Permissions follow the `resource:action` convention and are embedded directly in the JWT payload:

```json
{
  "sub": "alice@example.com",
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

## 🔍 Service Discovery — Eureka

### How It Works

All services register themselves with the Eureka Server on startup and send a heartbeat every 10 seconds. The Gateway fetches the full registry every 10 seconds and caches it locally via Caffeine (TTL 10s), so instance resolution at request time is a fast in-memory lookup.

```
Startup flow:
  auth-service     ──register──►
  resource-service ──register──►  Eureka Server (:8761)
  api-gateway      ──register──►

Request flow (lb:// URI):
  Request → DatabaseRouteLocator
              → LoadBalancerClient.choose("auth-service")
                  → CachingServiceInstanceListSupplier (Caffeine TTL=10s)
                      → EurekaDiscoveryClient (fetch every 10s)
              → ServiceInstance → http://10.0.0.5:8081
              → http() proxy handler
```

### Dashboard

The Eureka dashboard is available at **`http://localhost:8761`** and shows:

- All registered instances with their status (`UP` / `DOWN` / `OUT_OF_SERVICE`)
- Instance metadata: IP, port, health-check URL
- Lease info: last renewal timestamp, lease duration

### Route URI Format

Routes stored in the `gateway_routes` table support two URI formats:

| Format | Example | Behavior |
|---|---|---|
| Direct URL | `http://localhost:8081` | Gateway calls the fixed host:port directly |
| Service name | `lb://auth-service` | Gateway resolves via Eureka + LoadBalancer at request time |

To switch an existing route to use service discovery:

```sql
UPDATE gateway_routes
SET uri = 'lb://auth-service'
WHERE id = 'auth-route';
```

### Self-Preservation Mode

Self-preservation is **disabled** in development (configurable via `EUREKA_SELF_PRESERVATION` env var). In production, set it to `true` to prevent Eureka from evicting instances during network partitions.

```yaml
# eureka-server/application.yml
eureka:
  server:
    enable-self-preservation: ${EUREKA_SELF_PRESERVATION:false}
    eviction-interval-timer-in-ms: ${EUREKA_EVICTION_INTERVAL:15000}
```

---

## ⚖️ Load Balancing

### Architecture

Spring Cloud LoadBalancer sits between the Gateway routing layer and the Eureka registry. It uses the **blocking** `LoadBalancerClient` API (compatible with Gateway WebMVC / Virtual Threads — no reactive stack needed).

```
LoadBalancerClient.choose("auth-service")
  │
  └─► RoundRobinLoadBalancer
        │
        └─► CachingServiceInstanceListSupplier  (Caffeine, TTL=10s)
              │
              └─► DiscoveryClientServiceInstanceListSupplier
                    │
                    └─► EurekaDiscoveryClient  (fetches registry every 10s)
```

### Algorithm

**RoundRobin** (default for all services) — requests are distributed sequentially across all available instances. This is the best choice for services with homogeneous request cost (auth token validation, RBAC checks).

To switch a specific service to **Random** load balancing, create a dedicated config class:

```java
@Configuration
@LoadBalancerClient(name = "resource-service", configuration = RandomLBConfig.class)
public class RandomLBConfig {
    @Bean
    public ReactorLoadBalancer<ServiceInstance> randomLoadBalancer(
            Environment env, LoadBalancerClientFactory factory) {
        String name = env.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new RandomLoadBalancer(
            factory.getLazyProvider(name, ServiceInstanceListSupplier.class), name);
    }
}
```

### Caffeine Instance Cache

Without caching, every routed request would call `EurekaDiscoveryClient.getInstances()` — a full registry lookup. The Caffeine-backed `CachingServiceInstanceListSupplier` eliminates this overhead:

| Setting | Value | Rationale |
|---|---|---|
| Cache TTL | `10s` | Matches Eureka fetch interval — cache never holds staler data than the registry |
| Cache capacity | `256` | Handles up to 256 distinct service names |
| Backing store | Caffeine | Heap-only, GC-friendly; no serialization overhead vs Redis |

### No-Instance Handling

When `LoadBalancerClient.choose()` returns `null` (no healthy instances registered), the Gateway throws `IllegalStateException` immediately. This is intentional — the Circuit Breaker catches it, records the failure, and (if the threshold is exceeded) opens the circuit, triggering the `/fallback/auth` or `/fallback/resource` endpoint.

```
No instances available
  → IllegalStateException thrown by DatabaseRouteLocator
  → Resilience4j Circuit Breaker records failure
  → If failure rate > 50%: circuit OPEN
  → Fallback controller returns 503 JSON
```

This is safer than silently routing to a hardcoded hostname, which would produce confusing connection errors.

---

## 🚦 Rate Limiting

### How It Works

Every authenticated request passes through `RateLimitFilter` (order `-99`, runs after JWT validation at `-100`):

```
Request
  │
  ├── JwtAuthenticationFilter (-100) → validates JWT, extracts claims
  │
  └── RateLimitFilter (-99)
        │
        ├── Resolve bucket key
        │     ├── Authenticated → "user:{username}"
        │     └── Anonymous     → "ip:{clientIp}"
        │
        ├── Lookup config (RateLimitConfigService)
        │     ├── Redis cache hit  → return [replenishRate, burstCapacity]
        │     ├── DB: per-user override (username = ?)
        │     ├── DB: global default (username IS NULL)
        │     └── Fallback: application.yml
        │
        ├── Execute Redis Lua script (atomic Token Bucket)
        │     ├── tokens > 0 → decrement, set headers, forward
        │     └── tokens = 0 → 429 Too Many Requests
        │
        └── On Redis failure → fail-open (allow) or fail-closed (block)
```

### Config Priority

```
Per-User Override (rate_limit_config WHERE username = 'alice')
      ↓ not found
Global Default (rate_limit_config WHERE username IS NULL)
      ↓ not found
application.yml fallback (rate-limit.replenish-rate / burst-capacity)
```

### Redis Keys

```
rate_limit:user:alice@example.com.tokens      # token bucket state
rate_limit:user:alice@example.com.timestamp   # last refill timestamp
rate_limit:ip:203.0.113.42.tokens             # anonymous IP bucket
rl_cfg:alice@example.com                      # config cache (TTL 5 min)
rl_cfg:__default__                            # global default cache
```

### Admin API

```
GET    /api/admin/rate-limits              → list all configs
GET    /api/admin/rate-limits/default      → global default
GET    /api/admin/rate-limits/user/{user}  → per-user override
POST   /api/admin/rate-limits              → create override (or update global if username null)
PUT    /api/admin/rate-limits/{id}         → update (partial)
DELETE /api/admin/rate-limits/{id}         → delete per-user override
```

Cache is automatically invalidated after every write operation.

---

## 🚀 Getting Started

### Prerequisites

- Java 21
- Docker & Docker Compose
- Node.js 20+ (for `gateway-ui`)

### Run with Docker Compose

```bash
cp .env.example .env   # fill in your values
docker compose up -d
```

Startup order (enforced by `depends_on` + healthchecks):

```
postgres + redis → migration → eureka-server → auth-service + resource-service → api-gateway
```

Services available after startup:

| Service | URL |
|---|---|
| API Gateway | `http://localhost:8080` |
| Auth Service | `http://localhost:8081` |
| Resource Service | `http://localhost:8082` |
| Eureka Dashboard | `http://localhost:8761` |
| Redis Commander | `http://localhost:8090` |
| SonarQube | `http://localhost:9000` |

### Run services individually

```bash
# 1. Start infrastructure
docker compose up -d postgres redis

# 2. Run database migrations
cd migration && ./mvnw spring-boot:run

# 3. Start Eureka Server (services need to register somewhere)
cd eureka-server && ./mvnw spring-boot:run

# 4. Start auth-service
cd auth-service && ./mvnw spring-boot:run

# 5. Start resource-service
cd resource-service && ./mvnw spring-boot:run

# 6. Start api-gateway
cd api-gateway && ./mvnw spring-boot:run

# 7. Start admin UI (optional)
cd gateway-ui && npm install && npm run dev
```

> **Tip:** When running locally, services use `EUREKA_URL=http://localhost:8761/eureka/` (the default). No extra env vars needed in dev.

### Run tests

```bash
# Integration tests (requires Docker for Testcontainers)
cd api-gateway && ./mvnw test
```

Testcontainers will spin up PostgreSQL and Redis automatically. No local infra needed.

---

## 📡 API Endpoints

### Auth Service (via Gateway)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/login` | Authenticate and receive tokens |
| `POST` | `/api/auth/register` | Register a new account |
| `POST` | `/api/auth/refresh` | Exchange refresh token for new access token |
| `POST` | `/api/auth/logout` | Revoke current refresh token |
| `POST` | `/api/auth/logout-all` | Revoke all refresh tokens for the user |
| `GET` | `/api/auth/google` | Initiate Google OAuth2 redirect flow |
| `POST` | `/api/auth/google/sdk` | Exchange Google ID token (SDK flow) |

### Resource Service (via Gateway)

> All endpoints require a valid `Authorization: Bearer <accessToken>` header.

| Method | Path | Required Permission |
|---|---|---|
| `GET` | `/api/resources/products` | `products:READ` |
| *(add your own routes)* | | |

### Gateway Admin API

> Requires `ROLE_ADMIN`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/admin/routes` | List all routes |
| `POST` | `/api/admin/routes` | Create route |
| `PUT` | `/api/admin/routes/{id}` | Update route |
| `DELETE` | `/api/admin/routes/{id}` | Delete route |
| `PATCH` | `/api/admin/routes/{id}/toggle` | Enable / disable route |
| `GET` | `/api/admin/rate-limits` | List all rate limit configs |
| `GET` | `/api/admin/rate-limits/default` | Get global default |
| `GET` | `/api/admin/rate-limits/user/{username}` | Get per-user override |
| `POST` | `/api/admin/rate-limits` | Create per-user override |
| `PUT` | `/api/admin/rate-limits/{id}` | Update config |
| `DELETE` | `/api/admin/rate-limits/{id}` | Delete per-user override |

---

## ⚙️ Configuration

### `api-gateway/application.yml`

```yaml
spring:
  cloud:
    eureka:
      client:
        service-url:
          defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
        fetch-registry: true
        registry-fetch-interval-seconds: 10
      instance:
        prefer-ip-address: true
        lease-renewal-interval-in-seconds: 10
        lease-expiration-duration-in-seconds: 30

    loadbalancer:
      cache:
        enabled: true
        ttl: 10s          # sync with registry-fetch-interval-seconds
        capacity: 256
      health-check:
        interval: 10s
        refetch-instances-interval: 25s

jwt:
  secret: ${JWT_SECRET}

rate-limit:
  replenish-rate: 20
  burst-capacity: 40
  allow-on-redis-failure: true
  excluded-paths:
    - /api/auth/login
    - /api/auth/register
    - /api/auth/refresh
    - /api/auth/logout
    - /oauth2/**
    - /actuator/**

resilience4j:
  circuitbreaker:
    instances:
      authServiceCB:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
      resourceServiceCB:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 15s
```

### `eureka-server/application.yml`

```yaml
server:
  port: 8761

eureka:
  instance:
    hostname: ${EUREKA_HOSTNAME:localhost}
  client:
    register-with-eureka: false   # standalone mode — does not register itself
    fetch-registry: false
  server:
    enable-self-preservation: ${EUREKA_SELF_PRESERVATION:false}   # disable in dev
    eviction-interval-timer-in-ms: ${EUREKA_EVICTION_INTERVAL:15000}
```

### `auth-service` / `resource-service` — Eureka registration

Both services share the same client config pattern:

```yaml
spring:
  cloud:
    eureka:
      client:
        service-url:
          defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
        fetch-registry: true
        registry-fetch-interval-seconds: 10
      instance:
        prefer-ip-address: true
        lease-renewal-interval-in-seconds: 10
        lease-expiration-duration-in-seconds: 30
        health-check-url-path: /actuator/health
```

---

## 🗂️ Database Schema

Flyway migrations in `migration/src/main/resources/db/auth/`:

| Version | Description |
|---|---|
| `V1.0` | Init — users, roles, user_roles |
| `V1.2` | RBAC tables — permissions, role_permissions |
| `V1.4` | Gateway routes (`gateway_routes`) |
| `V1.5` | Route permissions (`route_permissions`) |
| `V1.6` | Rate limit config (`rate_limit_config`) |

Run migrations:

```bash
cd migration && ./mvnw flyway:migrate
```

### `gateway_routes` table — URI formats

```sql
-- Direct URL (no service discovery)
INSERT INTO gateway_routes (id, uri, predicates)
VALUES ('auth-route', 'http://localhost:8081', '["Path=/api/auth/**"]');

-- Service name via Eureka (recommended for production)
INSERT INTO gateway_routes (id, uri, predicates)
VALUES ('auth-route', 'lb://auth-service', '["Path=/api/auth/**"]');
```

### `rate_limit_config` table

```sql
CREATE TABLE rate_limit_config (
    id              BIGSERIAL    PRIMARY KEY,
    username        VARCHAR(255) NULL UNIQUE,  -- NULL = global default
    replenish_rate  INT          NOT NULL,
    burst_capacity  INT          NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    description     VARCHAR(500) NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

`username = NULL` is the global default row (one per system, cannot be deleted).

---

## 🧱 Project Structure

```
rbac-gateway/
├── eureka-server/
│   └── src/main/java/.../
│       └── EurekaServerApplication.java   # @EnableEurekaServer
│   └── src/main/resources/
│       └── application.yml
│   └── Dockerfile
│
├── api-gateway/
│   └── src/main/java/.../
│       ├── filter/              # JwtAuthenticationFilter, RateLimitFilter
│       ├── admin/               # ratelimit/, route/ — admin APIs
│       ├── config/              # GatewayConfig, LoadBalancerConfig,
│       │                        #   GatewayCircuitBreakerConfig, GatewayProperties
│       ├── route/               # DatabaseRouteLocator (lb:// resolution)
│       ├── controller/          # FallbackController
│       └── validator/           # JwtValidator, RbacPermissionChecker
│   └── src/main/resources/
│       ├── application.yml
│       ├── application-perf.yml
│       └── scripts/
│           └── rate_limit.lua
│
├── auth-service/
│   └── src/main/java/.../
│       ├── controller/          # Login, refresh, OAuth2 endpoints
│       ├── service/             # TokenService, OAuth2Service
│       ├── security/            # JWT provider, token revocation
│       └── entity/              # User, Role, RefreshToken
│
├── resource-service/
│   └── src/main/java/.../
│       ├── controller/          # Protected resource endpoints
│       └── security/            # PermissionEvaluator, RBAC config
│
├── migration/
│   └── src/main/resources/db/auth/
│       └── V*.sql
│
├── gateway-ui/                  # React + Vite + MUI admin console
│   └── src/
│       ├── api/                 # routeApi.js, rateLimitApi.js
│       ├── hooks/               # useRoutes.js, useRateLimits.js
│       ├── pages/               # RoutesPage.jsx, RateLimitPage.jsx
│       └── components/
│           ├── routes/          # RouteTable, RouteFormDialog, ...
│           ├── ratelimit/       # RateLimitTable, RateLimitFormDialog, ...
│           └── layout/          # Sidebar
│
├── docker/
│   └── redis/redis.conf
├── docker-compose.yml
├── sonar-project.properties
└── .env.example
```

---

## 🔒 Security Flow

```
Client
  │
  │  POST /api/auth/login  { username, password }
  ▼
API Gateway ──► Auth Service  (resolved via lb://auth-service → Eureka → LoadBalancer)
                  │  validates credentials
                  │  returns { accessToken, refreshToken }
  ◄──────────────
  │
  │  GET /api/resources/products   Authorization: Bearer <accessToken>
  ▼
API Gateway
  │  JwtAuthenticationFilter (-100): validate signature + expiry
  │  RateLimitFilter (-99): Token Bucket check via Redis
  │    ├── allowed → forward + add X-RateLimit-* headers
  │    └── blocked → 429 Too Many Requests (JSON)
  │  LoadBalancerClient.choose("resource-service")
  │    └── RoundRobin → ServiceInstance from Caffeine cache (Eureka-backed)
  ▼
Resource Service
  │  @PreAuthorize / PermissionEvaluator checks JWT claims
  │  e.g. requires "products:READ"
  ▼
  Response 200 OK  (or 403 Forbidden)
```

---

## 🌍 Environment Variables

Copy `.env.example` to `.env` before running.

### Database

| Variable      | Description          | Default                                      |
|---------------|----------------------|----------------------------------------------|
| `DB_URL`      | JDBC PostgreSQL URL  | `jdbc:postgresql://localhost:5432/postgres`  |
| `DB_USERNAME` | Database username    | `postgres`                                   |
| `DB_PASSWORD` | Database password    | —                                            |

### Redis

| Variable         | Description             | Default     |
|------------------|-------------------------|-------------|
| `REDIS_HOST`     | Redis hostname          | `localhost` |
| `REDIS_PORT`     | Redis port              | `6379`      |
| `REDIS_PASSWORD` | Redis password (if any) | —           |

### JWT

| Variable     | Description                               | Example                                     |
|--------------|-------------------------------------------|---------------------------------------------|
| `JWT_SECRET` | HMAC signing secret (Base64, min 256-bit) | *(generate with `openssl rand -base64 32`)* |

### Google OAuth2

| Variable               | Description          | Where to get                                              |
|------------------------|----------------------|-----------------------------------------------------------|
| `GOOGLE_CLIENT_ID`     | OAuth2 client ID     | [Google Cloud Console](https://console.cloud.google.com/) |
| `GOOGLE_CLIENT_SECRET` | OAuth2 client secret | Google Cloud Console                                      |

### Eureka

| Variable                  | Description                                                  | Default                          |
|---------------------------|--------------------------------------------------------------|----------------------------------|
| `EUREKA_URL`              | Eureka server URL (used by all client services)              | `http://localhost:8761/eureka/`  |
| `EUREKA_HOSTNAME`         | Hostname eureka-server advertises to clients (Docker: container name) | `localhost`             |
| `EUREKA_SELF_PRESERVATION`| Disable eviction protection (`false` for dev, `true` for prod) | `false`                        |
| `EUREKA_EVICTION_INTERVAL`| How often (ms) Eureka evicts dead instances                  | `15000`                          |

### Internal

| Variable          | Description                                |
|-------------------|--------------------------------------------|
| `INTERNAL_SECRET` | Shared secret for service-to-service calls |

---

## 🧪 Testing

Integration tests live in `api-gateway/src/test/` and use **Testcontainers** — no manual infra setup needed.

| Test class                      | Coverage                                                                      |
|---------------------------------|-------------------------------------------------------------------------------|
| `GatewayIntegrationTest`        | Route forwarding, JWT validation, 401/403 flows                               |
| `CircuitBreakerIntegrationTest` | Open/half-open/closed states, fallback responses                              |
| `RateLimitIntegrationTest`      | Token bucket behavior, per-user isolation, refill, excluded paths, 429 format |

```bash
cd api-gateway
./mvnw test
```

Testcontainers pulls `postgres:16-alpine` and `redis:7-alpine` automatically on first run.

---

## ⚡ Performance Testing

### Stack

| Tool                                 | Role                                                    |
|--------------------------------------|---------------------------------------------------------|
| [JMeter](https://jmeter.apache.org/) | Load generator — scripted scenarios, thresholds, checks |
| Docker Compose                       | Spin up full stack với `perf` profile                   |

### Kịch bản test

[RBAC Gateway test plan](<performance-test/RBAC%20Gateway.jmx>)

---
### Kết quả đo được (khi service đã chạy ổn định)

> Môi trường: Docker Compose trên Windows 11 / WSL2, mỗi service giới hạn **2 CPU + 1 GB RAM**, api gateway giới hạn **4 CPU + 3 GB RAM**.

#### Trực tiếp vs qua Gateway tải cao (500 VUs, 200K Requests)

| Metric      | Direct `:8081, :8082` | Via Gateway `:8080` | Overhead |
|-------------|-----------------------|---------------------|----------|
| Throughput  | **3082 req/s**        | **3080 req/s**      | ~0.001%  |
| p50 latency | 59 ms                 | 154 ms              | +95 ms   |
| p95 latency | 562 ms                | 251 ms              | -311 ms  |
| p99 latency | 874 ms                | 309 ms              | -565 ms  |
| Error rate  | 0%                    | 0%                  | —        |

#### Trực tiếp vs qua Gateway tải thấp (500 VUs, 1000 Requests)

| Metric      | Direct `:8081, :8082` | Via Gateway `:8080` | Overhead |
|-------------|-----------------------|---------------------|----------|
| Throughput  | **938 req/s**         | **931 req/s**       | ~0.1%    |
| p50 latency | 4 ms                  | 6 ms                | +2 ms    |
| p95 latency | 16 ms                 | 17 ms               | +1 ms    |
| p99 latency | 38 ms                 | 39 ms               | +1 ms    |
| Error rate  | 0%                    | 0%                  | —        |

> Gateway overhead ~1–2 ms/request: JWT validation + Redis rate limit (Lua) + route cache lookup + Eureka LB resolve (Caffeine hit) + header mutation + Circuit Breaker state check.

### Các vấn đề đã phát hiện và xử lý

| Vấn đề                          | Nguyên nhân gốc                                          | Fix                                       |
|---------------------------------|----------------------------------------------------------|-------------------------------------------|
| Redis command timed out (lần 1) | `appendonly yes` → fsync blocking dưới tải               | Tắt AOF + RDB snapshot trong `redis.conf` |
| Redis command timed out (lần 1) | Redis `timeout: 1s` quá thấp                             | Tăng lên `3s` trong `application.yml`     |
| Redis command timed out (lần 1) | `KEYS *` blocking trong `invalidateAllConfigCache`       | Thay bằng `SCAN` với `count(100)`         |
| Redis command timed out (lần 2) | `commons-pool2` thiếu → Lettuce dùng 1 shared connection | Thêm dependency + `pool.enabled: true`    |
| Redis command timed out (lần 2) | Lua script gọi 4 Redis commands riêng lẻ                 | Gộp thành `MGET` + `MSET` → 3 round-trips |

---

## 📊 Code Quality — SonarQube

The project is configured with **SonarQube 2025.1** (Community edition) and **JaCoCo** for code coverage across all three service modules.

### Infrastructure

SonarQube runs as a Docker service alongside the rest of the stack:

```bash
docker compose up -d sonarqube
```

UI available at **`http://localhost:9000`** (default credentials: `admin` / `admin`).

> **First-time setup:** SonarQube stores its data in the `sonarqube` PostgreSQL database. Create it once before starting:
> ```bash
> docker exec -it rbac-postgres psql -U postgres -c "CREATE DATABASE sonarqube;"
> ```
> On Linux hosts, also run: `sudo sysctl -w vm.max_map_count=524288`

### Running Analysis

```bash
# 1. Build + run tests + generate JaCoCo coverage reports
./mvnw clean verify

# 2. Push results to SonarQube (replace with your token)
./mvnw sonar:sonar -Dsonar.token=<YOUR_TOKEN>
```

Or in a single command:

```bash
./mvnw clean verify sonar:sonar -Dsonar.token=<YOUR_TOKEN>
```

### Generating a Token

1. Go to `http://localhost:9000` → **My Account → Security**
2. Under **Generate Tokens**, enter a name and click **Generate**
3. Copy the token — it's shown only once

### What Gets Analyzed

| Module | Sources | Coverage report |
|---|---|---|
| `api-gateway` | `src/main/java` | `target/site/jacoco/jacoco.xml` |
| `auth-service` | `src/main/java` | `target/site/jacoco/jacoco.xml` |
| `resource-service` | `src/main/java` | `target/site/jacoco/jacoco.xml` |

The following are excluded from analysis and coverage metrics: `*Application.java`, `config/`, `entity/`, `dto/`, `exception/`, `migration/`.

### Configuration Files

| File | Purpose |
|---|---|
| `sonar-project.properties` | Multi-module scanner config (sources, tests, coverage paths, exclusions) |
| `pom.xml` (root) | `sonar-maven-plugin` + `jacoco-maven-plugin` in `pluginManagement` |
| Each module `pom.xml` | Activates JaCoCo; `maven-surefire-plugin` uses `@{argLine}` so the JaCoCo agent injects correctly |

---

## 📄 License

MIT
