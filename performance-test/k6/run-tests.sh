#!/bin/bash
# =============================================================================
# run-tests.sh — Script chạy k6 performance tests cho RBAC Gateway
#
# Usage:
#   ./run-tests.sh [scenario] [profile]
#
# Scenarios:
#   gateway-routing         (default)
#   rate-limit
#   circuit-breaker
#   rbac-auth
#   admin-route-management
#   full-system
#   all                     (chạy tất cả tuần tự)
#
# Profiles (chỉ áp dụng cho gateway-routing):
#   SMOKE | LOAD (default) | STRESS | SOAK | SPIKE | BREAKPOINT
#
# Examples:
#   ./run-tests.sh
#   ./run-tests.sh full-system STRESS
#   ./run-tests.sh rate-limit
#   ./run-tests.sh all
#   BASE_URL=http://prod:8080 ./run-tests.sh full-system LOAD
# =============================================================================

set -euo pipefail

# ── Config ─────────────────────────────────────────────────────────────────────
SCENARIO="${1:-gateway-routing}"
PROFILE="${2:-LOAD}"

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_USERNAME="${ADMIN_USERNAME:-perfAdmin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@123}"
USER_USERNAME="${USER_USERNAME:-perfUser}"
USER_PASSWORD="${USER_PASSWORD:-User@123}"

RESULTS_DIR="$(dirname "$0")/results"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"

mkdir -p "$RESULTS_DIR"

# ── Colors ─────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'

log()  { echo -e "${CYAN}[k6]${NC} $*"; }
ok()   { echo -e "${GREEN}[OK]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err()  { echo -e "${RED}[ERR]${NC} $*"; }

# ── Kiểm tra k6 đã cài chưa ────────────────────────────────────────────────────
if ! command -v k6 &>/dev/null; then
  err "k6 chưa được cài. Xem: https://k6.io/docs/getting-started/installation/"
  echo ""
  echo "Cài nhanh (Windows scoop):  scoop install k6"
  echo "Cài nhanh (macOS):          brew install k6"
  echo "Cài nhanh (Linux):          sudo snap install k6"
  exit 1
fi

# ── Kiểm tra Gateway đang chạy ─────────────────────────────────────────────────
log "Kiểm tra Gateway tại $BASE_URL ..."
if ! curl -sf "$BASE_URL/actuator/health" -o /dev/null; then
  warn "Gateway không phản hồi tại $BASE_URL"
  warn "Đảm bảo đã chạy: docker-compose up api-gateway (hoặc chạy local)"
  warn "Tiếp tục chạy test dù sao..."
fi

# ── Hàm chạy 1 scenario ────────────────────────────────────────────────────────
run_scenario() {
  local scenario="$1"
  local profile="${2:-LOAD}"
  local script="$(dirname "$0")/scenarios/${scenario}.js"
  local output="$RESULTS_DIR/${scenario}_${TIMESTAMP}.json"

  if [[ ! -f "$script" ]]; then
    err "Không tìm thấy script: $script"
    return 1
  fi

  log "Chạy: ${YELLOW}${scenario}${NC} [profile=${profile}]"
  echo "  Script  : $script"
  echo "  Base URL: $BASE_URL"
  echo "  Output  : $output"
  echo ""

  k6 run \
    --env BASE_URL="$BASE_URL" \
    --env TEST_PROFILE="$profile" \
    --env ADMIN_USERNAME="$ADMIN_USERNAME" \
    --env ADMIN_PASSWORD="$ADMIN_PASSWORD" \
    --env USER_USERNAME="$USER_USERNAME" \
    --env USER_PASSWORD="$USER_PASSWORD" \
    --out "json=$output" \
    "$script"

  local exit_code=$?
  if [[ $exit_code -eq 0 ]]; then
    ok "Scenario '${scenario}' PASSED"
  else
    err "Scenario '${scenario}' FAILED (exit code: $exit_code)"
  fi
  echo ""
  return $exit_code
}

# ── Main ───────────────────────────────────────────────────────────────────────
echo ""
echo -e "${BLUE}═══════════════════════════════════════════════${NC}"
echo -e "${BLUE}   RBAC Gateway — k6 Performance Tests${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════${NC}"
echo ""

if [[ "$SCENARIO" == "all" ]]; then
  log "Chạy tất cả scenarios..."
  FAILED=0

  run_scenario "gateway-routing"        "SMOKE" || FAILED=$((FAILED+1))
  run_scenario "rbac-auth"              "LOAD"  || FAILED=$((FAILED+1))
  run_scenario "rate-limit"             "LOAD"  || FAILED=$((FAILED+1))
  run_scenario "circuit-breaker"        "LOAD"  || FAILED=$((FAILED+1))
  run_scenario "admin-route-management" "LOAD"  || FAILED=$((FAILED+1))
  run_scenario "full-system"            "STRESS"|| FAILED=$((FAILED+1))

  echo ""
  if [[ $FAILED -eq 0 ]]; then
    ok "Tất cả scenarios PASSED!"
  else
    err "$FAILED scenario(s) FAILED."
    exit 1
  fi
else
  run_scenario "$SCENARIO" "$PROFILE"
fi

echo -e "${BLUE}═══════════════════════════════════════════════${NC}"
log "Results lưu tại: $RESULTS_DIR/"
echo ""
