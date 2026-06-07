# Auth Service

Microservice xác thực và phân quyền trung tâm của hệ thống **rbac-gateway**. Cung cấp JWT (RS256), OIDC Discovery, RBAC, MFA (TOTP), SSO và quản lý người dùng.

---

## Mục lục

- [Tổng quan kiến trúc](#tổng-quan-kiến-trúc)
- [Tech stack](#tech-stack)
- [Cấu hình](#cấu-hình)
- [Chạy local](#chạy-local)
- [API Reference](#api-reference)
  - [Authentication](#authentication)
  - [MFA](#mfa)
  - [SSO](#sso)
  - [OIDC / OAuth2](#oidc--oauth2)
  - [Admin — Users](#admin--users)
  - [Admin — Roles](#admin--roles)
  - [Admin — Permissions](#admin--permissions)
  - [Admin — SSO Providers](#admin--sso-providers)
  - [Internal](#internal)
- [Login flows](#login-flows)
  - [Standard login (không có MFA)](#standard-login-không-có-mfa)
  - [Login với MFA](#login-với-mfa)
  - [Google OAuth2](#google-oauth2)
  - [SSO (OIDC redirect)](#sso-oidc-redirect)
  - [OIDC password grant](#oidc-password-grant)
- [MFA — hướng dẫn setup](#mfa--hướng-dẫn-setup)
- [SSO — hướng dẫn cấu hình provider](#sso--hướng-dẫn-cấu-hình-provider)
- [RBAC model](#rbac-model)
- [Kafka events](#kafka-events)
- [Database schema](#database-schema)
- [Bảo mật](#bảo-mật)

---

## Tổng quan kiến trúc

```
                        ┌─────────────────────────────────────────────┐
                        │              auth-service :8081              │
                        │                                             │
  Browser / Client ────▶│  POST /api/auth/login                       │
                        │  POST /api/auth/mfa/verify                  │
  api-gateway ─────────▶│  GET  /api/auth/validate                    │──▶ PostgreSQL
                        │  GET  /.well-known/openid-configuration     │
  Kubernetes ──────────▶│  GET  /oauth2/jwks                          │──▶ Redis
                        │                                             │
  Internal services ───▶│  GET  /internal/users/{username}            │──▶ Kafka
                        │  POST /oauth2/token (client_credentials)    │
                        └─────────────────────────────────────────────┘
```

Auth-service đóng vai trò **Identity Provider (IdP)** cho toàn bộ hệ thống:

- **api-gateway** xác thực JWT tại edge bằng public key lấy từ `/oauth2/jwks` — không gọi lại auth-service trên mỗi request.
- **Kubernetes** có thể dùng auth-service làm OIDC provider cho service-account và RBAC webhook.
- **SSO providers** (Okta, Azure AD, Keycloak, ...) được cấu hình động qua Admin API, không cần restart.

---

## Tech stack

| Thành phần        | Thư viện / Version                                   |
|-------------------|------------------------------------------------------|
| Runtime           | Java 21, Spring Boot 4.0.x                           |
| Security          | Spring Security 6, spring-boot-starter-oauth2-client |
| JWT               | JJWT 0.13 (RS256 — asymmetric)                       |
| MFA               | `dev.samstevens.totp:totp-spring-boot-starter` 1.7.1 |
| QR Code           | `com.google.zxing` 3.5.3                             |
| Database          | PostgreSQL + Spring Data JPA + Hibernate             |
| Cache / Session   | Redis (Lettuce), Spring Cache                        |
| Messaging         | Apache Kafka (Spring Kafka)                          |
| Service discovery | Netflix Eureka                                       |
| Observability     | Micrometer, OpenTelemetry, Prometheus                |

---

## Cấu hình

Tất cả biến môi trường cần thiết:

```bash
# Database
AUTH_DB_URL=jdbc:postgresql://localhost:5432/rbac_auth
DB_USERNAME=postgres
DB_PASSWORD=secret

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=           # bỏ trống nếu không có password

# Google OAuth2
GOOGLE_CLIENT_ID=xxx.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-xxx

# OIDC server
OIDC_ISSUER_URI=https://auth.your-domain.com   # phải accessible từ k8s
OIDC_RSA_KEY_PATH=                              # trống = generate key tạm (chỉ dùng dev)

# JWT
# jwt.secret chỉ dùng để backward-compat, signing thực tế dùng RSA key
# jwt.expiration: thời gian sống access token (ms), mặc định 86400000 (24h)

# MFA
MFA_ISSUER=YourAppName   # tên hiện trong Google Authenticator

# Internal service auth
INTERNAL_SECRET=changeme-internal-secret

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:29092

# Eureka
EUREKA_URL=http://localhost:8761/eureka/
```

> **Production:** mount RSA private key qua Kubernetes Secret và set `OIDC_RSA_KEY_PATH=/etc/auth/keys/rsa_private.pem`.

---

## Chạy local

**Yêu cầu:** Docker, Java 21, Maven.

```bash
# 1. Khởi động infrastructure
docker compose up -d postgres redis kafka zookeeper

# 2. Chạy Flyway migration
cd migration && mvn spring-boot:run

# 3. Chạy auth-service
cd auth-service
cp .env.example .env   # điền biến môi trường
mvn spring-boot:run
```

Kiểm tra health:

```bash
curl http://localhost:8081/actuator/health
# {"status":"UP",...}

curl http://localhost:8081/.well-known/openid-configuration
# {"issuer":"http://localhost:8081","jwks_uri":"http://localhost:8081/oauth2/jwks",...}
```

---

## API Reference

Base URL: `http://localhost:8081`

### Authentication

#### POST `/api/auth/register`

Tạo tài khoản mới.

// Request
```json
{
  "username": "alice@example.com",
  "password": "P@ssw0rd!",
  "roles": ["ROLE_USER"]
}

```

// Response 201
```
"User created"
```

---

#### POST `/api/auth/login`

Đăng nhập bằng username/password.

// Request
```json
{
  "username": "alice@example.com",
  "password": "P@ssw0rd!"
}
```

**Trường hợp A — MFA không bật** → HTTP 200:

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Trường hợp B — MFA bật** → HTTP 202:

```json
{
  "mfaSessionToken": "7f3e2a1b-...",
  "message": "Vui lòng nhập mã xác thực MFA để tiếp tục"
}
```

---

#### POST `/api/auth/mfa/verify`

Bước 2 của MFA login — gửi TOTP code (6 chữ số) hoặc backup code (8 ký tự).

// Request
```json
{
  "mfaSessionToken": "7f3e2a1b-...",
  "code": "123456"
}
```

// Response 200
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "refreshToken": "550e8400-..."
}
```

MFA session token hết hạn sau **5 phút** (lưu trên Redis).

---

#### POST `/api/auth/refresh`

Rotate refresh token — đổi refresh token cũ lấy cặp token mới.

// Request
```json
{ "refreshToken": "550e8400-..." }
```

// Response 200
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "new-uuid-..."
}
```
---

#### POST `/api/auth/logout`

Thu hồi refresh token.

```json
{ "refreshToken": "550e8400-..." }
```
// Response 204

---

#### POST `/api/auth/logout-all`

Thu hồi tất cả refresh token của user (force logout toàn thiết bị).

```
Header: X-User-Name: alice@example.com
// Response 204
```

---

#### GET `/api/auth/validate`

Validate access token và trả về claims (username, roles, permissions). Được api-gateway gọi.

```
Header: Authorization: Bearer <access_token>

// Response 200
{
  "username": "alice@example.com",
  "roles": ["ROLE_USER"],
  "permissions": ["order:read", "product:read"]
}
```

---

#### POST `/api/auth/google`

Đăng nhập bằng Google ID Token (từ Google Sign-In SDK phía client).

// Request
```json
{ "idToken": "eyJhbGciOiJSUzI1NiJ9..." }
```

// Response 200
```json
{
  "accessToken": "...",
  "refreshToken": "..."
}
```

---

### MFA

> Tất cả endpoint `/api/mfa/**` yêu cầu `Authorization: Bearer <access_token>` hợp lệ.

#### POST `/api/mfa/setup`

Khởi tạo MFA — tạo TOTP secret và QR code. Chưa bật MFA, user phải xác nhận bằng `/api/mfa/enable`.

// Response 200
```json
{
  "secret": "JBSWY3DPEHPK3PXP",
  "otpauthUri": "otpauth://totp/RbacGateway:alice%40example.com?secret=...&issuer=RbacGateway",
  "qrCodeDataUrl": "data:image/png;base64,iVBOR..."
}
```

Dùng `qrCodeDataUrl` để render thẳng vào `<img>` tag trên frontend.

---

#### POST `/api/mfa/enable?code=123456`

Bật MFA bằng cách xác nhận TOTP code đầu tiên. Trả về 10 backup codes — **chỉ hiển thị một lần**.

// Response 200
```json
{
  "backupCodes": [
    "A3KP9QR2", "BX7MN4T6", "CZ2WL8S5",
    "D6HQ3YU1", "E9GF7VJ4", "F1RM5KE8",
    "G4DN2XC7", "H8TS6PO3", "J7LB4WA9", "K3VE1QN5"
  ],
  "message": "MFA đã được kích hoạt. Lưu các backup codes ở nơi an toàn!"
}
```

---

#### POST `/api/mfa/disable?code=123456`

Tắt MFA. Yêu cầu TOTP code hiện tại để xác nhận.

```
Response 204
```

---

#### POST `/api/mfa/backup-codes/regenerate?code=123456`

Tạo lại 10 backup codes mới, vô hiệu hóa toàn bộ codes cũ. Yêu cầu TOTP code hiện tại.

// Response 200
```json
{
  "backupCodes": ["...", "..."],
  "message": "Backup codes mới đã được tạo. Các codes cũ đã bị vô hiệu hóa."
}
```

---

### SSO

#### GET `/api/auth/sso/providers`

Endpoint **public** — trả về danh sách SSO providers đang bật. Frontend dùng để render nút "Login with Okta", "Login with Azure AD", v.v.

// Response 200
```json
[
  {
    "providerId": "okta",
    "displayName": "Okta",
    "type": "oidc",
    "loginUrl": "/oauth2/authorization/okta"
  }
]
```

---

### OIDC / OAuth2

Auth-service hoạt động như một OIDC-compliant Identity Provider.

| Endpoint                                      | Mô tả                                       |
|-----------------------------------------------|---------------------------------------------|
| `GET /.well-known/openid-configuration`       | OIDC Discovery Document (RFC 8414)          |
| `GET /.well-known/oauth-authorization-server` | Alias OAuth2 metadata                       |
| `GET /oauth2/jwks`                            | JSON Web Key Set — public key để verify JWT |
| `POST /oauth2/token`                          | Token endpoint (form-encoded)               |
| `GET /oauth2/userinfo`                        | OIDC UserInfo endpoint                      |
| `GET /oauth2/authorize`                       | Authorization endpoint (redirect flow)      |

#### POST `/oauth2/token`

Content-Type: `application/x-www-form-urlencoded`

**grant_type=password**

```
grant_type=password&username=alice@example.com&password=P@ssw0rd!&client_id=my-app

// Response
{
  "access_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 86400,
  "refresh_token": "uuid-...",
  "id_token": "eyJ..."
}
```

**grant_type=refresh_token**

```
grant_type=refresh_token&refresh_token=uuid-...
```

**grant_type=client_credentials**

```
grant_type=client_credentials&client_id=k8s-service-account&client_secret=secret

// Response (không có refresh_token hay id_token)
{
  "access_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 86400
}
```

---

### Admin — Users

> Yêu cầu `ROLE_ADMIN`. Header `X-User-Roles` được set bởi api-gateway.

| Method   | Path                                | Mô tả                  |
|----------|-------------------------------------|------------------------|
| `GET`    | `/api/auth/admin/users`             | Danh sách tất cả users |
| `GET`    | `/api/auth/admin/users/{id}`        | Chi tiết user          |
| `POST`   | `/api/auth/admin/users`             | Tạo user mới           |
| `PUT`    | `/api/auth/admin/users/{id}`        | Cập nhật user          |
| `PATCH`  | `/api/auth/admin/users/{id}/toggle` | Bật/tắt tài khoản      |
| `DELETE` | `/api/auth/admin/users/{id}`        | Xóa user               |

---

### Admin — Roles

| Method   | Path                                     | Mô tả                    |
|----------|------------------------------------------|--------------------------|
| `GET`    | `/api/auth/admin/roles`                  | Danh sách roles          |
| `GET`    | `/api/auth/admin/roles/{id}`             | Chi tiết role            |
| `POST`   | `/api/auth/admin/roles`                  | Tạo role mới             |
| `PUT`    | `/api/auth/admin/roles/{id}`             | Cập nhật role            |
| `PATCH`  | `/api/auth/admin/roles/{id}/permissions` | Gán permissions cho role |
| `DELETE` | `/api/auth/admin/roles/{id}`             | Xóa role                 |

---

### Admin — Permissions

| Method   | Path                               | Mô tả                 |
|----------|------------------------------------|-----------------------|
| `GET`    | `/api/auth/admin/permissions`      | Danh sách permissions |
| `POST`   | `/api/auth/admin/permissions`      | Tạo permission        |
| `PUT`    | `/api/auth/admin/permissions/{id}` | Cập nhật              |
| `DELETE` | `/api/auth/admin/permissions/{id}` | Xóa                   |

Permission format: `resource:action`, ví dụ `order:read`, `product:write`.

---

### Admin — SSO Providers

> Yêu cầu `ROLE_ADMIN`.

| Method   | Path                               | Mô tả               |
|----------|------------------------------------|---------------------|
| `GET`    | `/api/auth/admin/sso`              | Danh sách providers |
| `POST`   | `/api/auth/admin/sso`              | Thêm provider mới   |
| `PUT`    | `/api/auth/admin/sso/{providerId}` | Cập nhật provider   |
| `DELETE` | `/api/auth/admin/sso/{providerId}` | Tắt provider        |

Ví dụ tạo Okta provider:

// POST /api/auth/admin/sso
```json
{
  "providerId": "okta",
  "displayName": "Okta",
  "type": "oidc",
  "issuerUri": "https://dev-12345.okta.com/oauth2/default",
  "clientId": "0oa...",
  "clientSecret": "xxx",
  "scopes": "openid,profile,email",
  "defaultRoles": "ROLE_USER",
  "enabled": true
}
```

---

### Internal

Dành cho service-to-service. Header `X-Internal-Secret` phải khớp với `app.internal.secret`.

| Method | Path                         | Mô tả                                          |
|--------|------------------------------|------------------------------------------------|
| `GET`  | `/internal/users/{username}` | Lấy thông tin user (dùng bởi resource-service) |

---

## Login flows

### Standard login (không có MFA)

```
Client                        auth-service                    PostgreSQL
  │                               │                               │
  │  POST /api/auth/login         │                               │
  │  { username, password }       │                               │
  │ ─────────────────────────────▶│                               │
  │                               │  SELECT user WHERE username   │
  │                               │ ─────────────────────────────▶│
  │                               │◀─────────────────────────────│
  │                               │  verify BCrypt password       │
  │                               │  generate RS256 JWT           │
  │                               │  create refresh token         │
  │◀─────────────────────────────│                               │
  │  200 { accessToken,           │                               │
  │        refreshToken }         │                               │
```

---

### Login với MFA

```
Client              auth-service                 Redis
  │                      │                         │
  │  POST /login          │                         │
  │ ─────────────────────▶│                         │
  │                      │  mfaEnabled = true       │
  │                      │  createMfaSession(user)  │
  │                      │ ────────────────────────▶│ SET mfa:session:{token} = username  TTL=5m
  │◀─────────────────────│                         │
  │  202 { mfaSessionToken }                        │
  │                      │                         │
  │  POST /mfa/verify     │                         │
  │  { mfaSessionToken,   │                         │
  │    code: "123456" }   │                         │
  │ ─────────────────────▶│                         │
  │                      │  consumeMfaSession()     │
  │                      │ ────────────────────────▶│ GET + DEL mfa:session:{token}
  │                      │  verifyTotpCode()        │
  │                      │  generate RS256 JWT      │
  │◀─────────────────────│                         │
  │  200 { accessToken,   │                         │
  │        refreshToken } │                         │
```

---

### Google OAuth2

**Redirect flow** (browser):

```
Browser → GET /oauth2/authorization/google
        → redirect to Google consent screen
        → Google callback /login/oauth2/code/google
        → OAuth2LoginSuccessHandler: findOrCreateUser → issue JWT
        → response body: { accessToken, refreshToken }
```

**SDK flow** (mobile/SPA — client lấy idToken từ Google SDK):

```
POST /api/auth/google
{ "idToken": "<google-id-token>" }
→ verify tại https://oauth2.googleapis.com/tokeninfo
→ findOrCreateUser → issue JWT
→ 200 { accessToken, refreshToken }
```

---

### SSO (OIDC redirect)

```
Browser → GET /api/auth/sso/providers                    # lấy danh sách providers
        ← [{ providerId: "okta", loginUrl: "/oauth2/authorization/okta" }]

Browser → GET /oauth2/authorization/okta
        → redirect đến Okta
        → Okta callback /login/oauth2/code/okta
        → OAuth2LoginSuccessHandler:
            findOrCreate user (match bằng ssoProviderId + ssoSubject)
            gán roles từ SsoProvider.defaultRoles
            issue JWT
        → response body: { accessToken, refreshToken }
```

User mới được **tự động provision** lần đầu đăng nhập qua SSO.

---

### OIDC password grant

Dành cho Kubernetes và các service cần token theo chuẩn OIDC:

```bash
curl -X POST http://localhost:8081/oauth2/token \
  -d "grant_type=password&username=alice@example.com&password=P@ssw0rd!" \
  -d "client_id=my-k8s-app"
```

---

## MFA — hướng dẫn setup

**Bước 1 — Khởi tạo:**

```bash
curl -X POST http://localhost:8081/api/mfa/setup \
  -H "Authorization: Bearer <access_token>"
```

Lấy `qrCodeDataUrl` và render vào `<img>` để user quét bằng Google Authenticator / Authy.

**Bước 2 — Xác nhận và bật:**

```bash
curl -X POST "http://localhost:8081/api/mfa/enable?code=123456" \
  -H "Authorization: Bearer <access_token>"
```

Lưu lại 10 backup codes trả về — **không thể lấy lại**.

**Bước 3 — Đăng nhập:**

```bash
# Login → nhận mfaSessionToken
curl -X POST http://localhost:8081/api/auth/login \
  -d '{"username":"alice","password":"pass"}' -H "Content-Type: application/json"
# → 202 { "mfaSessionToken": "xxx" }

# Verify TOTP
curl -X POST http://localhost:8081/api/auth/mfa/verify \
  -d '{"mfaSessionToken":"xxx","code":"654321"}' -H "Content-Type: application/json"
# → 200 { "accessToken": "...", "refreshToken": "..." }
```

---

## SSO — hướng dẫn cấu hình provider

### Azure Active Directory

POST /api/auth/admin/sso
```json
{
  "providerId": "azure-ad",
  "displayName": "Microsoft Azure AD",
  "type": "oidc",
  "issuerUri": "https://login.microsoftonline.com/{tenant-id}/v2.0",
  "clientId": "<azure-app-client-id>",
  "clientSecret": "<azure-app-client-secret>",
  "scopes": "openid,profile,email",
  "defaultRoles": "ROLE_USER",
  "enabled": true
}
```

**Redirect URI** cần đăng ký trong Azure App Registration:
```
https://auth.your-domain.com/login/oauth2/code/azure-ad
```

### Okta

```json
{
  "providerId": "okta",
  "displayName": "Okta",
  "issuerUri": "https://dev-XXXXX.okta.com/oauth2/default",
  "clientId": "0oa...",
  "clientSecret": "xxx",
  "scopes": "openid,profile,email",
  "enabled": true
}
```

**Redirect URI** trong Okta:
```
https://auth.your-domain.com/login/oauth2/code/okta
```

---

## RBAC model

```
User ──▶ Role ──▶ Permission
                    │
                    └── resource: "order"
                        action:   "read"
                    → permission string: "order:read"
```

Permission string được nhúng vào JWT claim `permissions` khi generate token. api-gateway đọc claim này để kiểm tra quyền truy cập từng route mà không cần gọi lại auth-service.

Khi thay đổi permissions/roles, auth-service publish event lên Kafka (`rbac.permission.events`, `rbac.resource.events`), api-gateway nhận và invalidate cache.

---

## Kafka events

| Topic                    | Khi nào                         | Payload           |
|--------------------------|---------------------------------|-------------------|
| `rbac.permission.events` | Tạo / cập nhật / xóa Permission | `PermissionEvent` |
| `rbac.resource.events`   | Tạo / cập nhật / xóa Resource   | `ResourceEvent`   |
| `rbac.action.events`     | Tạo / cập nhật / xóa Action     | `ActionEvent`     |

api-gateway subscribe các topic này để đồng bộ RBAC cache mà không cần restart.

---

## Database schema

```
users
  id, username, password, full_name, email, provider
  mfa_enabled, sso_provider_id, sso_subject, enabled

roles
  id, name

user_roles
  user_id, role_id

permissions
  id, name (= "resource:action")

role_permissions
  role_id, permission_id

refresh_tokens
  id (UUID), token, username, expires_at, revoked, created_at

mfa_secrets
  id, user_id, secret (Base32 TOTP secret), verified, enabled, created_at

mfa_backup_codes
  id, user_id, code_hash (BCrypt), used, used_at, created_at

sso_providers
  id, provider_id, display_name, type, issuer_uri,
  client_id, client_secret, scopes, default_roles, enabled

oauth2_clients
  id, client_id, client_secret (BCrypt), scopes, granted_types,
  description, enabled, created_at
```

Migration được quản lý bởi Flyway trong module `migration`:

| File                                | Nội dung                                     |
|-------------------------------------|----------------------------------------------|
| `V1.0__init_database.sql`           | users, roles, user_roles                     |
| `V1.1__create_refresh_tokens.sql`   | refresh_tokens                               |
| `V1.2__create_rbac_tables.sql`      | permissions, resources, actions              |
| `V1.3__add_users_enabled_email.sql` | cột enabled, email                           |
| `V1.4__create_oauth2_clients.sql`   | oauth2_clients                               |
| `V1.5__add_mfa_and_sso.sql`         | mfa_secrets, mfa_backup_codes, sso_providers |

---

## Bảo mật

**JWT RS256 — tại sao không dùng HS256?**

Với HS256, Kubernetes và các relying party phải giữ secret key → rủi ro lộ key. RS256 cho phép verify bằng public key duy nhất từ `/oauth2/jwks` mà không cần chia sẻ private key.

**Production checklist:**

- [ ] Mount RSA private key qua Kubernetes Secret, set `OIDC_RSA_KEY_PATH`
- [ ] Đổi `INTERNAL_SECRET` khỏi giá trị mặc định
- [ ] Đặt `OIDC_ISSUER_URI` là URL public HTTPS của auth-service
- [ ] Cấu hình Redis password (`REDIS_PASSWORD`)
- [ ] Rotate `client_secret` của OAuth2 clients trong bảng `oauth2_clients`
- [ ] Enable TLS cho PostgreSQL và Redis connection
- [ ] Giới hạn `/internal/**` chỉ accessible từ internal cluster network
- [ ] Set `MFA_ISSUER` về tên app thật để hiển thị đúng trong authenticator
