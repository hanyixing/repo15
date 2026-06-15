#!/usr/bin/env bash
#
# Select an environment config and (re)build + start the Kiss backend.
#
# Usage:
#   ./deploy.sh <dev|test|prod> [--war-only]
#
# This is a thin wrapper over the real build tool (./bld); it does not
# reimplement the build.  It copies config/application-<env>.ini over
# src/main/backend/application.ini, then builds and starts the backend.
#
set -euo pipefail
cd "$(dirname "$0")"

ENVIRONMENT="${1:-}"
WAR_ONLY="${2:-}"

case "$ENVIRONMENT" in
    dev|test|prod) ;;
    *)
        echo "Usage: $0 <dev|test|prod> [--war-only]" >&2
        exit 1
        ;;
esac

# --- Java 17+ guard (modeled on build-dist) ---
if ! command -v java >/dev/null 2>&1; then
    echo "Error: Java is not installed or not in PATH (need Java 17+)." >&2
    exit 1
fi
JAVA_VER=$(java -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')
if [ "$JAVA_VER" -lt 17 ] 2>/dev/null; then
    echo "Error: Java 17 or later is required (found Java $JAVA_VER)." >&2
    exit 1
fi

SRC_INI="config/application-${ENVIRONMENT}.ini"
DEST_INI="src/main/backend/application.ini"
if [ ! -f "$SRC_INI" ]; then
    echo "Error: $SRC_INI not found." >&2
    exit 1
fi
echo "Selecting $SRC_INI -> $DEST_INI"
cp "$SRC_INI" "$DEST_INI"

chmod +x bld

if [ "$WAR_ONLY" = "--war-only" ]; then
    echo "Building WAR only..."
    ./bld war
    echo "Created work/Kiss.war"
    exit 0
fi

echo "Stopping any running backend (best effort)..."
./bld stop-backend || true

echo "Building..."
./bld build

echo "Starting backend..."
./bld start-backend

# --- Readiness poll ---
printf "Waiting for http://localhost:8080 "
for _ in $(seq 1 60); do
    if curl -fsS -o /dev/null http://localhost:8080 2>/dev/null; then
        echo ""
        echo "Backend is up at http://localhost:8080"
        exit 0
    fi
    printf "."
    sleep 1
done
echo ""
echo "Warning: backend did not respond within 60s; check tomcat/logs/catalina.out" >&2
exit 1
