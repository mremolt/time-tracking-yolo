#!/usr/bin/env bash
# Fresh local demo installation: resets the demo database, loads the demo data and starts the application on http://localhost:8080
# Use RESET=0 to keep the existing demo data.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/backend/target/timetracking-0.1.0.jar"
[ -f "$JAR" ] || "$ROOT/scripts/build.sh"
cd "$ROOT/backend"
[ "${RESET:-1}" = "1" ] && rm -rf data
exec java -jar "$JAR" --spring.profiles.active=demo
