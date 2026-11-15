#!/usr/bin/env bash
# CI entry point: compiles everything, runs unit/slice tests (surefire, *Test) and the
# Testcontainers integration tests (failsafe, *IT). ITs are skipped automatically when Docker is absent,
# so set REQUIRE_DOCKER=1 in CI to fail fast instead of silently skipping them.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ "${REQUIRE_DOCKER:-0}" == "1" ]] && ! docker info >/dev/null 2>&1; then
  echo "REQUIRE_DOCKER=1 but Docker is not available; integration tests would be skipped." >&2
  exit 1
fi

./mvnw -B -ntp verify "$@"
