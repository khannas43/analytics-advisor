# Performance at realistic volume (AA-17)

**These numbers are from a developer laptop, not the Government of Rajasthan
on-prem Presto cluster.** They are useful for comparing mechanisms (which costs
dominate, where guardrails refuse, how wrong an estimator gets on skew) — not
for quoting absolute production SLAs.

## Environment (2026-09-25)

| Item | Value |
|------|--------|
| Machine | Apple Silicon **arm64**, **32 GB** RAM |
| Presto | `prestodb/presto:0.297`, host port **8081** |
| Presto heap | **4G** (`docker/presto/jvm.config`, `-Xmx4G`) |
| Seed profile | `SEED_PROFILE=realistic` (default), deterministic from row `id` |
| Default volume | `ROWS=200000` — everyday compose unchanged |
| Opt-in volume | `ROWS=10000000` (`SEED_SEQ_A=1000` → grid 1000×10000) |
| Probe script | `docker/seed/volume_probe.py` (not part of `mvn test`) |

**Procedure:** `docker build -t aa-seed docker/seed`, point `PRESTO_URL` at local
Presto, run seed then `volume_probe.py` (see script header).

### Seeding wall-clock

| `ROWS` | Beneficiary insert | Join table `iceberg_silver.silver_txn.tbl_txn_bankdtl` |
|--------|-------------------|--------------------------------------------------------|
| 200,000 | **1.0–2.5 s** | **0.2–0.4 s** (~28,571 rows, `id % 7 = 0`) |
| 10,000,000 | **31.4 s** | **1.1 s** (~1,428,571 rows) |

Join overlap is imperfect by design (~8% deliberate `m_id` mismatch on the bank side).

### Why uniform 200k data was misleading (Part 0)

Legacy seed put **28,571 rows in each of 7 districts**. The fan-out estimator
treats every join key as uniformly distributed. That makes **low-cardinality
keys look predictable** while **real skew** (Jaipur vs a sparse district) breaks
the assumption.

The new **`realistic`** profile keeps 200k as default but skews districts
(~32% Jaipur … ~6% Alwar on `mod(id,100)` buckets) and uses a **long-tailed**
`father_name` / `mother_name` (common pool + `F_*` / `M_*` tail, ~3.4% nulls).

Example on 200k:

| District | Rows (approx.) |
|----------|----------------|
| Jaipur | 64,000 |
| Jodhpur | 40,000 |
| … | … |
| Alwar | 12,000 |

(`father_name` **~33k** distinct values vs 8 head names in the old uniform pool.)

---

## Measurements (200k realistic — primary run)

Tables: `iceberg.srse.beneficiary` **200,000** ×
`iceberg_silver.silver_txn.tbl_txn_bankdtl` **28,571**.

### 1 — Fan-out estimate vs actual on a skewed key

| Key | Estimated rows | Actual rows | Wall clock | Error | Setting change |
|-----|----------------|-------------|------------|-------|----------------|
| **`district`** (7 distinct, **skewed** populations) | 816,314,286 | **1,097,092,000** | **19.5 s** | **25.6% under-estimate** | **None** — guard still **refuses** (estimate ≫ 50M) |
| **`id = m_id`** (selective) | 29,113 | 28,571 | 0.23 s | 1.9% | None |

**Finding:** On a skewed key the product estimator is **directionally safe for
refusal** (under-estimate on a key already above ceiling) but **wrong by ~26%**
on magnitude — if a join were allowed nearer the ceiling, skew could push actual
fan-out **above** the estimate. No threshold change without on-prem skewed data.

#### How far wrong it can get: bounded by the key's cardinality, not by 26%

The 26% figure is this district distribution, not the estimator's limit. The
formula divides by distinct values, so for a key with `d` distinct values and
`n` rows a side:

- estimate is always `n² / d`
- actual is `Σ cᵢ²` over the groups, which equals `n² / d` only when the groups
  are **equal**, and rises to `n²` when one group holds everything

So the under-estimate factor runs from **1× (uniform) to d× (maximally
skewed)** — it grows with the cardinality of the key, and 26% is simply where
this particular skew happens to sit.

