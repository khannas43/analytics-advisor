#!/usr/bin/env bash
# Idempotent setup for analytics_demo on the root compose postgres (never touches srse).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SECRETS_DIR="${SCRIPT_DIR}/secrets"
PASSWORD_FILE="${SECRETS_DIR}/demo_ro.password"

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5433}"
PGUSER="${PGUSER:-srse}"
PGPASSWORD="${PGPASSWORD:-srse_local_pw}"
export PGHOST PGPORT PGUSER PGPASSWORD

RESET=false
if [[ "${1:-}" == "--reset" ]]; then
  RESET=true
fi

mkdir -p "${SECRETS_DIR}"
if [[ ! -f "${PASSWORD_FILE}" ]]; then
  echo "demo_ro_dev_pw" > "${PASSWORD_FILE}"
  chmod 600 "${PASSWORD_FILE}"
fi
DEMO_RO_PW="$(tr -d '\n' < "${PASSWORD_FILE}")"

# Keep catalog properties in sync (dev default password).
PROPS="${SCRIPT_DIR}/presto-catalog/demo_pg.properties"
if grep -q '^connection-password=' "${PROPS}"; then
  sed -i.bak "s/^connection-password=.*/connection-password=${DEMO_RO_PW}/" "${PROPS}"
  rm -f "${PROPS}.bak"
fi

echo "==> Target postgres://${PGUSER}@${PGHOST}:${PGPORT} (database srse for admin)"

if ${RESET}; then
  echo "==> Dropping database analytics_demo (only)"
  psql -d postgres -v ON_ERROR_STOP=1 <<SQL
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = 'analytics_demo' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS analytics_demo;
SQL
fi

psql -d postgres -v ON_ERROR_STOP=1 <<SQL
SELECT 'CREATE DATABASE analytics_demo'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'analytics_demo')\gexec
SQL

psql -d postgres -v ON_ERROR_STOP=1 <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'demo_ro') THEN
    CREATE ROLE demo_ro LOGIN PASSWORD '${DEMO_RO_PW}';
  ELSE
    ALTER ROLE demo_ro WITH PASSWORD '${DEMO_RO_PW}';
  END IF;
END
\$\$;
GRANT CONNECT ON DATABASE analytics_demo TO demo_ro;
SQL

psql -d analytics_demo -v ON_ERROR_STOP=1 <<SQL
CREATE SCHEMA IF NOT EXISTS demo AUTHORIZATION ${PGUSER};
GRANT USAGE ON SCHEMA demo TO demo_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA demo GRANT SELECT ON TABLES TO demo_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA demo GRANT SELECT ON SEQUENCES TO demo_ro;
SQL

echo "==> analytics_demo ready; demo_ro password in ${PASSWORD_FILE}"
