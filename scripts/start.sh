#!/usr/bin/env bash
# Starts the application without demo data (only the bootstrap admin from application.yml). Data is kept in backend/data.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/backend/target/timetracking-0.1.0.jar"
[ -f "$JAR" ] || "$ROOT/scripts/build.sh"
cd "$ROOT/backend"
exec java -jar "$JAR" "$@"
