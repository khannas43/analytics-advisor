#!/usr/bin/env bash
# Runs profile.sql on Postgres and fuzzy benchmarks on demo Presto; writes docs/demo/DATA_PROFILE.md
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
OUT="${REPO_ROOT}/docs/demo/DATA_PROFILE.md"
PRESTO_PORT="${PRESTO_PORT:-8083}"
ROWS="${ROWS:-1000000}"
BLOCKING_PREFIX_LEN="${SRSE_ANALYSIS_BLOCKING_PREFIX_LEN:-3}"

PGHOST="${PGHOST:-localhost}" PGPORT="${PGPORT:-5433}" PGUSER="${PGUSER:-srse}" PGPASSWORD="${PGPASSWORD:-srse_local_pw}"
export PGHOST PGPORT PGUSER PGPASSWORD

mkdir -p "$(dirname "${OUT}")"
PROFILE_OUT="$(mktemp)"
psql -d analytics_demo -v ON_ERROR_STOP=1 \
  -v blocking_prefix_len="${BLOCKING_PREFIX_LEN}" \
  -f "${SCRIPT_DIR}/profile.sql" | tee "${PROFILE_OUT}"

BEN=$(psql -d analytics_demo -tAc 'SELECT count(*) FROM demo.beneficiary')
BANK=$(psql -d analytics_demo -tAc 'SELECT count(*) FROM demo.bank_account')
SRC_DISTINCT=$(psql -d analytics_demo -tAc "SELECT count(DISTINCT substr(lower(full_name),1,${BLOCKING_PREFIX_LEN})) FROM demo.beneficiary")
TGT_DISTINCT=$(psql -d analytics_demo -tAc "SELECT count(DISTINCT substr(lower(account_holder_name),1,${BLOCKING_PREFIX_LEN})) FROM demo.bank_account")
BLOCKING_KEY_DISTINCT=$(( SRC_DISTINCT > TGT_DISTINCT ? SRC_DISTINCT : TGT_DISTINCT ))
FANOUT=$(( BEN * BANK / BLOCKING_KEY_DISTINCT ))
GUARD=50000000
FANOUT_TARGET=40000000

sample_table() {
  local title="$1"
  local sql="$2"
  echo "### ${title}"
  echo '```'
  psql -d analytics_demo -tA -c "${sql}" | head -20
  echo '```'
}

SAMPLES="$(sample_table "beneficiary.full_name (20)" "SELECT full_name FROM demo.beneficiary TABLESAMPLE SYSTEM (0.01) LIMIT 20")"
SAMPLES+=$'\n\n'"$(sample_table "bank_account.account_holder_name (20)" "SELECT account_holder_name FROM demo.bank_account TABLESAMPLE SYSTEM (0.01) LIMIT 20")"
SAMPLES+=$'\n\n'"$(sample_table "pension_register first + last (20)" "SELECT first_name || ' ' || last_name FROM demo.pension_register TABLESAMPLE SYSTEM (0.05) LIMIT 20")"
SAMPLES+=$'\n\n'"$(sample_table "ration_card.head_name (20)" "SELECT head_name FROM demo.ration_card TABLESAMPLE SYSTEM (0.05) LIMIT 20")"

SIM_EXPR="(1.0 - CAST(levenshtein_distance(lower(s.full_name), lower(t.account_holder_name)) AS DOUBLE) / GREATEST(length(s.full_name), length(t.account_holder_name), 1))"
BLOCK_ON="substr(lower(s.full_name), 1, ${BLOCKING_PREFIX_LEN}) = substr(lower(t.account_holder_name), 1, ${BLOCKING_PREFIX_LEN})"
ID_AGREE="s.beneficiary_id = CAST(NULLIF(LTRIM(t.beneficiary_ref, '0'), '') AS BIGINT)"

BENCH1_SQL="
SELECT
  count(*) AS match_rows,
  sum(CASE WHEN ${ID_AGREE} THEN 1 ELSE 0 END) AS id_agree_rows
FROM demo_pg.demo.beneficiary s
JOIN demo_pg.demo.bank_account t
  ON ${BLOCK_ON}
 AND s.mobile = t.mobile
 AND ${SIM_EXPR} >= 0.85
