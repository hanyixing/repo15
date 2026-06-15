#!/bin/sh
#
# Container entrypoint for the Kiss framework.
#
# Bridges 12-factor environment variables to the framework's INI loader by
# editing the deployed application.ini in place, then hands off (PID 1) to
# Tomcat.  Core code is untouched: the running app still reads its settings
# from application.ini, and secrets arrive via the environment at run time
# rather than being baked into any image layer.
#
set -e

INI="${CATALINA_HOME}/webapps/ROOT/WEB-INF/backend/application.ini"

# set_ini KEY VALUE
# Replace the (optionally commented-out) "KEY = ..." line in application.ini
# with "KEY = VALUE".  No-op when VALUE is empty, so any environment variable
# that is not set leaves the committed default in place.  Appends the key if
# it is absent (the file has a single [main] section).
set_ini() {
    key="$1"
    val="$2"
    [ -z "$val" ] && return 0
    [ -f "$INI" ] || return 0
    # Escape sed replacement metacharacters (& \ /) in the value.
    esc=$(printf '%s' "$val" | sed -e 's/[&\\/]/\\&/g')
    if grep -Eq "^[[:space:]]*#?[[:space:]]*${key}[[:space:]]*=" "$INI"; then
        sed -i -E "s|^[[:space:]]*#?[[:space:]]*${key}[[:space:]]*=.*|${key} = ${esc}|" "$INI"
    else
        printf '%s = %s\n' "$key" "$val" >> "$INI"
    fi
}

set_ini DatabaseType        "${DATABASE_TYPE:-}"
set_ini DatabaseHost        "${DATABASE_HOST:-}"
set_ini DatabasePort        "${DATABASE_PORT:-}"
set_ini DatabaseName        "${DATABASE_NAME:-}"
set_ini DatabaseUser        "${DATABASE_USER:-}"
set_ini DatabasePassword    "${DATABASE_PASSWORD:-}"
set_ini MaxWorkerThreads    "${MAX_WORKER_THREADS:-}"
set_ini UserInactiveSeconds "${USER_INACTIVE_SECONDS:-}"

# Hand off to Tomcat in the foreground as PID 1.
exec catalina.sh run
