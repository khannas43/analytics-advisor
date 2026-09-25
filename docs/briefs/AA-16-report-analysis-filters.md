# Brief AA-16 — Value filter and fuzzy options

**Repo:** `analytics-advisor` · **Covers:** `PRODUCT_PLAN.md` 5.1, 5.3, 5.4
**Follows:** AA-15 (`702ea43`)

## Scope

The three remaining Report Analysis items. Small individually; each has one
sharp edge.

## Part 1 — Value filter (5.1): a rule with a better picker

An officer picks a column, sees its distinct values, ticks the ones they want.

**Do not build a second filtering mechanism.** AA-13 already emits
`Operator.IN` through `RuleCompiler`, bound and placed inside the scoped derived
table. The value filter is a **UI over that predicate**, not a parallel path. A
second filter would need its own scope handling, its own audit shape and its own
placement relative to the join — three chances to get right what is already
right once.

The new backend surface is only the value list:

**`POST /api/analysis/column-values`** → distinct values for one registered
column.

- **This returns data, not metadata.** It is real values out of the table, so it
  goes through the **scoped** derived table like any other read, and unlike the
  registry cascade it **is audited** (`QUERY_EXECUTED`). A7 excluded metadata
  browsing for volume; a value list is lower-frequency and higher-consequence —
  it is the one screen where an officer reads column contents directly.
- **Cap it, and say when you did.** `SELECT DISTINCT col FROM (scoped) LIMIT
  n+1`, then report `truncated: true` when `n+1` came back. A distinct list on a
  high-cardinality column is the whole column; returning it silently truncated is
  how an officer concludes a value does not exist.
- When truncated, the UI must offer **type-to-search** (a `LIKE` filter bound as
  a parameter) rather than pretending the list is complete.
- NULL is a legitimate value to filter on. Offer it explicitly; do not drop it.

## Part 2 — Fuzzy options (5.3): normalise in exactly one place

Case-sensitivity and ignore-spaces are one-line changes that will break matching
if applied in one place and not the other.

Today the normalisation is written **twice, independently**:

- the blocking key — `substr(lower(col), 1, n)` in `RecordMatchService`
- the similarity — `levenshtein_distance(lower(a), lower(b))` in `FuzzyMatchSql`

They agree today only because both happen to say `lower()`. Add "ignore spaces"
to the similarity alone and two names differing by a space stop being
**candidates at all** — the blocking key never brings them together, so the
similarity that would have matched them is never evaluated. The result is a
fuzzy match that silently misses exactly the rows the option was added for.

**Extract a single normalisation function** — something like
`fuzzyNormalise(columnSql, options)` returning the wrapped expression — and have
both the blocking key and the similarity call it. Then the two cannot drift.

- `caseSensitive: true` → drop `lower()`.
- `ignoreSpaces: true` → wrap in `replace(…, ' ', '')`.
- Both default to today's behaviour: case-insensitive, spaces significant. **The
  emitted SQL with default options must be byte-identical to what it is now**,
  so the recorded manual Presto runs stay valid.
- Options apply per fuzzy group, beside the existing threshold.

Note that these change the blocking key, so they change the fan-out estimate —
which is correct, since they change which rows are compared. Fuzzy groups
already always compute their estimate live rather than reading statistics, so
nothing further is needed.

## Part 3 — Fuzzy against typed text (5.4)

The officer types a value and matches a column against it, instead of against a
second column.

- This is a **filter on one side**, not a join. It belongs inside the scoped
  derived table with the rules, not in an ON clause.
- **Block on the typed value too.** `substr(lower(col), 1, n) = substr(lower(?),
  1, n)` is an equality against a constant and therefore very selective — far
  cheaper than scanning. Use the same shared normalisation from Part 2 on both
  sides, for the same reason.
- Project `match_score_pct` as the existing fuzzy paths do, so the officer can
  see how close each row was.
- **The typed text is a bound parameter.** It is very likely a person's name, so
  A6 applies with force: it must appear in the audit log as `?` and nowhere else
  in the row. Assert that, the way AA-11 and AA-13 already do.

## Part 4 — Tests

- Default fuzzy options emit **byte-identical** SQL to today.
- `ignoreSpaces` appears in the blocking key **and** the similarity — assert
  both in one test, since the failure is that only one changes.
- `caseSensitive` drops `lower()` from both.
- A value list is scope-filtered: a scoped officer sees only their own values.
- A value list past the cap reports `truncated: true`.
- The value filter emits the same `IN` predicate a hand-built rule would.
- Typed-text fuzzy: predicate inside the derived table, blocked on the constant,
  and the typed value absent from the audit row.
- Emitted shapes pass `EmittedSqlParsesTest`.

## Acceptance

- `mvn -f backend/pom.xml test` green on **JDK 17**. Report the count — **485**.
- `npm run build` / `npm run test` clean.
- Live, as `jaipurofficer`: list distinct `district` values and confirm **only
  Jaipur** comes back — the value list is the most direct way to read another
  district's data if scoping were missed, so this is the check that matters.
- Also live: a typed-text fuzzy against `father_name` returning rows with
  `match_score_pct`, and the audit row showing `?`.

## Do not

- Do not build a second filter path — the value filter is an `IN` rule.
- Do not normalise in two places.
- Do not return a truncated value list without saying so.
- Do not let typed text reach the audit log.