Measured on the same 200k table, forcing a maximally skewed key of 8 values:

| Key shape | Distinct | Estimate | Actual | Under by |
|---|---|---|---|---|
| `district` (moderate skew) | 7 | 5,714,285,714 | 7,680,000,000 | **1.26×** |
| one bulk value + 7 singletons | 8 | 5,000,000,000 | **39,997,200,056** | **8.0×** |

**Why this matters more than the 26%.** The A17 case is a district officer
against a **village-bound** table — a key with hundreds or thousands of distinct
values, and village populations are not equal. At `d = 1000` the worst case is a
thousandfold under-estimate: a join the guard approves at 40M could be 40
billion.

The ceiling is not the protection it looks like for high-cardinality skewed
keys. Two options, neither taken here because both want on-prem data first:

1. Feed the estimator **per-value frequencies** from `SHOW STATS` where the
   engine has them, rather than distinct counts alone.
2. Scale the estimate by a skew factor when the key's cardinality is high,
   accepting some false refusals in exchange for the tail.

**Recorded, not fixed.** Changing the estimator on laptop data would be tuning
against the wrong distribution — the point of this probe was to find out whether
the guard deserves the confidence `CLAUDE.md` places in it, and for a
low-cardinality key it does.

### 2 — Is the 50,000,000 ceiling survivable?

| Case | Estimated | Actual | Wall clock | Outcome |
|------|-----------|--------|------------|---------|
| `district` join | 816M | 1.09B | 19.5 s (when forced) | **Refused** by guard in product (`SRSE_ANALYSIS_MAX_ESTIMATED_ROWS`) |
| Synthetic **`mod(id,117)`** join (estimate **just under** ceiling) | 48,839,316 | 48,839,320 | **1.1–1.2 s** | **Completes** on laptop 4G Presto |

**Finding:** **50M is survivable here** for an equi-join that actually lands near
the limit (~49M rows). **District-shaped skew at 200k already blows the ceiling**
— the guard behaves as intended. **No ceiling change** from this run.

### 3 — Scope `IN` list at depth (village-bound table)

Product default: **`SRSE_SCOPE_MAX_IN_VALUES=1000`**.

| Predicate shape | `n` | Rows returned | Presto wall clock |
|-----------------|-----|---------------|-------------------|
| `district_code IN (7 real RJ codes)` | 7 | 200,000 | **0.09 s** |
| `id IN (1…n)` (proxy for **n** bound scope codes) | 100 | 100 | 0.15 s |
| same | 500 | 500 | 0.19 s |
| same | 1000 | 1,000 | **0.27 s** |

Resolver behaviour (district officer, binding depth below assignment) is covered in
`ScopePredicateCodeResolverTest` — codes collected under a Jaipur assignment at
village depth, not re-run as SQL here.

**Finding:** **1000 literals is cheap on Presto** at this scale (~0.3 s filter).
Operational risk is **officer path + JDBC binding + repeated queries**, not a single
IN parse. **Keep 1000** until real hierarchy imports show larger lists failing in
`AnalysisScopeFromService`.

### 4 — Audit write cost per query

| Mechanism | Measurement |
|-----------|-------------|
| PostgreSQL **100×** `INSERT INTO audit_event …` (single PL/pgSQL block) | **~21 ms** total (~0.2 ms/row) |
| Typical row shape | `query_shape` up to 8k chars (schema limit) |

Spring **`REQUIRES_NEW`** on `recordBestEffort` adds a transaction boundary per
request; not micro-benchmarked in-process here. **Finding:** Storage write is
**not material** on local Postgres; async audit remains a product choice, not
something this laptop run forces.

### 5 — Live fan-out fallback vs `ANALYZE`

On `beneficiary` (200k), after one **`ANALYZE`**:

| Step | Wall clock |
|------|------------|
| `ANALYZE iceberg.srse.beneficiary` | **1.4–2.1 s** |
| `SHOW STATS FOR …` | **0.05–0.10 s** |
| Fallback `count(*), approx_distinct(district)` | **0.05–0.07 s** |

