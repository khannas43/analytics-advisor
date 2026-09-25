"""
Synthetic beneficiary data seed for local SRSE development.

Generates Jan-Aadhaar-shaped rows in Presto (one INSERT … SELECT) and writes a
compact Iceberg table. Client-side VALUES batches fragment the table and hit
Presto's 1MB query-text cap.

Distributions are deterministic from row id (repeatable across runs). Opt-in
volume via ROWS=10000000; default ROWS=200000 keeps the everyday stack fast.

SEED_PROFILE:
  realistic (default) — skewed districts, long-tailed names, nulls
  uniform        — legacy-like equal district buckets (200k dev only)
"""
from __future__ import annotations

import os
import sys
import time

import prestodb

PRESTO_URL = os.environ.get("PRESTO_URL", "jdbc:presto://presto:8080/iceberg/srse")
ROWS = int(os.environ.get("ROWS", "200000"))
# Presto sequence() is capped at 10_000; cross-join two sequences for row count.
SEQ_A = int(os.environ.get("SEED_SEQ_A", "1000"))
SEED_PROFILE = os.environ.get("SEED_PROFILE", "realistic").strip().lower()
SEED_JOIN_TABLE = os.environ.get("SEED_JOIN_TABLE", "true").strip().lower() in ("1", "true", "yes")
# Fraction of beneficiary ids that appear in the Silver bank table (imperfect overlap).
JOIN_SAMPLE_MOD = int(os.environ.get("SEED_JOIN_SAMPLE_MOD", "7"))  # id % 7 == 0 → ~14.3%

CREATE_TABLE = """
CREATE TABLE IF NOT EXISTS beneficiary (
    id                   BIGINT,
    age_years            INTEGER,
    gender               VARCHAR,
    district             VARCHAR,
    district_code        VARCHAR,
    annual_income_total  DECIMAL(12,2),
    marital_status       VARCHAR,
    is_domicile_holder   BOOLEAN,
    ration_card_category VARCHAR,
    census_category      VARCHAR,
    community            VARCHAR,
    disability_pct       INTEGER,
    tsp_classification   VARCHAR,
    class_passed         VARCHAR,
    is_girl_child_of_hof  BOOLEAN,
    has_vehicle          BOOLEAN,
    land_holding_sqyd    DECIMAL(10,2),
    relationship_to_hof  VARCHAR,
    father_name          VARCHAR,
    mother_name          VARCHAR,
    annual_income_fy2627 DECIMAL(12,2),
    annual_income_fy2526 DECIMAL(12,2),
    annual_income_fy2425 DECIMAL(12,2),
    annual_income_fy2324 DECIMAL(12,2),
    annual_income_fy2223 DECIMAL(12,2),
    annual_income_fy2122 DECIMAL(12,2),
    annual_income_fy2021 DECIMAL(12,2),
    annual_income_fy1920 DECIMAL(12,2),
    annual_income_fy1819 DECIMAL(12,2),
    annual_income_fy1718 DECIMAL(12,2),
    age_band             VARCHAR,
    last_refreshed_at    VARCHAR
)
"""


def _sql_string_array(values: list[str]) -> str:
    escaped = ["'" + v.replace("'", "''") + "'" for v in values]
    return "ARRAY[" + ", ".join(escaped) + "]"


def _vary_name_expr(base: str, id_col: str = "g.id") -> str:
    """Deterministic single-character edit (~18% of rows), ported from _typo()."""
    i = f"(1 + mod({id_col}, greatest(length({base}) - 2, 1)))"
    return f"""CASE
      WHEN length({base}) < 3 THEN {base}
      WHEN mod({id_col}, 100) >= 18 THEN {base}
      ELSE CASE mod({id_col}, 3)
        WHEN 0 THEN concat(substr({base}, 1, {i} - 1), substr({base}, {i} + 1))
        WHEN 1 THEN concat(substr({base}, 1, {i}), substr({base}, {i}, 1), substr({base}, {i} + 1))
        ELSE concat(substr({base}, 1, {i} - 1), substr({base}, {i} + 1, 1), substr({base}, {i}, 1), substr({base}, {i} + 2))
      END
    END"""


