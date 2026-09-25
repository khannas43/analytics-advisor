#!/usr/bin/env python3
"""
AA-17 volume measurements against local Presto (not part of mvn test).

Usage (Presto on localhost:8081, after seed):
  docker run --rm --entrypoint python -v $PWD:/seed -w /seed \\
    -e PRESTO_URL=jdbc:presto://host.docker.internal:8081/iceberg/srse aa-seed volume_probe.py

Optional:
  SKIP_HEAVY=1     — skip district join and near-ceiling join
  SKIP_NEAR_CEILING=1 — skip Q2 actual join (~49M rows at 200k scale)
"""
from __future__ import annotations

import os
import sys
import time

import prestodb

from seed import parse_presto_url

PRESTO_URL = os.environ.get("PRESTO_URL", "jdbc:presto://localhost:8081/iceberg/srse")
PREFIX_LEN = int(os.environ.get("SRSE_ANALYSIS_BLOCKING_PREFIX_LEN", "3"))
CEILING = int(os.environ.get("SRSE_ANALYSIS_MAX_ESTIMATED_ROWS", "50000000"))
SKIP_HEAVY = os.environ.get("SKIP_HEAVY", "").strip().lower() in ("1", "true", "yes")
SKIP_NEAR_CEILING = os.environ.get("SKIP_NEAR_CEILING", "").strip().lower() in ("1", "true", "yes")


def connect():
    host, port, catalog, schema = parse_presto_url(PRESTO_URL)
    return prestodb.dbapi.connect(
        host=host,
        port=port,
        user=os.environ.get("PRESTO_USER", "probe"),
        catalog=catalog,
        schema=schema,
    )


def timed(cursor, label: str, sql: str) -> tuple[float, list]:
    t0 = time.perf_counter()
    cursor.execute(sql)
    rows = cursor.fetchall() if cursor.description else []
    secs = time.perf_counter() - t0
    print(f"[probe] {label}: {secs:.2f}s -> {rows[:3]}{'…' if len(rows) > 3 else ''}")
    return secs, rows


def scalar(rows, default=0):
    if not rows or rows[0][0] is None:
        return default
    return rows[0][0]


def fan_out_estimate(src_rows, tgt_rows, src_distinct, tgt_distinct) -> float:
    return src_rows * tgt_rows / max(src_distinct, tgt_distinct, 1)


def in_list(codes: list[str]) -> str:
    return ", ".join("'" + c.replace("'", "''") + "'" for c in codes)


def village_codes(n: int) -> list[str]:
    """Synthetic village-level codes (A17 depth), deterministic."""
    return [f"RJ-VLG-{i:05d}" for i in range(n)]


