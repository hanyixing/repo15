#!/usr/bin/env bash
# Start the Kiss application
# Usage: ./start.sh [dev|test|prod]
# Default mode: dev

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV="${1:-dev}"
TOMCAT_DIR="$SCRIPT_DIR/tomcat"
CATALINA_PID="$TOMCAT_DIR/catalina.pid"
export CATALINA_PID

TIMEOUT=60

# --- Helper functions ---

usage() {
    echo "Usage: $0 [dev|test|prod]"
    echo "  dev   - Development mode (default)"
    echo "  test  - Test/staging mode"
    echo "  prod  - Production mode"
    exit 1
}

log_info() {
    echo "[INFO] $1"
}

log_error() {
    echo "[ERROR] $1" >&2
}

check_env() {
    case "$ENV" in
        dev|test|prod) ;;
        *) usage ;;
    esac
}

check_java() {
    if ! command -v java &>/dev/null; then
        log_error "Java is not installed or not on PATH."
        exit 1
    fi
    local version
    version=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$version" -lt 17 ] 2>/dev/null; then
        log_error "Java 17+ is required. Found version: $version"
        exit 1
    fi
    log_info "Java $version detected."
}

apply_config() {
    local config_file="config/application-${ENV}.ini"
    if [ -f "$config_file" ]; then
        log_info "Applying config for environment: $ENV"
        cp "$config_file" src/main/backend/application.ini
    else
        log_info "No environment config found at $config_file, using existing application.ini"
    fi
}

is_port_in_use() {
    if command -v ss &>/dev/null; then
        ss -tln 2>/dev/null | grep -q ":8080 " && return 0
    elif command -v netstat &>/dev/null; then
        netstat -tln 2>/dev/null | grep -q ":8080 " && return 0
    elif command -v lsof &>/dev/null; then
        lsof -i :8080 -sTCP:LISTEN &>/dev/null && return 0
    fi
    return 1
}

wait_for_server() {
    log_info "Waiting for server to start (timeout: ${TIMEOUT}s)..."
    local elapsed=0
    while [ $elapsed -lt $TIMEOUT ]; do
        if curl -sf http://localhost:8080 &>/dev/null; then
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done
    return 1
}

# --- Main ---

check_env
check_java

if is_port_in_use; then
    log_error "Port 8080 is already in use. Is Tomcat already running?"
    log_error "Stop it first with: ./stop.sh"
    exit 1
fi

apply_config

log_info "Building application..."
if ! ./bld build; then
    log_error "Build failed. See output above for details."
    exit 1
fi

log_info "Starting Tomcat ($ENV)..."
if [ ! -x "$TOMCAT_DIR/bin/catalina.sh" ]; then
    log_error "Cannot find $TOMCAT_DIR/bin/catalina.sh"
    exit 1
fi

"$TOMCAT_DIR/bin/catalina.sh" start

if wait_for_server; then
    log_info "Server started successfully."
    log_info "  URL: http://localhost:8080"
    log_info "  Environment: $ENV"
    log_info "  Logs: tomcat/logs/catalina.out"
else
    log_error "Server failed to start within ${TIMEOUT}s."
    log_error "Check logs: tomcat/logs/catalina.out"
    exit 1
fi
