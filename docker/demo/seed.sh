#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ROWS="${ROWS:-1000000}"
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5433}"
PGUSER="${PGUSER:-srse}"
PGPASSWORD="${PGPASSWORD:-srse_local_pw}"
export PGHOST PGPORT PGUSER PGPASSWORD

BANK_ROWS=$(( ROWS * 900 / 1000 ))
PENSION_ROWS=$(( ROWS * 300 / 1000 ))
RATION_ROWS=$(( ROWS * 400 / 1000 ))

if (( ROWS < 1 )); then
  echo "ROWS must be positive" >&2
  exit 1
fi

echo "==> Building / loading name pools"
python3 "${SCRIPT_DIR}/build_name_pools.py" "${SCRIPT_DIR}/name_pools"
psql -d analytics_demo -v ON_ERROR_STOP=1 <<SQL
DROP TABLE IF EXISTS demo._name_first_pool;
DROP TABLE IF EXISTS demo._name_last_pool;
DROP TABLE IF EXISTS demo._name_middle_pool;
CREATE TABLE demo._name_first_pool (gender char(1) NOT NULL, idx int NOT NULL, name text NOT NULL, weight int NOT NULL, PRIMARY KEY (gender, idx));
CREATE TABLE demo._name_last_pool (idx int PRIMARY KEY, name text NOT NULL, weight int NOT NULL);
CREATE TABLE demo._name_middle_pool (gender char(1) NOT NULL, idx int NOT NULL, name text NOT NULL, weight int NOT NULL, PRIMARY KEY (gender, idx));
SQL
psql -d analytics_demo -v ON_ERROR_STOP=1 -c "\\copy demo._name_first_pool FROM '${SCRIPT_DIR}/name_pools/first_names.tsv' WITH (FORMAT csv, DELIMITER E'\\t', HEADER true)"
psql -d analytics_demo -v ON_ERROR_STOP=1 -c "\\copy demo._name_middle_pool FROM '${SCRIPT_DIR}/name_pools/middle_names.tsv' WITH (FORMAT csv, DELIMITER E'\\t', HEADER true)"
psql -d analytics_demo -v ON_ERROR_STOP=1 -c "\\copy demo._name_last_pool FROM '${SCRIPT_DIR}/name_pools/last_names.tsv' WITH (FORMAT csv, DELIMITER E'\\t', HEADER true)"

echo "==> Seeding analytics_demo.demo ROWS=${ROWS} bank=${BANK_ROWS} pension=${PENSION_ROWS} ration=${RATION_ROWS}"

START=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
psql -d analytics_demo -v ON_ERROR_STOP=1 \
  -v beneficiary_rows="${ROWS}" \
  -v bank_rows="${BANK_ROWS}" \
  -v pension_rows="${PENSION_ROWS}" \
  -v ration_rows="${RATION_ROWS}" \
  -f "${SCRIPT_DIR}/seed.sql"
END=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
echo "==> Seed finished ${START} .. ${END}"

echo "==> Table checksums (md5 of ordered pk cast to text):"
psql -d analytics_demo -v ON_ERROR_STOP=1 <<'SQL'
SELECT 'beneficiary' AS tbl, md5(string_agg(beneficiary_id::text, ',' ORDER BY beneficiary_id)) AS chk
FROM demo.beneficiary
UNION ALL
SELECT 'bank_account', md5(string_agg(account_id::text, ',' ORDER BY account_id)) FROM demo.bank_account
UNION ALL
SELECT 'pension_register', md5(string_agg(pension_id::text, ',' ORDER BY pension_id)) FROM demo.pension_register
UNION ALL
SELECT 'ration_card', md5(string_agg(card_id::text, ',' ORDER BY card_id)) FROM demo.ration_card
UNION ALL
SELECT 'district_master', md5(string_agg(district_code, ',' ORDER BY district_code)) FROM demo.district_master;
SQL