**Finding:** **`ANALYZE` buys ~1–2 s once per table refresh**; fallback is fine
for ad-hoc laptops that never analyze. **No reordering change** — fallback already
 cheaper than analyze-on-every-match.

### 6 — Fuzzy blocking at scale

Blocking only (`substr(lower(father_name),1,3)` equi-join, **no** Levenshtein):

| Scale | Candidate pairs | Wall clock |
|-------|-----------------|------------|
| **200k × 28k** (realistic names) | **492,806,554** | **8–10 s** |
| **10M × 1.43M** (after opt-in seed) | **Not run** | Would be **billions+** candidates — laptop probe omitted intentionally |

Compare: CLAUDE.md **~145M** candidates at **86k × 20k** with the old **uniform**
32-name pool. Long tail + overlap **~3.4×** blocking fan-out at similar row counts.

**Finding:** Prefix **3** is load-bearing; **no default prefix change** without
officer UX and recall study. **10M fuzzy count** needs cluster-grade timeout/memory,
not a default CI test.

### 7 — CSV streaming to completion

| Path | Result |
|------|--------|
| `POST /api/analysis/match.csv` on **:8080** container (SRSE stack image) | **500** — stale/wrong request schema vs current API; not used as timing |
| **Equivalent:** JDBC stream of full **`id = m_id`** join (28,571 rows, 4 columns) | **28,571 rows in 0.17 s** (`fetchmany(5000)`) |
| Contract tests | `RecordMatchControllerTest#matchCsvStreamsAnAttachment` |

**Finding:** **Complete selective-key result streams end-to-end** on this Presto.
Proving **billion-row** CSV on a laptop was **not attempted** (same as guard
refusing district join). Production proof belongs on-cluster with a allowed match.

### 8 — Group By high-cardinality key

| Query | Output groups | Wall clock |
|-------|---------------|------------|
| `GROUP BY id` on beneficiary | **200,000** | **0.12–0.25 s** |

**Finding:** At 200k, group-by on unique id is cheap; caps (`SRSE_ANALYSIS_ROW_CAP`,
browser drop at 10k) bite in the **UI layer**, not this aggregate.

---

## Settings changed

| Setting | Changed? | Evidence |
|---------|----------|----------|
| `SRSE_ANALYSIS_MAX_ESTIMATED_ROWS` (50M) | **No** | ~49M join completes; district skew refused well above ceiling |
| `SRSE_SCOPE_MAX_IN_VALUES` (1000) | **No** | IN(1000) ≤ 0.27 s on Presto |
| `SRSE_ANALYSIS_BLOCKING_PREFIX_LEN` (3) | **No** | 492M blocking pairs at 200k — tightening is recall trade-off, not measured fix |
| Display / stream caps | **No** | — |
| `audit_event` indexes | **No** | Sub-ms inserts locally |

---

## Tests

- `mvn -f backend/pom.xml clean test` (JDK **17**): **498** tests, **~29 s**
  on this machine (Docker-only `*IT` not in default Surefire set).
- Seed grid sanity: `docker/seed/test_seed_grid.py` (run manually with pytest).
- Heavy Presto probes: **not** gated into `mvn test` (same class as
  `AnalysisEmittedSqlPrestoValidateIT`).

---

## Re-run cheat sheet

```bash
# Default stack volume
docker build -t aa-seed docker/seed
docker run --rm -e PRESTO_URL=jdbc:presto://host.docker.internal:8081/iceberg/srse aa-seed

# Opt-in 10M (plan ~30–60 s seed)
docker run --rm -e ROWS=10000000 -e PRESTO_URL=jdbc:presto://host.docker.internal:8081/iceberg/srse aa-seed

# Measurements (after seed)
docker run --rm --entrypoint python -v "$PWD/docker/seed:/seed" -w /seed \
  -e PRESTO_URL=jdbc:presto://host.docker.internal:8081/iceberg/srse aa-seed volume_probe.py
```

Restore default **200k** after a 10M experiment so local dev stays fast.