def _grid_factors(total_rows: int) -> tuple[int, int]:
    a = min(SEQ_A, total_rows)
    if a <= 0:
        raise ValueError("ROWS must be positive")
    b = (total_rows + a - 1) // a
    if b > 10_000:
        raise ValueError(
            f"ROWS={total_rows} needs SEQ_B={b} > 10_000 (Presto sequence cap); "
            "raise SEED_SEQ_A or lower ROWS"
        )
    return a, b


def _district_expr(profile: str) -> str:
    districts = _sql_string_array(
        ["Jaipur", "Jodhpur", "Udaipur", "Kota", "Ajmer", "Bikaner", "Alwar"]
    )
    if profile == "uniform":
        return f"element_at({districts}, 1 + mod(g.id, cardinality({districts})))"
    # ~10:1 population skew: Jaipur largest, Bikaner smallest (cumulative buckets on mod 100).
    return f"""CASE
      WHEN mod(g.id, 100) < 32 THEN 'Jaipur'
      WHEN mod(g.id, 100) < 52 THEN 'Jodhpur'
      WHEN mod(g.id, 100) < 66 THEN 'Kota'
      WHEN mod(g.id, 100) < 76 THEN 'Ajmer'
      WHEN mod(g.id, 100) < 84 THEN 'Udaipur'
      WHEN mod(g.id, 100) < 90 THEN 'Alwar'
      ELSE 'Bikaner'
    END"""


def _district_code_expr(profile: str) -> str:
    if profile == "uniform":
        codes = _sql_string_array(
            ["RJ-JPR", "RJ-JDH", "RJ-UDR", "RJ-KOT", "RJ-AJM", "RJ-BKN", "RJ-ALW"]
        )
        return f"element_at({codes}, 1 + mod(g.id, cardinality({codes})))"
    return """CASE
      WHEN mod(g.id, 100) < 32 THEN 'RJ-JPR'
      WHEN mod(g.id, 100) < 52 THEN 'RJ-JDH'
      WHEN mod(g.id, 100) < 66 THEN 'RJ-KOT'
      WHEN mod(g.id, 100) < 76 THEN 'RJ-AJM'
      WHEN mod(g.id, 100) < 84 THEN 'RJ-UDR'
      WHEN mod(g.id, 100) < 90 THEN 'RJ-ALW'
      ELSE 'RJ-BKN'
    END"""


def _long_tail_name_expr(id_col: str, common_pool: str, prefix: str) -> str:
    """Head names (~35% rows) + long tail of synthetic rare names."""
    common = f"element_at({common_pool}, 1 + mod({id_col}, cardinality({common_pool})))"
    tail = f"concat('{prefix}', cast(mod({id_col} * 7919, 50000) AS varchar))"
    base = f"""CASE
      WHEN mod({id_col}, 100) < 35 THEN {common}
      ELSE {tail}
    END"""
    varied = _vary_name_expr(base, id_col)
    return f"""CASE
      WHEN mod({id_col}, 29) = 0 THEN cast(null AS varchar)
      ELSE {varied}
    END"""


