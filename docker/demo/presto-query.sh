#!/usr/bin/env bash
# Run one SQL statement against demo Presto via the HTTP API (no CLI required).
set -euo pipefail
SQL="${1:?usage: presto-query.sh \"SELECT 1\"}"
HOST="${PRESTO_HOST:-localhost}"
PORT="${PRESTO_PORT:-8083}"

resp=$(curl -sf -X POST "http://${HOST}:${PORT}/v1/statement" \
  -H 'X-Presto-User: demo' \
  -H 'X-Presto-Catalog: demo_pg' \
  -H 'X-Presto-Schema: demo' \
  -d "${SQL}")

uri=$(echo "${resp}" | python3 -c "import sys,json; print(json.load(sys.stdin)['nextUri'])")

while true; do
  page=$(curl -sf "${uri}")
  err=$(echo "${page}" | python3 -c "import sys,json; j=json.load(sys.stdin); print(j.get('error',{}).get('message',''))" 2>/dev/null || true)
  if [[ -n "${err}" ]]; then
    echo "ERROR: ${err}" >&2
    exit 1
  fi
  data=$(echo "${page}" | python3 -c "import sys,json; j=json.load(sys.stdin); d=j.get('data'); print('DATA:'+str(d) if d is not None else '')")
  if [[ "${data}" == DATA:* ]]; then
    echo "${data#DATA:}"
    exit 0
  fi
  uri=$(echo "${page}" | python3 -c "import sys,json; print(json.load(sys.stdin)['nextUri'])")
done
