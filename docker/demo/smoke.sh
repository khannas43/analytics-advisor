#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRESTO_PORT="${PRESTO_PORT:-8083}"

echo "==> Postgres counts"
PGHOST="${PGHOST:-localhost}" PGPORT="${PGPORT:-5433}" PGUSER="${PGUSER:-srse}" PGPASSWORD="${PGPASSWORD:-srse_local_pw}"
export PGHOST PGPORT PGUSER PGPASSWORD
psql -d analytics_demo -c "SELECT count(*) AS beneficiary FROM demo.beneficiary;"
psql -d analytics_demo -c "SELECT count(*) AS bank FROM demo.bank_account;"

echo "==> Waiting for Presto on :${PRESTO_PORT}..."
for i in $(seq 1 60); do
  if curl -sf "http://localhost:${PRESTO_PORT}/v1/info" >/dev/null 2>&1; then
    echo "Presto up."
    break
  fi
  sleep 2
done

echo "==> SHOW CATALOGS"
"${SCRIPT_DIR}/presto-query.sh" "SHOW CATALOGS"

echo "==> SELECT count(*) FROM demo_pg.demo.beneficiary"
"${SCRIPT_DIR}/presto-query.sh" "SELECT count(*) FROM demo_pg.demo.beneficiary"