def build_insert_select(total_rows: int, profile: str = SEED_PROFILE) -> str:
    seq_a, seq_b = _grid_factors(total_rows)

    communities = _sql_string_array(["GENERAL", "SAHARIYA", "KATHODI", "KHAIRWA"])
    ration = _sql_string_array(["NONE", "BPL", "ANTYODAYA"])
    census = _sql_string_array(["APL", "BPL", "EWS"])
    tsp = _sql_string_array(["TSP", "NON_TSP"])
    marital = _sql_string_array(["SINGLE", "MARRIED", "DIVORCED", "WIDOWED"])
    relationship = _sql_string_array([
        "SELF", "SON", "DAUGHTER", "FATHER", "MOTHER", "SPOUSE",
        "GRANDSON", "GRANDDAUGHTER", "BROTHER", "SISTER", "OTHER",
    ])
    father_pool = _sql_string_array(
        ["Ramesh", "Suresh", "Mahesh", "Rajesh", "Dinesh", "Ganesh", "Naresh", "Mukesh"]
    )
    mother_pool = _sql_string_array(
        ["Sunita", "Kavita", "Anita", "Rekha", "Meena", "Sita", "Geeta", "Lata"]
    )
    class_passed = _sql_string_array(
        ["NURSERY", "KG"]
        + [str(n) for n in range(1, 13)]
        + ["GRADUATE"]
    )
    disability = "ARRAY[0, 0, 0, 40, 60, 80]"

    district = _district_expr(profile)
    district_code = _district_code_expr(profile)
    father_name = _long_tail_name_expr("g.id", father_pool, "F_")
    mother_name = _long_tail_name_expr("g.id", mother_pool, "M_")

    return f"""
INSERT INTO beneficiary
SELECT
  g.id,
  CAST(mod(g.id, 91) AS integer) AS age_years,
  element_at(ARRAY['MALE', 'FEMALE'], 1 + mod(g.id, 2)) AS gender,
  {district} AS district,
  {district_code} AS district_code,
  CAST(round(mod(g.id * 1237, 300000), 2) AS decimal(12, 2)) AS annual_income_total,
  element_at({marital}, 1 + mod(g.id, cardinality({marital}))) AS marital_status,
  (mod(g.id, 10) <> 0) AS is_domicile_holder,
  element_at({ration}, 1 + mod(g.id, cardinality({ration}))) AS ration_card_category,
  element_at({census}, 1 + mod(g.id, cardinality({census}))) AS census_category,
  element_at({communities}, 1 + mod(g.id, cardinality({communities}))) AS community,
  element_at({disability}, 1 + mod(g.id, cardinality({disability}))) AS disability_pct,
  element_at({tsp}, 1 + mod(g.id, 2)) AS tsp_classification,
  element_at({class_passed}, 1 + mod(g.id, cardinality({class_passed}))) AS class_passed,
  (
    mod(g.id, 2) = 1
    AND mod(g.id, 91) < 18
    AND mod(g.id, 10) < 3
  ) AS is_girl_child_of_hof,
  (mod(g.id, 100) < 35) AS has_vehicle,
  CAST(round(mod(g.id * 17, 50000) / 100.0, 2) AS decimal(10, 2)) AS land_holding_sqyd,
  element_at({relationship}, 1 + mod(g.id, cardinality({relationship}))) AS relationship_to_hof,
  {father_name} AS father_name,
  {mother_name} AS mother_name,
  CAST(round(mod(g.id * 1237, 300000) * (0.7 + mod(g.id, 600) / 1000.0), 2) AS decimal(12, 2)) AS annual_income_fy2627,
  CAST(round(mod(g.id * 1237, 300000) * (0.7 + mod(g.id + 1, 600) / 1000.0), 2) AS decimal(12, 2)) AS annual_income_fy2526,
  CAST(round(mod(g.id * 1237, 300000) * (0.7 + mod(g.id + 2, 600) / 1000.0), 2) AS decimal(12, 2)) AS annual_income_fy2425,
  CAST(round(mod(g.id * 1237, 300000) * (0.7 + mod(g.id + 3, 600) / 1000.0), 2) AS decimal(12, 2)) AS annual_income_fy2324,
  CAST(round(mod(g.id * 1237, 300000) * (0.7 + mod(g.id + 4, 600) / 1000.0), 2) AS decimal(12, 2)) AS annual_income_fy2223,
  CAST(round(mod(g.id * 1237, 300000) * (0.7 + mod(g.id + 5, 600) / 1000.0), 2) AS decimal(12, 2)) AS annual_income_fy2122,
  CAST(round(mod(g.id * 1237, 300000) * (0.7 + mod(g.id + 6, 600) / 1000.0), 2) AS decimal(12, 2)) AS annual_income_fy2021,
  CAST(round(mod(g.id * 1237, 300000) * (0.7 + mod(g.id + 7, 600) / 1000.0), 2) AS decimal(12, 2)) AS annual_income_fy1920,
  CAST(round(mod(g.id * 1237, 300000) * (0.7 + mod(g.id + 8, 600) / 1000.0), 2) AS decimal(12, 2)) AS annual_income_fy1819,
  CAST(round(mod(g.id * 1237, 300000) * (0.7 + mod(g.id + 9, 600) / 1000.0), 2) AS decimal(12, 2)) AS annual_income_fy1718,
  CASE
    WHEN mod(g.id, 91) < 18 THEN '0-17'
    WHEN mod(g.id, 91) < 35 THEN '18-34'
    WHEN mod(g.id, 91) < 55 THEN '35-54'
    ELSE '55+'
  END AS age_band,
  '2026-01-01' AS last_refreshed_at
FROM (
  SELECT CAST((a.i - 1) * {seq_b} + b.j AS bigint) AS id
  FROM UNNEST(sequence(1, {seq_a})) AS a(i)
  CROSS JOIN UNNEST(sequence(1, {seq_b})) AS b(j)
) g
WHERE g.id <= {total_rows}
"""


