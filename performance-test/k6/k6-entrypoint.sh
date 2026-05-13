#!/bin/sh
# k6-entrypoint.sh — wrapper script cho k6 container.
#
# Tại sao cần script này thay vì dùng command/entrypoint trực tiếp?
#
# Vấn đề: Docker Compose interpolate $VAR ở host-side khi parse docker-compose.yml.
# - `command: ["k6 run $K6_SCRIPT"]`  → Compose expand $K6_SCRIPT ở HOST → empty → lỗi
# - `command: ["k6 run $$K6_SCRIPT"]` → Compose giữ $K6_SCRIPT → truyền vào sh -c đúng,
#   nhưng một số version Compose/platform vẫn không reliable.
#
# Giải pháp: mount script này vào container, dùng làm entrypoint.
# $K6_SCRIPT được expand hoàn toàn trong container shell, không liên quan host.

set -e

SCRIPT="${K6_SCRIPT:-scenarios/full-system.js}"

echo "[k6-entrypoint] Script  : $SCRIPT"
echo "[k6-entrypoint] Profile : ${TEST_PROFILE:-LOAD}"
echo "[k6-entrypoint] Base URL: ${BASE_URL:-http://localhost:8080}"
echo ""

exec k6 run "$SCRIPT"
