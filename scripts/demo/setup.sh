#!/usr/bin/env bash
# Idempotent DEMO-02 setup. Re-run is a no-op. --reset removes only what we created.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi
if [[ -f "$ROOT/scripts/demo/demo.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/scripts/demo/demo.env"
  set +a
fi
exec python3 "$ROOT/scripts/demo/setup.py" "$@"
