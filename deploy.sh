#!/usr/bin/env bash
# Deploy the Kiss application
# Usage: ./deploy.sh [dev|test|prod]
# Default mode: prod

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV="${1:-prod}"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

# --- Colors ---

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# --- Helper functions ---

usage() {
    echo "Usage: $0 [dev|test|prod]"
    echo "  dev   - Development deployment"
    echo "  test  - Test/staging deployment"
    echo "  prod  - Production deployment (default)"
    exit 1
}

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

log_step() {
    echo ""
    echo -e "${GREEN}=== $1 ===${NC}"
}

on_error() {
    log_error "Deployment failed!"
    log_error "Check logs: tomcat/logs/catalina.out"
    log_error "Environment: $ENV"
    exit 1
}

trap on_error ERR

# --- Main ---

case "$ENV" in
    dev|test|prod) ;;
    *) usage ;;
esac

log_info "Starting deployment for environment: $ENV"
echo ""

# Step 1: Stop existing application
log_step "Step 1/7: Stop existing application"
if bash ./stop.sh --force; then
    log_info "Application stopped."
else
    log_warn "Stop script returned non-zero (may not have been running)."
fi

# Step 2: Copy environment-specific config
log_step "Step 2/7: Apply environment config"
config_file="config/application-${ENV}.ini"
if [ -f "$config_file" ]; then
    cp "$config_file" src/main/backend/application.ini
    log_info "Applied config: $config_file"
else
    log_warn "No config found at $config_file, keeping existing application.ini"
fi

# Step 3: Clean
log_step "Step 3/7: Clean build artifacts"
./bld clean
log_info "Clean complete."

# Step 4: Build
log_step "Step 4/7: Build application"
./bld build
log_info "Build complete."

# Step 5: Create WAR
log_step "Step 5/7: Create WAR file"
./bld war
log_info "WAR created."

# Step 6: Start application
log_step "Step 6/7: Start application"
./start.sh "$ENV"

# Step 7: Verify deployment
log_step "Step 7/7: Verify deployment"
if curl -sf http://localhost:8080 &>/dev/null; then
    log_info "Health check passed."
else
    log_error "Health check failed - server may not be responding."
    exit 1
fi

# Summary
echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  Deployment Complete${NC}"
echo -e "${GREEN}============================================${NC}"
echo "  Environment : $ENV"
echo "  URL         : http://localhost:8080"
echo "  Timestamp   : $TIMESTAMP"
echo "  Logs        : tomcat/logs/catalina.out"
echo -e "${GREEN}============================================${NC}"
