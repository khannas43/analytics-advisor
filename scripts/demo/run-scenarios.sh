#!/usr/bin/env bash
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
export PYTHONUNBUFFERED=1
exec python3 "$ROOT/scripts/demo/run-scenarios.py" "$@"
