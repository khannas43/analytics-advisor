# Brief AA-17 — Performance at realistic volumes

**Repo:** `analytics-advisor` · **Covers:** `PRODUCT_PLAN.md` 9.3
**Follows:** AA-16 (`988114e`)

## What this is

A **measurement** exercise, not an optimisation one. The deliverable is a
document with numbers in it — `docs/performance-at-volume.md`, in the shape of
the existing `docs/chain-fanout-probe.md`.

Change code only where a measurement shows a problem, and say which measurement
forced each change. "Made it faster" without a before and after is not an
outcome here.

## Part 0 — Why the current data proves nothing

`iceberg.srse.beneficiary` holds 200,000 rows across 7 districts — **28,571
each, to within one row**. Every distinct-value count is round and every group
is the same size.

That is the friendliest possible input to the fan-out guard, whose estimate is
`sourceRows × targetRows / ∏ max(sourceDistinct, targetDistinct)`. That formula
is *exact* for a uniform join key and increasingly wrong as the key skews,
because it assumes every value appears equally often. Real district populations
differ by an order of magnitude; Jaipur is not Jaisalmer.

So the estimate landing within 0.9% of ground truth on this data, as recorded in
`CLAUDE.md`, tells us the arithmetic is right. It does not tell us the guard
works.

**Volume alone will not fix that. The data must be skewed as well as large.**

## Part 1 — The data

Extend `docker/seed/seed.py`, keeping the existing `INSERT … SELECT` over
`UNNEST(sequence(...))` approach rather than generating files.

- **Target 10 million rows** for the main table. Note the wall-clock cost of
  seeding, since whoever repeats this needs to plan for it.
- **Make the distributions skewed and realistic:**
  - District populations differing by roughly 10:1 across the set, not uniform.
  - `father_name` / `mother_name` with a long tail — a few very common names and
    many rare ones. A uniform 32 names is what makes fuzzy blocking look cheap.
  - Nulls present in the columns that will carry them in real data.
- Keep a **second table** for two-table joins, with a realistic overlap rather
  than a perfect one.
- The 200,000-row default must keep working unchanged, so the everyday local
  stack stays quick. Volume is opt-in via `ROWS`.

## Part 2 — What to measure

Every number below was set by estimate and has never been checked against
volume.

| # | Question | Why it matters |
|---|---|---|
| 1 | **Fan-out estimate vs actual on a *skewed* key.** | The guard's whole purpose. If it under-estimates on skew, it waves through the queries it exists to stop. |
| 2 | **Is `SRSE_ANALYSIS_MAX_ESTIMATED_ROWS` (50,000,000) survivable?** Run a join estimated just under it. | A ceiling that still kills the cluster is not a ceiling. It may need lowering — or raising, if 50M is trivially fast. |
| 3 | **Scope `IN` list at depth.** A district officer against a table bound only at village level (A17). | A17 predicted thousands of bound values. Measure where it stops being acceptable and set `SRSE_SCOPE_MAX_IN_VALUES` from the measurement rather than the guess. |
| 4 | **Audit write cost per query.** | It is on every officer request. Measure the added latency; if it is material, that is an argument for the async path Q6 deliberately did not take. |
| 5 | **The live fan-out fallback** — `count(*)` + `approx_distinct` when `ANALYZE` has not run. | Two aggregates on the officer's path before their query even starts. Measure both paths and quantify what `ANALYZE` buys (2.2.9). |
| 6 | **Fuzzy blocking at scale.** `CLAUDE.md` records ~145M candidate rows from an 86k × 20k name match. Repeat at 10M with a long-tailed name distribution. | The blocking prefix is the only thing standing between a fuzzy match and a cross product. |
| 7 | **CSV streaming to completion** at full result size. | Memory and time. The contract says the download is the *complete* result; prove it completes. |
| 8 | **Group By on a high-cardinality key.** | Groups become the output rows, so the caps apply differently. |

For each: record the input shape, the measured number, the wall clock, and
whether it changes a setting.

## Part 3 — Reporting honestly

- **A laptop is not the client's cluster.** Say so at the top. These numbers are
  comparative — they show which things scale badly relative to each other, and
  roughly where the cliffs are. They do not predict on-prem absolute timings.
- Record the machine, the container memory limits, the Presto heap
  (`docker/presto/jvm.config` currently overrides to 4G) and the row counts, so
  a later run can be compared to this one.
- Where a measurement is dominated by the local setup rather than the product,
  say that instead of reporting it as a product number.

## Part 4 — What may change, and what may not

**May change on evidence:** the fan-out ceiling, `SRSE_SCOPE_MAX_IN_VALUES`, the
blocking prefix default, the display and stream caps, index choices on
`audit_event`, and the ordering of aggregates in the fan-out fallback.

**May not change to make a number look better:** deny-by-default, the scope
predicate, parameter binding, the audit write on exports, or any guardrail's
*existence*. If a control is too slow, that is a finding to report — not a
licence to remove it.

If a measurement suggests removing a safety property, stop and write it up
rather than doing it.

## Part 5 — Tests

This package adds few unit tests; its output is a document. But:

- The seed must be deterministic enough that a repeat run produces comparable
  numbers. Seed the randomness.
- Any setting changed gets its existing test updated, with the measurement cited
  in the commit.
- Do not add long-running tests to the default `mvn test`. Gate anything heavy
  the way `AnalysisEmittedSqlPrestoValidateIT` is gated.

## Acceptance

- `docs/performance-at-volume.md` exists and answers all eight questions in
  Part 2 with numbers, or says why one could not be measured.
- `mvn -f backend/pom.xml test` still green on **JDK 17** — report the count
  (**498**) — and still fast.
- The 200,000-row default seed still works.
- Report which settings you changed and the measurement behind each.

## Do not

- Do not tune a guardrail's threshold to make a slow query pass.
- Do not remove a safety property for speed.
- Do not report absolute timings as if they were the client's.
- Do not leave the heavy seed as the default.
