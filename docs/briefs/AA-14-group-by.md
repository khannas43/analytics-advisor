# Brief AA-14 — Group By and aggregates

**Repo:** `analytics-advisor` · **Covers:** `PRODUCT_PLAN.md` 5.5, and closes 7.2.6
**Follows:** AA-13 (`02455cd`)

## Why this one is different

Every feature so far has **filtered** a result. This one changes its **shape**:
the officer stops getting rows and starts getting numbers. That breaks
assumptions three existing mechanisms make — the row caps, the fan-out guard,
and type coercion — and each breaks in its own direction.

It is also the first place where a defect produces **a plausible wrong number**
rather than a missing row or an error. Nobody double-checks a total.

## Part 0 — Shape

Grouping applies to the existing match query, after the join, over the scoped
and ruled derived tables:

```sql
SELECT src.district AS "source_district",
       count(*)                      AS "count_all",
       sum(src.annual_income_total)  AS "sum_income"
FROM (SELECT * FROM iceberg.srse.beneficiary t
       WHERE district IN (?) AND (t.age_years > ?)) src
JOIN (...) tgt ON src.id = tgt.id
GROUP BY src.district
```

**7.2.6 is satisfied by construction and must be proved anyway.** The scope
filter lives inside the derived table, so it is applied before the join and
therefore before aggregation — an officer's `count(*)` cannot include another
district's rows. That is a consequence of AA-09's design rather than new work,
but it is exactly the kind of property that silently stops being true, and a
leak here is a *number* that looks right. Test it explicitly: the same grouped
query as a scoped officer and as a SuperAdmin must produce different counts, and
the officer's per-group counts must equal the SuperAdmin's for the groups they
share.

Aggregate with **no** grouping columns is valid and returns one row.

Grouping is **single-match only** — not multi-target. Aggregating across N
independent sub-matches means deciding what a shared group key even is, and that
is its own brief.

## Part 1 — The trap: a silently wrong total

`SUM` and `AVG` over a `varchar` column that happens to hold numbers is the case
to get right, and the obvious fix is the wrong one.

Reaching for `TRY_CAST` — as the match engine does — would make
`sum(TRY_CAST(col AS DOUBLE))` **silently skip every unparseable row**. In a
match, a `TRY_CAST` that fails costs a row that would not have matched anyway,
and the officer sees fewer results. In a sum it changes the answer, and there is
nothing on screen to suggest the total is missing anything.

So:

- **`SUM` / `AVG` / `MIN` / `MAX` on a non-`NUMBER` column are refused**, with a
  message naming the column and its type and suggesting `COUNT`, or fixing the
  type upstream. `CLAUDE.md` already takes this line for boolean and temporal
  coercion — *better to fail loudly than answer wrongly* — and a wrong total is
  the strongest case for it.
- Use `SqlTypeFamily` to decide, from the live column type. Do not guess from the
  column name.
- `MIN` / `MAX` on `TEXT` or `TEMPORAL` are meaningful and **allowed** — they are
  orderings, not arithmetic. Only `SUM` and `AVG` require `NUMBER`.
- `COUNT` works on anything, including `COUNT(DISTINCT col)`.

If you find a case where refusing is genuinely unhelpful, say so in the report
rather than adding a silent cast.

## Part 2 — The fan-out guard now measures the wrong thing

The guard estimates **join output rows** and refuses above
`SRSE_ANALYSIS_MAX_ESTIMATED_ROWS`. With grouping, that estimate is still the
right measure of *work* — the engine really does process those rows — but it is
no longer the size of the *answer*.

Keep the guard. **Change the message.** An officer who asked for seven district
totals and is told "estimated 816,302,041 rows" will reasonably conclude the
product is broken. Say that the estimate is of rows processed before grouping,
and that the result itself would be small.

Do not raise or bypass the ceiling for grouped queries. The work is real.

## Part 3 — Caps mean something different

The 10,000-row render limit and 200,000-row stream stop were sized for
row-level results.

- They apply unchanged, to **output rows** — which for a grouped query means
  groups. That is correct as it stands: grouping by a high-cardinality column
  produces as many groups as rows, and that result deserves the same treatment.
- **`COUNT(DISTINCT ...)` is expensive at scale.** Presto's `approx_distinct` is
  what the fan-out guard already uses. Offer exact `COUNT(DISTINCT)` but say in
  the UI that it is the costly one; do not silently substitute the approximation,
  because an approximate count presented as exact is the same class of error as
  Part 1.

## Part 4 — Things that will surprise someone

- **NULL forms its own group.** Standard SQL, frequently surprising. Label it in
  the UI rather than leaving a blank row.
- **Grouping columns go through the registry gates** like every other
  identifier — registered, live, not hidden. No new path.
- **The group key must be a column, not an expression** in this brief. Grouping
  by `year(dob)` is desirable and is not in scope; adding it means officer-authored
  expressions reaching SQL, which is a different safety conversation.
- **Cap the number of grouping columns and aggregates.** Reuse the existing
  `maxGroupColumns` convention rather than inventing a second limit, and report
  both in `AnalysisLimitsResponse` so the UI can enforce what the server does.

## Part 5 — Surfaces to carry it through

Grouping changes the result shape, so everything that consumes a result needs to
cope:

| Surface | What changes |
|---|---|
| `match` (NDJSON) | Columns are group keys plus aggregates |
| `match.csv` | Same, streamed |
| `match.sql` | Preview shows `GROUP BY`, scope and rules |
| Audit | `query_shape` includes `GROUP BY`; values still bound |
| Results grid | New column set; sorting by an aggregate is the obvious want |
| Charts | Grouped data is what `ChartsSection` was always for — wire it |

`comparison-summary` is **not** part of this — it is its own aggregate over
matched rows and grouping it is a separate question.

## Part 6 — Tests

- Scope holds through aggregation: officer's counts differ from SuperAdmin's,
  and agree on shared groups (7.2.6, the one that matters).
- `SUM` on a text column is refused, with the type in the message.
- `MIN`/`MAX` on text and dates are allowed.
- Aggregate with no grouping column returns one row.
- NULL group present and labelled.
- Grouping column unregistered or hidden → refused.
- Emitted SQL parses (`EmittedSqlParsesTest`) and validates
  (`AnalysisEmittedSqlPrestoValidateIT`) — the analyzer catches aggregate/
  grouping mismatches a parser will not.
- Rule + scope + grouping together, asserted on the emitted SQL.
- Audit row for a grouped query carries `?`, not values.

## Acceptance

- `mvn -f backend/pom.xml test` green on **JDK 17**. Report the count — **465**.
- `npm run build` / `npm run test` clean.
- **Live, against the local Presto**, as `jaipurofficer` and `superadmin`:
  group `beneficiary` by `district` with `count(*)`. The officer must get **one
  row, Jaipur**, and its count must equal the Jaipur row in the SuperAdmin
  result. Report both.
- Also report what a `SUM` on a text column does, in words.

## Do not

- Do not `TRY_CAST` inside `SUM` or `AVG`.
- Do not substitute `approx_distinct` for `COUNT(DISTINCT)` without saying so.
- Do not relax the fan-out ceiling for grouped queries.
- Do not add `HAVING` or expression group keys — both are worth having, neither
  is in this brief.
