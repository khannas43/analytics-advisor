#!/usr/bin/env bash
# Ensures analytics_demo objects were not created in srse.
set -euo pipefail
PGHOST="${PGHOST:-localhost}" PGPORT="${PGPORT:-5433}" PGUSER="${PGUSER:-srse}" PGPASSWORD="${PGPASSWORD:-srse_local_pw}"
export PGHOST PGPORT PGUSER PGPASSWORD

BAD=$(psql -d srse -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'demo'")
if [[ "${BAD}" != "0" ]]; then
  echo "FAIL: found demo schema tables in srse" >&2
  exit 1
fi
echo "OK: no demo schema in srse database"
