#!/usr/bin/env bash
# Stop the Kiss application
# Usage: ./stop.sh [--force]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

FORCE=false
TOMCAT_DIR="$SCRIPT_DIR/tomcat"
CATALINA_PID="$TOMCAT_DIR/catalina.pid"
export CATALINA_PID

GRACEFUL_TIMEOUT=30

if [ "${1:-}" = "--force" ]; then
    FORCE=true
fi

log_info() {
    echo "[INFO] $1"
}

log_warn() {
    echo "[WARN] $1"
}

is_tomcat_running() {
    if [ -f "$CATALINA_PID" ] && kill -0 "$(cat "$CATALINA_PID")" 2>/dev/null; then
        return 0
    fi
    ps aux 2>/dev/null | grep -v grep | grep -q "catalina.startup.Bootstrap" && return 0
    return 1
}

wait_for_stop() {
    local elapsed=0
    while [ $elapsed -lt $GRACEFUL_TIMEOUT ]; do
        if ! is_tomcat_running; then
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    return 1
}

get_tomcat_pid() {
    if [ -f "$CATALINA_PID" ]; then
        cat "$CATALINA_PID"
        return
    fi
    ps aux 2>/dev/null | grep -v grep | grep "catalina.startup.Bootstrap" | awk '{print $2}' | head -1
}

# --- Main ---

if ! is_tomcat_running; then
    log_info "Tomcat is not running."
    exit 0
fi

log_info "Stopping Tomcat..."

if [ -x "$TOMCAT_DIR/bin/shutdown.sh" ]; then
    "$TOMCAT_DIR/bin/shutdown.sh" 2>/dev/null || true
fi

if wait_for_stop; then
    log_info "Tomcat stopped gracefully."
    exit 0
fi

if [ "$FORCE" = true ]; then
    log_warn "Graceful shutdown timed out (${GRACEFUL_TIMEOUT}s). Force killing..."
    pid=$(get_tomcat_pid)
    if [ -n "$pid" ]; then
        kill -9 "$pid" 2>/dev/null || true
        rm -f "$CATALINA_PID"
        log_info "Tomcat force killed (PID: $pid)."
    else
        log_warn "Could not determine Tomcat PID."
        exit 1
    fi
else
    log_warn "Tomcat did not stop within ${GRACEFUL_TIMEOUT}s."
    log_warn "Use './stop.sh --force' to force kill."
    exit 1
fi
