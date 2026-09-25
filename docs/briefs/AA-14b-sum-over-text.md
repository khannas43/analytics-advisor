# Brief AA-14b — SUM over text: allow it, and report what was skipped

**Repo:** `analytics-advisor` · **Amends:** AA-14 Part 1 · **Follows:** AA-14

## Why this exists

AA-14 was built against a superseded version of Part 1. The brief originally
said to **refuse** `SUM`/`AVG` on a non-numeric column; that was changed to
**allow and report** before the work started, and the update did not reach the
implementation. `MatchGroupingSql.validateAggregateType` currently throws:

```
SUM requires a numeric column; gender is varchar.
Use COUNT, MIN/MAX on text or dates, or fix the type upstream.
```

Everything else in AA-14 is verified and stays. This changes one rule.

**The reason for the change:** Golden Layer data really does keep numbers in
text columns, so refusing blocks ordinary work. But a total that silently omits
rows is worse than no total, so the cast is allowed only if the number of rows
it could not add travels with it.

## What to build

**Allow `SUM` and `AVG` over a `TEXT` column** via `TRY_CAST`, and emit a
companion count beside each one:

```sql
sum(TRY_CAST(src.annual_income_total AS DOUBLE))            AS "sum_income",
count_if(src.annual_income_total IS NOT NULL
         AND TRY_CAST(src.annual_income_total AS DOUBLE) IS NULL)
                                                            AS "sum_income_unparseable"
```

Rules, in order of how easily each is got wrong:

1. **Count non-null failures only.** `SUM` has always ignored NULLs and an
   absent value is not a lost one. The figure that matters is rows that held
   something unaddable — that is what makes a total wrong. A column of NULLs
   reports **zero** unparseable, not one per row.
2. **Only where a cast happened.** A `SUM` over a genuinely numeric column must
   not grow a companion column of zeroes.
3. **The companion follows the aggregate everywhere** — NDJSON, `match.csv`,
   the grid. A total that sheds its caveat on the way into a spreadsheet is the
   exact failure this design prevents.
4. **Still refuse `BOOLEAN`, `TEMPORAL` and `UNKNOWN`.** `TRY_CAST(date AS
   DOUBLE)` is null for every row, so the result would be a confident zero. Only
   `NUMBER` (uncast) and `TEXT` (cast) are summable.
5. **`MIN`/`MAX` stay uncast**, exactly as built. They already mean lexicographic
   ordering, and casting would silently redefine the answer.

## Frontend

A non-zero unparseable count must be **impossible to miss** — shown beside the
total, not as one more column to scroll past. Zero can be quiet, or hidden
entirely.

## Tests

- `SUM` over a text column emits both columns, and the count is right against
  data with a known number of bad values.
- A text column of NULLs reports zero unparseable.
- No companion column for a `SUM` over a numeric column.
- `SUM` on a date or boolean is still refused.
- `MIN`/`MAX` on text unchanged and uncast.
- The companion appears in the CSV as well as the stream.

## Acceptance

- `mvn -f backend/pom.xml test` green on **JDK 17**. Report the count — **475**.
- Live: `SUM` over a text column holding some non-numeric values, grouped by
  `district`, as `jaipurofficer`. Report the total **and** the unparseable count,
  and confirm the count matches the data.

## Also worth a moment

The fan-out refusal now reads:

> …about 816,302,041 rows processed before the join (before grouping) (limit
> 50,000,000). That estimate is rows processed before grouping; the grouped
> result itself may be much smaller.

It says "before grouping" three times. Say it once.