"

run_presto() {
  local sql="$1"
  local start end elapsed result
  start=$(date +%s)
  result=$("${SCRIPT_DIR}/presto-query.sh" "${sql}" 2>/dev/null | tr -d '[]" ') || result="FAILED"
  end=$(date +%s)
  elapsed=$(( end - start ))
  echo "${result}|${elapsed}"
}

PRESTO_B1="skipped|0"
if curl -sf "http://localhost:${PRESTO_PORT}/v1/info" >/dev/null 2>&1; then
  PRESTO_B1=$(run_presto "${BENCH1_SQL}")
fi

B1_COUNTS=$(echo "${PRESTO_B1}" | cut -d'|' -f1)
B1_ROWS=$(echo "${B1_COUNTS}" | cut -d',' -f1)
B1_AGREE=$(echo "${B1_COUNTS}" | cut -d',' -f2)
B1_TIME=$(echo "${PRESTO_B1}" | cut -d'|' -f2)
if [[ "${B1_ROWS}" =~ ^[0-9]+$ ]] && [[ "${B1_ROWS}" -gt 0 ]] && [[ "${B1_AGREE}" =~ ^[0-9]+$ ]]; then
  B1_PRECISION=$(awk "BEGIN {printf \"%.2f\", 100.0 * ${B1_AGREE} / ${B1_ROWS}}")
else
  B1_PRECISION="n/a"
fi

GENERATED=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

cat > "${OUT}" <<EOF
# Demo data profile (generated)

Generated: **${GENERATED}** · \`ROWS=${ROWS}\` · blocking prefix **${BLOCKING_PREFIX_LEN}** (product default) · beneficiary=${BEN} · bank=${BANK}

## Row counts and overlap

\`\`\`
$(grep -v '^$' "${PROFILE_OUT}" | head -280)
\`\`\`

## Fan-out estimate (product guard, prefix len ${BLOCKING_PREFIX_LEN})

Formula: \`sourceRows × targetRows / max(distinct blocking prefix)\` on
\`substr(lower(full_name), 1, ${BLOCKING_PREFIX_LEN})\` vs \`substr(lower(account_holder_name), 1, ${BLOCKING_PREFIX_LEN})\`.

| Input | Value |
|-------|-------|
| sourceRows | ${BEN} |
| targetRows | ${BANK} |
| max distinct blocking prefixes | ${BLOCKING_KEY_DISTINCT} |
| **Estimated fan-out** | **${FANOUT}** |
| Demo acceptance target | ${FANOUT_TARGET} (must stay below 40M; guard unchanged at 50M) |
| \`SRSE_ANALYSIS_MAX_ESTIMATED_ROWS\` | ${GUARD} |
| Guard would refuse? | $(if (( FANOUT > GUARD )); then echo Yes; else echo No; fi) |
| Under 40M target? | $(if (( FANOUT < FANOUT_TARGET )); then echo Yes; else echo No; fi) |

## Fuzzy benchmark (Presto direct, blocking prefix ${BLOCKING_PREFIX_LEN})

Fuzzy name ≥85% **and exact mobile**, unscoped: **${B1_ROWS}** rows, **${B1_AGREE} / ${B1_ROWS}** ID precision (${B1_PRECISION}%), **${B1_TIME}s** wall time.

Precision counts rows where \`beneficiary_id\` matches \`beneficiary_ref\` on the linked bank row.

## Sample names (20 each)

${SAMPLES}

## Notes

- Name pools: \`docker/demo/name_pools/\` (250 M / 250 F first, 75 M / 75 F middle, 1000 single-token surnames); at most three tokens per person name; typos on bank/pension only.
- Quality gates: distinct full names ≥60%, most common full name ≤1,500 rows; \`name_regex_check\` in profile output must show zero digits and zero short tokens.
- Re-seed checksums: run \`./docker/demo/seed.sh\` and compare md5 lines at end of output.

EOF

rm -f "${PROFILE_OUT}"
echo "Wrote ${OUT}"