def build_join_table_sql(sample_mod: int = JOIN_SAMPLE_MOD) -> str:
    """Silver bank detail — subset of beneficiaries, ~8% deliberate m_id mismatch."""
    return f"""
CREATE TABLE iceberg_silver.silver_txn.tbl_txn_bankdtl AS
SELECT
  b.id AS bank_id,
  CASE WHEN mod(b.id, 100) < 92 THEN b.id ELSE b.id + 1 END AS m_id,
  CAST(b.id AS varchar) AS account_no,
  b.father_name,
  b.district_code,
  b.district
FROM iceberg.srse.beneficiary b
WHERE mod(b.id, {sample_mod}) = 0
"""


def parse_presto_url(url: str):
    """Parse jdbc:presto://host:port/catalog/schema into components."""
    prefix = "jdbc:presto://"
    if not url.startswith(prefix):
        raise ValueError(f"PRESTO_URL must start with {prefix!r}, got: {url!r}")
    rest = url[len(prefix):]
    slash = rest.find("/")
    if slash < 0:
        raise ValueError(f"PRESTO_URL missing catalog/schema path: {url!r}")
    host_port = rest[:slash]
    path = rest[slash + 1:]
    parts = path.split("/")
    if len(parts) != 2 or not parts[0] or not parts[1]:
        raise ValueError(f"PRESTO_URL must be jdbc:presto://host:port/catalog/schema, got: {url!r}")
    catalog, schema = parts
    if ":" in host_port:
        host, port_str = host_port.rsplit(":", 1)
        port = int(port_str)
    else:
        host = host_port
        port = 8080
    if not host:
        raise ValueError(f"PRESTO_URL missing host: {url!r}")
    return host, port, catalog, schema


def _execute(cursor, sql: str):
    cursor.execute(sql)
    if cursor.description is not None:
        return cursor.fetchall()
    return None


def main():
    try:
        seq_a, seq_b = _grid_factors(ROWS)
        print(
            f"[seed] target={PRESTO_URL} rows={ROWS} profile={SEED_PROFILE} "
            f"grid={seq_a}x{seq_b} join_table={SEED_JOIN_TABLE}"
        )
        host, port, catalog, schema = parse_presto_url(PRESTO_URL)
        print(f"[seed] connecting host={host} port={port} catalog={catalog} schema={schema}")

        conn = prestodb.dbapi.connect(
            host=host,
            port=port,
            user=os.environ.get("PRESTO_USER", "seed"),
            catalog=catalog,
            schema=schema,
        )
        cursor = conn.cursor()

        _execute(cursor, f"CREATE SCHEMA IF NOT EXISTS {catalog}.{schema}")
        print(f"[seed] schema {catalog}.{schema} ready")

        _execute(cursor, "DROP TABLE IF EXISTS beneficiary")
        _execute(cursor, CREATE_TABLE)
        print("[seed] table beneficiary ready")

        insert_sql = build_insert_select(ROWS, SEED_PROFILE)
        print(f"[seed] inserting {ROWS} rows in one statement …")
        t0 = time.perf_counter()
        _execute(cursor, insert_sql)
        insert_secs = time.perf_counter() - t0

        count_rows = _execute(cursor, "SELECT count(*) FROM beneficiary")
        count = count_rows[0][0] if count_rows else ROWS
        print(f"[seed] beneficiary done — {count} rows in {insert_secs:.1f}s")

        if SEED_JOIN_TABLE:
            _execute(cursor, "CREATE SCHEMA IF NOT EXISTS iceberg_silver.silver_txn")
            _execute(cursor, "DROP TABLE IF EXISTS iceberg_silver.silver_txn.tbl_txn_bankdtl")
            print("[seed] building iceberg_silver.silver_txn.tbl_txn_bankdtl …")
            t1 = time.perf_counter()
            _execute(cursor, build_join_table_sql())
            join_secs = time.perf_counter() - t1
            bank_count = _execute(cursor, "SELECT count(*) FROM iceberg_silver.silver_txn.tbl_txn_bankdtl")
            bank_n = bank_count[0][0] if bank_count else 0
            print(f"[seed] join table done — {bank_n} rows in {join_secs:.1f}s")

        cursor.close()
        conn.close()
    except Exception as e:
        print(f"[seed] ERROR: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
