# k6 Performance Tests — RBAC Gateway

Bộ test performance cho `api-gateway` dùng [k6](https://k6.io/).

## Cấu trúc thư mục

```
k6/
├── config/
│   ├── environment.js        # URLs, credentials, endpoints
│   └── options.js            # Preset profiles: SMOKE, LOAD, STRESS, SOAK, SPIKE, BREAKPOINT
├── helpers/
│   ├── auth.js               # Login helper, token management
│   └── metrics.js            # Custom k6 metrics (Trend, Counter, Rate)
├── scenarios/
│   ├── gateway-routing.js    # Mixed traffic, routing latency
│   ├── rate-limit.js         # Token bucket rate limiting (429 behavior)
│   ├── circuit-breaker.js    # Resilience4j CB open/close/half-open
│   ├── rbac-auth.js          # JWT auth + RBAC permission checks
│   ├── admin-route-management.js  # Admin CRUD API dưới tải
│   └── full-system.js        # Stress test toàn hệ thống
├── results/                  # JSON output (git-ignored)
├── run-tests.sh              # Linux/macOS runner
└── run-tests.bat             # Windows runner
```

## Cài đặt k6

```bash
# Windows (Scoop)
scoop install k6

# macOS (Homebrew)
brew install k6

# Linux (snap)
sudo snap install k6

# Docker (không cần cài)
docker run --rm -i grafana/k6 run - <scenarios/full-system.js
```

## Chạy test

### Cách 1: Script có sẵn

```bash
# Linux / macOS
chmod +x run-tests.sh
./run-tests.sh                          # gateway-routing, profile LOAD
./run-tests.sh full-system STRESS
./run-tests.sh rate-limit
./run-tests.sh all                      # chạy tất cả scenarios

# Windows
run-tests.bat
run-tests.bat full-system STRESS
run-tests.bat all
```

### Cách 2: Chạy trực tiếp với k6

```bash
# Smoke test nhanh
k6 run --env BASE_URL=http://localhost:8080 \
       --env ADMIN_EMAIL=admin@example.com \
       --env ADMIN_PASSWORD=Admin@123 \
       --env USER_EMAIL=user@example.com \
       --env USER_PASSWORD=User@123 \
       scenarios/gateway-routing.js

# Load test với profile tùy chọn
k6 run --env TEST_PROFILE=STRESS scenarios/full-system.js

# Xuất kết quả JSON
k6 run --out json=results/output.json scenarios/full-system.js
```

### Cách 3: Docker (không cần cài k6)

```bash
docker run --rm -i \
  --network host \
  -e BASE_URL=http://localhost:8080 \
  -e ADMIN_EMAIL=admin@example.com \
  -e ADMIN_PASSWORD=Admin@123 \
  -e USER_EMAIL=user@example.com \
  -e USER_PASSWORD=User@123 \
  -v $(pwd):/scripts \
  grafana/k6 run /scripts/scenarios/full-system.js
```

## Test Profiles

| Profile     | VUs          | Duration | Mục đích                            |
|-------------|--------------|----------|-------------------------------------|
| `SMOKE`     | 1            | 1 phút   | Sanity check, không lỗi cơ bản      |
| `LOAD`      | 20 (sustain) | ~4.5 phút| Traffic bình thường, p95 < 800ms    |
| `STRESS`    | lên đến 200  | ~6 phút  | Tìm điểm gãy                        |
| `SOAK`      | 20 (sustain) | ~34 phút | Detect memory leak                  |
| `SPIKE`     | lên đến 300  | ~2 phút  | Kiểm tra CB và rate limit khi burst |
| `BREAKPOINT`| lên đến 500  | ~13 phút | Xác định throughput tối đa          |

## Scenarios

### `gateway-routing.js`
Test routing latency với mixed workload:
- 40% GET `/api/resources/products`
- 30% GET `/api/resources/orders`
- 20% GET `/api/resources/profile/whoami`
- 10% POST `/api/auth/refresh`

### `rate-limit.js`
Test Token Bucket Rate Limiting:
- **Burst phase**: 50 req/s → trigger 429
- **Recovery phase**: nhẹ nhàng → verify bucket refill và user isolation

### `circuit-breaker.js`
Test Resilience4j Circuit Breaker:
- Normal → degraded → CB OPEN (fast-fail 503) → HALF-OPEN → CLOSED
- Cần inject failure thủ công hoặc dùng chaos tool vào downstream

### `rbac-auth.js`
Test JWT + RBAC dưới tải:
- 401 khi không có token (reject nhanh < 100ms)
- 403 khi thiếu permission (reject nhanh < 100ms)
- 200 khi có đủ permission

### `admin-route-management.js`
Test Admin CRUD API + RouteRefreshPublisher:
- 70% reads (GET routes, GET permissions)
- 30% writes (POST, PUT, DELETE routes)
- Tự cleanup k6 test routes trong teardown()

### `full-system.js`
Stress test toàn hệ thống với traffic thực tế:
- Mixed workload 6 loại request
- Custom summary report xuất ra `results/full-system-summary.json`

## Custom Metrics

| Metric                     | Type    | Mô tả                          |
|----------------------------|---------|--------------------------------|
| `auth_latency_ms`          | Trend   | Latency auth flow              |
| `resource_latency_ms`      | Trend   | Latency resource requests      |
| `admin_latency_ms`         | Trend   | Latency admin API              |
| `gateway_e2e_latency_ms`   | Trend   | End-to-end gateway latency     |
| `errors_401_total`         | Counter | Tổng 401 Unauthorized          |
| `errors_403_total`         | Counter | Tổng 403 Forbidden             |
| `errors_429_total`         | Counter | Tổng 429 Too Many Requests     |
| `errors_5xx_total`         | Counter | Tổng 5xx Server Error          |
| `circuit_breaker_open_total`| Counter| Số lần CB OPEN (503)           |
| `success_rate`             | Rate    | Tỷ lệ request thành công (2xx) |
| `rate_limit_rate`          | Rate    | Tỷ lệ bị rate limit            |

## Biến môi trường

| Biến              | Default                    | Mô tả                    |
|-------------------|----------------------------|--------------------------|
| `BASE_URL`        | `http://localhost:8080`    | Gateway URL              |
| `ADMIN_EMAIL`     | `admin@example.com`        | Admin credentials        |
| `ADMIN_PASSWORD`  | `Admin@123`                | Admin password           |
| `USER_EMAIL`      | `user@example.com`         | User credentials         |
| `USER_PASSWORD`   | `User@123`                 | User password            |
| `TEST_PROFILE`    | `LOAD`                     | Preset profile name      |
| `JWT_SECRET`      | (giá trị trong `.env`)     | JWT secret để tạo token  |

## Xem kết quả trên Grafana

Project đã có Grafana ở `http://localhost:3001`. Để xem k6 metrics real-time:

1. Chạy k6 với output InfluxDB:
```bash
k6 run --out influxdb=http://localhost:8086/k6 scenarios/full-system.js
```

2. Hoặc dùng k6 Grafana Cloud (miễn phí):
```bash
k6 run --out cloud scenarios/full-system.js
```

Kết quả JSON trong `results/` có thể import vào Grafana dashboard `k6 Load Testing Results`.

## Lưu ý

- `rate-limit.js` nên chạy với config rate-limit thấp (test environment) để thấy 429 rõ ràng.
  Production có `replenish-rate: 200000` nên rất khó trigger.
- `circuit-breaker.js` cần inject failure vào downstream (auth-service/resource-service).
  Dùng WireMock, chaos monkey, hoặc tắt service tạm thời để test.
- `results/` đã được thêm vào `.gitignore` — không commit kết quả test.
