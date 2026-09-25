# Brief AA-13 — Extract Records

**Repo:** `analytics-advisor` · **Covers:** `PRODUCT_PLAN.md` 4.1–4.8
**Follows:** AA-12 (`732e4d0`)

## Scope

The officer's starting screen: pick two tables (or one), choose the columns to
return, pick the join key, add filtering rules, preview the SQL, run it.

Most of it is reuse — the Analysis tab already does table pickers, column
multi-select, join-key selection, SQL preview, streaming and the fan-out guard.
**Two things are genuinely new:** rules (4.5) and single-source mode (4.4).

## Part 0 — Re-seat the rule compiler (4.5)

`CLAUDE.md` records this as a known landmine, and it is the main work here.

`RuleCompiler` is a live `@Component` that resolves **abstract field keys**
through `FieldResolver`. This product has no field keys — officers pick
`catalog.schema.table.column` from the registry cascade — and
`StubFieldResolver` throws for every key, so **the compiler cannot currently
emit a single predicate.**

- Change `Ast.PredicateNode` to carry a **qualified column** instead of
  `fieldKey`.
- Resolve it through **`LakehouseRegistryService.validateColumn`** — the same two
  gates everything else uses: the table must be registered *and* the column must
  exist live *and* not be hidden. No new validation path.
- Keep the emitter exactly as it is otherwise: parameterised SQL, `?`
  placeholders, an ordered parameter list. The operator set
  (`EQ`…`BETWEEN`, `IS_NULL`, `FUZZY_MATCH`) already exists and is tested; do not
  rewrite it.
- Once nothing calls it, **delete `FieldResolver` and `StubFieldResolver`**, and
  their two mappings in `ApiExceptionHandler`. Leaving a resolver that throws for
  every input is how this trap stayed live through four packages.

`AliasRebase` keeps its fourteen tests and becomes useful again here — a rule
predicate has to be rebased onto the side alias it applies to, which is exactly
what it was written for.

## Part 1 — Where the rule predicate goes

**Inside the scoped derived table, with the scope filter. Never in the join's
WHERE clause.**

```sql
FROM (SELECT * FROM iceberg.srse.beneficiary
       WHERE district IN (?)            -- scope, AA-09
         AND age_years > ?) src         -- the officer's rule
JOIN (...) tgt ON src.id = tgt.id
```

This is the same rule AA-09 established and for the same reason: a predicate on
a nullable side placed in WHERE silently converts LEFT back to INNER. `CLAUDE.md`
records that happening once already with the fuzzy predicate. Putting rules
where the scope filter goes means all four join types keep working with no
join-specific logic, and rules narrow each side before the join rather than
after, which is also faster.

Rules on the **source** go in the source's derived table; rules on the
**destination** go in the destination's. A rule never spans both sides — that is
a join condition, not a filter, and it is not in this brief.

## Part 2 — Typing the officer's value

An officer types `48000` into a form; the column may be `varchar`. Presto does
not coerce across type families, so the comparison fails the whole query rather
than returning nothing — the failure `SqlTypeFamily` and `TypeCoercion` were
written for.

Reuse them. The live column type comes from the registry's introspection, which
is already how the match engine decides. Do not re-derive typing rules here, and
keep the existing conventions: `TRY_CAST` rather than `CAST` so one unparseable
row cannot fail the query, and **no coercion at all for boolean or temporal
columns**, where a silent NULL is worse than a loud failure.

## Part 3 — Single-source mode (4.4)

One table, no join, no target side.

This is a **new query shape**, and every obligation the two-table path carries
applies to it. It is the most likely place for something to be forgotten:

- **Scope filter** — the single FROM is a scoped derived table like any other.
  Go through AA-09's planner; do not build a `FROM` anywhere else.
- **Audit** — `QUERY_EXECUTED` / `QUERY_PREVIEWED` / `QUERY_REFUSED`, with the
  query shape, through AA-11's choke point.
- **Row caps and streaming** — the same 10,000-row render limit, 200,000-row
  stream stop, and complete streamed CSV.
- **Fan-out guard** — there is no join, so there is no fan-out. Skip the check
  rather than passing it degenerate inputs, and say so in a comment.

## Part 4 — What is reuse, and should stay reuse

4.1 pickers, 4.2 column multi-select, 4.3 join-key selection, 4.6 SQL preview,
4.7 run and stream, 4.8 fan-out guard. The Analysis tab has all of this working
and verified against a real Presto.

**Do not fork the match path to make Extract Records.** If the two screens need
different shapes, extend the existing request model; two engines drift, and this
one has defects that took live testing to find and fix.

The preview must show the **real** SQL, including the scope predicate (7.2.7)
and the rules.

## Part 5 — Tests

- A rule compiles to parameterised SQL with the value **bound**, not inlined.
- A rule on an unregistered table, and on a hidden column, are both refused.
- Rules land **inside** the derived table — assert on the emitted SQL, and
  assert across all four join types that a rule on the nullable side does not
  drop unmatched rows.
- Number-vs-text comparison uses `TRY_CAST`; boolean and temporal are untouched.
- Single-source: scope predicate present, audit row written, caps applied.
- SuperAdmin single-source SQL carries no scope filter.
- The audit row for a rule-bearing query has `?` in `query_shape` and the typed
  value nowhere in the row — the AA-11 assertion, on this new path.

## Acceptance

- `mvn -f backend/pom.xml test` green on **JDK 17**. Report the count — **461**.
- `npm run build` / `npm run test` clean.
- Live, against the local Presto and dev PostgreSQL, as `jaipurofficer`:
  single-source extract from `beneficiary` with a rule on a real column; confirm
  the returned rows satisfy the rule **and** carry only the Jaipur district.
  Then the same as `superadmin` and confirm strictly more rows.
- Report the row counts, and whether `FieldResolver` could be deleted.

## Do not

- Do not put rule predicates in the join's ON or WHERE.
- Do not build a second query planner for single-source.
- Do not inline officer values into SQL.
- Do not leave `StubFieldResolver` in place once the compiler is re-seated.
