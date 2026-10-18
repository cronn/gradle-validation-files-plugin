#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

./gradlew --refresh-dependencies dependencies buildEnvironment --write-locks
