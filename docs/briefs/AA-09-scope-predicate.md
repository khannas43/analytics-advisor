# Brief AA-09 — Inject the scope predicate into every query

**Repo:** `analytics-advisor` · **Covers:** `PRODUCT_PLAN.md` 7.2.3–7.2.9
**Follows:** AA-08 (`f4f60cc`) and the policy correction (`7839346`)

## Why this one is different

Every other feature here fails visibly. This one fails silently and looks like
working software: an officer sees rows, the numbers look plausible, and they
belong to another district.

Three consecutive packages in this area shipped a permissive default —
AA-06's path normalisation, AA-07's vacuous-truth escalation, AA-08's
fail-open bypass and the binding chooser corrected in `7839346`. All four were
invisible to a green suite and all four erred toward more access. Assume this
brief contains one too, and build so it is findable.

## Part 0 — Emit the filter as a derived table, not a WHERE clause

**This is the central decision. Build it this way.**

Wrap each scoped side in an inline view, so the filter is part of what the side
*is* rather than a predicate on the join:

```sql
FROM (SELECT * FROM iceberg.srse.beneficiary WHERE district IN (?, ?)) src
JOIN (SELECT * FROM iceberg_silver.silver_txn.tbl_txn_bankdtl WHERE district IN (?, ?)) tgt
  ON src.id = tgt.id
```

The alternative — adding the predicate to ON or WHERE per join type — is a trap
this codebase has already fallen into once. `CLAUDE.md` records that a fuzzy
predicate in WHERE silently converted LEFT back to INNER by filtering away the
unmatched rows; a scope predicate in the wrong clause does the same thing, and
on FULL joins puts out-of-scope rows back on screen as unmatched rows carrying
their own columns.

With a derived table:

- INNER, LEFT, RIGHT and FULL all behave correctly with no per-join-type logic.
- Out-of-scope rows cannot appear on either side, matched or unmatched.
- Dedup, comparison groups, `matched_on` and `match_status` are untouched.

Keep the existing SQL **byte-identical when no filter applies** — a SuperAdmin,
a shared-reference table, or a bypassing mode. Do not wrap a side you are not
filtering. `EmittedSqlParsesTest` and the recorded manual runs depend on that.

## Part 1 — Codes are bound, never interpolated

The predicate is `column IN (?, ?, …)` with the codes as bound parameters.

This is not negotiable and it is not the same as the catalog/schema case.
`CLAUDE.md`'s injection rule allows interpolation only for identifiers that
Presto cannot bind, and only after allow-listing. A node code is a **value**.
Bind it.

The bound column name comes from `table_scope_binding`, which AA-08 validates
against the live table on save — it never comes from a request.

## Part 2 — Resolving the codes

`AssignmentNode` currently carries only `depth`. It needs the node's **path**
and **code**, because the predicate needs actual values.

For each dimension the officer is assigned in, with the chosen binding at depth
`d` (see `7839346` for why it is the coarsest binding at least as specific as
their deepest assignment):

- For every assignment node, collect every node at depth `d` whose `path` starts
  with that assignment's path — the assignment itself when it is already at `d`.
- The predicate is the `code` values of that set, deduplicated.
- **AND across dimensions, OR within one** (§7.1 rule 1) — one `IN` per
  dimension, `AND`ed together.

**Cap the list.** A17 warns this is where it hurts: a district officer against a
village-bound table needs every village in the district. Add
`SRSE_SCOPE_MAX_IN_VALUES` (suggest 1000) and **refuse with a message naming the
table, the dimension and the count** rather than emitting a query that will
crawl. A refusal an admin can act on — by binding a coarser column — beats a
timeout.

**An empty code set must produce a query returning nothing**, never an absent
filter. `IN ()` is invalid SQL in Presto, so emit an explicit false predicate.
This is the single likeliest place for a hole: "no codes, so no filter" is one
careless line away from "no codes, so everything".

## Part 3 — Every path, without exception (7.2.4)

A path that forgets the filter is the breach. All of these plan or execute SQL:

| Endpoint / path | Note |
|---|---|
| `POST /api/analysis/match` | NDJSON stream |
| `POST /api/analysis/match.csv` | **re-runs the match** — filter it independently |
| `POST /api/analysis/match.sql` | preview; see 7.2.7 below |
| `POST /api/analysis/match/comparison-summary` | aggregates over the join |
| `POST /api/analysis/suggest-keys` | both the source sample and the target scan |
| `POST /api/analysis/match-multi` | hub **and every target** |
| `POST /api/analysis/match-multi.csv` | same |
| Fan-out guard | see 7.2.8 |

The right way to be sure is structural: make the planner the **only** place a
`FROM <table>` is produced, so a path cannot omit the filter without failing to
compile. If that is not achievable, say so in your report and list what you
checked by hand instead.

**7.2.7 — the SQL preview must show the injected predicate.** A preview that
hides it is worse than no preview: an officer comparing a hand-written query to
the preview would conclude the tool is lying. Show the real SQL, placeholders
and all.

**7.2.8 — the fan-out estimate must account for the filter.** It currently uses
whole-table statistics, so a scoped query gets refused on rows the officer will
never see. Scale the estimate by the filtered selectivity where statistics allow,
and where they do not, say in the refusal message that the estimate is
unfiltered. Do not simply raise the ceiling.

## Part 4 — Negative tests (7.2.9)

Your plan already calls this the suite that matters most. It must prove a scoped
officer **cannot reach another district through any endpoint**, not that the
happy path works.

- One test per row of Part 3's table, asserting the emitted SQL carries the
  filter and the bound parameters match the officer's codes.
- A scoped officer against a table bound in a dimension they are **not** assigned
  in.
- Two dimensions, asserting `AND` between them.
- Empty code set → a query returning no rows, asserted on the SQL, not by
  running it.
- Over-cap → refusal, with the table and count in the message.
- SuperAdmin → byte-identical SQL to today's.
- Shared-reference table → no filter.
- All four join types, asserting an out-of-scope row appears on **neither** side,
  including as an unmatched row under FULL.

Run the emitted shapes through `EmittedSqlParsesTest`, and the
`EXPLAIN (TYPE VALIDATE)` IT where a type could resolve differently.

## Part 5 — Prove it against the real container

Automated tests did not catch any of the last four defects. Before reporting
done, with the local Presto and the dev PostgreSQL:

1. Log in as `sanganeruser` (taluka scope) and run a match on
   `iceberg.srse.beneficiary`, bound at District to `district`.
2. Confirm the returned rows carry **only** districts within that officer's
   subtree — check the data, not the SQL.
3. Re-run as `superadmin` and confirm strictly more rows.
4. Attempt to reach an out-of-scope district by crafting the request directly:
   name the column in `sourceCriteria`, filter on it, ask for it as a display
   column. Confirm none of it widens the result.

Record what you ran and the row counts.

## Acceptance

- `mvn -f backend/pom.xml test` green on **JDK 17**. Report the count — **416**.
- `npm run build` / `npm run test` clean.
- No-filter SQL byte-identical to today's.
- Part 5 executed, with numbers.
- Report any path in Part 3 you could not cover, and whether the planner is the
  single choke point.

## Do not

- Do not interpolate codes into SQL.
- Do not put the scope predicate in ON or WHERE — Part 0.
- Do not treat an empty code set as "no filter".
- Do not relax the fan-out ceiling to make a scoped query fit.
