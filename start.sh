#!/usr/bin/env bash
#
# Start the already-built Kiss backend and wait until it answers on :8080.
# (Run ./deploy.sh <env> first if you need to (re)build.)
#
set -euo pipefail
cd "$(dirname "$0")"

chmod +x bld
./bld start-backend

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
