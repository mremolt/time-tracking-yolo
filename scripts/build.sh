#!/usr/bin/env bash
# Builds the Angular app, bundles it into the Spring Boot jar and runs the backend tests (unless SKIP_TESTS=1).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/frontend"
[ -d node_modules ] || npm ci
npx ng build
rm -rf "$ROOT/backend/src/main/resources/static"
mkdir -p "$ROOT/backend/src/main/resources/static"
cp -r dist/frontend/browser/* "$ROOT/backend/src/main/resources/static/"
cd "$ROOT/backend"
if [ "${SKIP_TESTS:-0}" = "1" ]; then mvn -q package -DskipTests; else mvn -q package; fi
echo "Built: backend/target/timetracking-0.1.0.jar"
