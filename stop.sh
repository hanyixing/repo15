#!/usr/bin/env bash
#
# Stop the running Kiss backend.
#
set -euo pipefail
cd "$(dirname "$0")"

chmod +x bld
./bld stop-backend
echo "Backend stopped."