def main():
    conn = connect()
    cur = conn.cursor()

    print("=== AA-17 volume probe ===")
    print(f"PRESTO_URL={PRESTO_URL}")

    _, n_ben = timed(cur, "count beneficiary", "SELECT count(*) FROM iceberg.srse.beneficiary")
    ben = scalar(n_ben)
    _, n_bank = timed(cur, "count bank", "SELECT count(*) FROM iceberg_silver.silver_txn.tbl_txn_bankdtl")
    bank = scalar(n_bank)

    # Q1 skewed key: district join (low distinct, high skew)
    _, d_src = timed(
        cur,
        "approx_distinct district (src)",
        "SELECT approx_distinct(district) FROM iceberg.srse.beneficiary",
    )
    _, d_tgt = timed(
        cur,
        "approx_distinct district (tgt)",
        "SELECT approx_distinct(district) FROM iceberg_silver.silver_txn.tbl_txn_bankdtl",
    )
    est_d = fan_out_estimate(ben, bank, scalar(d_src), scalar(d_tgt))
    actual_d = None
    district_secs = None
    if not SKIP_HEAVY:
        district_secs, act_d = timed(
            cur,
            "actual join on district",
            """
            SELECT count(*) FROM iceberg.srse.beneficiary src
            JOIN iceberg_silver.silver_txn.tbl_txn_bankdtl tgt ON src.district = tgt.district
            """,
        )
        actual_d = scalar(act_d)
        err_d = abs(est_d - actual_d) / actual_d * 100 if actual_d else 0
        direction = "under" if est_d < actual_d else "over"
        print(
            f"[probe] Q1 district: estimate={est_d:,.0f} actual={actual_d:,} "
            f"error={err_d:.2f}% ({direction}-estimate)"
        )
    else:
        print(f"[probe] Q1 district: estimate={est_d:,.0f} actual=SKIPPED")

    # Q1 selective key: m_id
    _, m_src = timed(cur, "approx_distinct id", "SELECT approx_distinct(id) FROM iceberg.srse.beneficiary")
    _, m_tgt = timed(
        cur, "approx_distinct m_id", "SELECT approx_distinct(m_id) FROM iceberg_silver.silver_txn.tbl_txn_bankdtl"
    )
    est_m = fan_out_estimate(ben, bank, scalar(m_src), scalar(m_tgt))
    _, act_m = timed(
        cur,
        "actual join on m_id",
        """
        SELECT count(*) FROM iceberg.srse.beneficiary src
        JOIN iceberg_silver.silver_txn.tbl_txn_bankdtl tgt ON src.id = tgt.m_id
        """,
    )
    actual_m = scalar(act_m)
    err_m = abs(est_m - actual_m) / actual_m * 100 if actual_m else 0
    print(f"[probe] Q1 m_id: estimate={est_m:,.0f} actual={actual_m:,} error={err_m:.2f}%")

    # Q5 fallback vs ANALYZE timing (beneficiary table)
    analyze_secs, _ = timed(cur, "ANALYZE beneficiary", "ANALYZE iceberg.srse.beneficiary")
    stats_secs, _ = timed(cur, "SHOW STATS beneficiary", "SHOW STATS FOR iceberg.srse.beneficiary")
    fallback_secs, _ = timed(
        cur,
        "live fallback count+approx_distinct",
        "SELECT count(*), approx_distinct(district) FROM iceberg.srse.beneficiary",
    )
    print(
        f"[probe] Q5 timings: ANALYZE={analyze_secs:.2f}s SHOW_STATS={stats_secs:.2f}s "
        f"fallback={fallback_secs:.2f}s"
    )

    # Q6 fuzzy blocking candidate count (blocking key only, no Levenshtein filter)
    fuzzy_secs, fuzzy = timed(
        cur,
        "fuzzy blocking join count",
        f"""
        SELECT count(*) FROM iceberg.srse.beneficiary src
        JOIN iceberg_silver.silver_txn.tbl_txn_bankdtl tgt
          ON substr(lower(src.father_name), 1, {PREFIX_LEN})
           = substr(lower(tgt.father_name), 1, {PREFIX_LEN})
        WHERE src.father_name IS NOT NULL AND tgt.father_name IS NOT NULL
        """,
    )
    print(f"[probe] Q6 fuzzy blocking pairs={scalar(fuzzy):,} in {fuzzy_secs:.2f}s")

    # Q8 group by high-cardinality
    grp_secs, grp = timed(
        cur,
        "GROUP BY id (high cardinality)",
        "SELECT count(*) FROM (SELECT id FROM iceberg.srse.beneficiary GROUP BY id) g",
    )
    print(f"[probe] Q8 grouped rows={scalar(grp):,} in {grp_secs:.2f}s")

    # Q2 — join with estimate just under ceiling (synthetic equi-key mod 117 at 200k scale)
    _, mod_dist = timed(
        cur,
        "approx_distinct mod(id,117)",
        "SELECT approx_distinct(mod(id, 117)) FROM iceberg.srse.beneficiary",
    )
    est_mod = fan_out_estimate(ben, bank, scalar(mod_dist), scalar(mod_dist))
    print(
        f"[probe] Q2 near-ceiling: est(mod117)={est_mod:,.0f} ceiling={CEILING:,} "
        f"({'under' if est_mod < CEILING else 'over'} ceiling)"
    )
    if not SKIP_NEAR_CEILING and not SKIP_HEAVY and est_mod < CEILING * 1.05:
        nc_secs, nc = timed(
            cur,
            "actual join mod(id,117)",
            """
            SELECT count(*) FROM iceberg.srse.beneficiary src
            JOIN iceberg_silver.silver_txn.tbl_txn_bankdtl tgt
              ON mod(src.id, 117) = mod(tgt.bank_id, 117)
            """,
        )
        print(f"[probe] Q2 actual mod117 join rows={scalar(nc):,} in {nc_secs:.2f}s")
    else:
        print("[probe] Q2 actual join SKIPPED (SKIP_* or estimate not near ceiling)")

    # Q3 — scope IN list cost (Presto): real district codes vs large bound lists (village depth proxy)
    district_codes = ["RJ-JPR", "RJ-JDH", "RJ-KOT", "RJ-AJM", "RJ-UDR", "RJ-ALW", "RJ-BKN"]
    sql7 = f"SELECT count(*) FROM iceberg.srse.beneficiary WHERE district_code IN ({in_list(district_codes)})"
    in_secs, in_rows = timed(cur, "scope IN n=7 (district codes)", sql7)
    print(f"[probe] Q3 IN(7) real district_code count={scalar(in_rows):,} in {in_secs:.3f}s")

    for n in (100, 500, 1000):
        ids = list(range(1, n + 1))
        sql = f"SELECT count(*) FROM iceberg.srse.beneficiary WHERE id IN ({', '.join(str(i) for i in ids)})"
        in_secs, in_rows = timed(cur, f"scope IN proxy n={n} (id list)", sql)
        print(f"[probe] Q3 IN({n}) id-list count={scalar(in_rows):,} in {in_secs:.3f}s")

    # Q7 — stream full m_id join result (same rows as match.csv for selective key)
    stream_sql = """
        SELECT src.id, tgt.m_id, src.district, tgt.district
        FROM iceberg.srse.beneficiary src
        JOIN iceberg_silver.silver_txn.tbl_txn_bankdtl tgt ON src.id = tgt.m_id
    """
    t0 = time.perf_counter()
    cur.execute(stream_sql)
    streamed = 0
    while True:
        batch = cur.fetchmany(5000)
        if not batch:
            break
        streamed += len(batch)
    stream_secs = time.perf_counter() - t0
    print(f"[probe] Q7 streamed m_id join rows={streamed:,} in {stream_secs:.2f}s (JDBC fetchmany)")

    print(f"[probe] Q2 district guard: estimate={est_d:,.0f} ({'REFUSE' if est_d > CEILING else 'allow'})")

    cur.close()
    conn.close()
    print("=== done ===")


if __name__ == "__main__":
    try:
        main()
    except Exception as ex:
        print(f"[probe] ERROR: {ex}", file=sys.stderr)
        sys.exit(1)
