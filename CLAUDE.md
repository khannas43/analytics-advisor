# Analytics Advisor

> **Read this first, every session.** This file is the source of truth for
> architectural decisions that are settled. Decisions that are still open are
> listed as open — do not quietly resolve one by writing code that assumes an
> answer. If a change to a settled decision is proposed, flag it explicitly
> rather than drifting.

## What Analytics Advisor is

A no-code analysis platform over a lakehouse. A departmental officer picks two
registered tables, joins them on one or more columns (exact or fuzzy), compares
the columns that did not form the join, filters and groups the result, and
exports it. The point is reconciliation at scale — finding where two systems
disagree about the same person, account or transaction — without anyone writing
SQL.

Target UX: `docs/Analytics_Advisor.html`. It is a **mockup**: hardcoded data, six
rows per table, Levenshtein running in the browser. Treat it as a picture of the
destination, not a specification.

The build plan, with every activity numbered and marked reused/new, is
`docs/PRODUCT_PLAN.md`. That file also holds the open product decisions and the
user-management design. **Read it before planning work.**

## Relationship to SRSE

This repository is a fork of **SRSE** (Scheme Rule Simulation Engine), a
welfare-scheme eligibility simulator built for the Government of Rajasthan.
Fork point: SRSE tag `srse-pre-fork` (`ad4efb4`). `FORK.md` records what was
removed and why; anything removed is still readable at
`git show srse-pre-fork:<path>`.

About 85% of SRSE's backend was a general-purpose lakehouse analysis engine with
no scheme assumptions in it, and that is what this product is built on. Much of
its value is in defects already found against a real Presto — several of which
no unit test could have caught. Those are recorded below; **do not re-derive
them the hard way.**

- **The Java package root is still `gov.rajasthan.smart.srse`, and config keys
  are still `srse.*` / `SRSE_*`.** Deliberate. Renaming now makes it harder to
  copy fixes between two moving repositories. Rename once they stop trading
  fixes (`PRODUCT_PLAN.md` 1.6).
- Fixes to the shared engine should be considered for both repositories.

## Settled decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | **Two containers** (backend, frontend), own release lifecycle | Unchanged from SRSE and still right |
| 2 | **Backend: Java 17 / Spring Boot 3.3**, fat-JAR, embedded Tomcat (NOT WAR) | Modern LTS; records/sealed/switch/text-blocks suit a SQL compiler |
| 3 | **Frontend: Next.js 16 / React 19 / TypeScript** | Carried over and working |
| 4 | **Two data planes, never conflated** | See below |
| 5 | **Push-down execution** — aggregation and matching run in the query engine, never by pulling rows into the app tier | Analytical scale |
| 6 | **Registry allow-list** — officers reach only admin-registered tables | See below |
| 7 | **Row-level data scoping is in scope** and shapes the schema | See "Data scoping", `PRODUCT_PLAN.md` §7 |

## Open decisions — do not assume an answer

These are recorded in `docs/PRODUCT_PLAN.md` §0. They are open because they
change the shape of the work, and code that silently picks one is expensive to
unpick.

| # | Open question |
|---|---|
| 0.1 | **Operational store: stay on DB2, or move to PostgreSQL?** DB2 was a Rajasthan constraint, not a product choice. |
| 0.2 | **Query engine: PrestoDB only, or Trino too?** Different drivers and dialects. |
| 0.3 | How "Source System" maps to physical storage. |
| 0.4 | Single tenant or multi-tenant. |
| 0.5 | Who the users are. |
| 0.6 | Row limits. |
| A16 | **Is "department" a second, orthogonal dimension alongside geography?** The scoping model assumes yes. |

Until 0.1 and 0.2 are settled, **keep the JDBC seams generic**: new code should
go through the existing datasource configuration rather than naming a driver.

## The two data planes (critical — never conflate)

- **Operational plane** → currently **DB2** via **IBM JCC 11.5.8** + **Spring
  Data JPA**. The product's own data: registered tables, column metadata, and
  (to come) users, scope assignments, saved queries, audit log. Small,
  transactional, entity-shaped. ORM is right here. *Subject to decision 0.1.*
- **Analytical plane** → **PrestoDB 0.297** over **Iceberg** via
  **`com.facebook.presto:presto-jdbc`** + **JdbcTemplate** (raw SQL). The data
  being analysed. Set-based. **NO ORM** — Hibernate has no Presto dialect, and
  an ORM has no business mediating an analytical set-query. *Subject to 0.2.*

> ⚠️ **Driver flavour is PrestoDB, NOT Trino.** PrestoDB 0.297 is the
> Facebook/Presto lineage; since the 2020 fork these are different drivers with
> different dialects. Use `com.facebook.presto:presto-jdbc`, never
> `io.trino:trino-jdbc`, unless decision 0.2 says otherwise.

## Lakehouse addressing: Catalog → Schema → Table → Column (load-bearing)

Lakehouse objects are addressed by their **full four-part path**. A bare table
name is NOT an address: a deployment maps several layers whose catalog, schema
and table names all differ, and the same table name legitimately exists in more
than one.

- **The Presto connection is catalog-agnostic.** `jdbc:presto://host:8080` is a
  complete URL. Any trailing `/catalog/schema` is only a *default*, never relied
  on — that model could not express multiple catalogs.
- **Everything is qualified.** `analysis_column_metadata`'s key and every
  identifier the match SQL emits are `catalog.schema.table[.column]`.
- **Two reaches, never conflated** (`...srse.lakehouse`):
  - `LakehouseBrowseService` — the live cluster, everything the connection can
    reach. **Admin-only** (`SRSE_ADMIN`), enforced in `SecurityConfig`, for
    discovery.
  - `LakehouseRegistryService` — the admin-registered subset, persisted
    operationally (`registered_table`). **Everything officer-facing reads this.**
- **Registration is per TABLE; columns are never copied into the operational
  store.** Registering exposes all of a table's live columns, re-read on every
  call — so a column added upstream appears without re-registration, and a
  dropped one disappears instead of lingering as a reference that compiles into
  a broken query. Individual columns are hidden, given business names, fuzzy
  flags and comparison modes via `analysis_column_metadata`.
- **`layer` (BRONZE/SILVER/GOLD/…) is a display TAG, not a hierarchy level.**
  Registration requires a layer tag for new rows; legacy null-layer rows stay
  reachable under the wire sentinel `UNTAGGED` (a filter only, never a stored
  tag). Layer never enters a request payload, a validation gate, or emitted SQL.
  A Silver↔Gold reconciliation is an ordinary two-table match whose sides carry
  different catalog/schema values.

### Injection safety at this seam

Catalog and schema names must be **interpolated**, not bound — Presto has no
placeholder for a catalog qualifier, so `?.information_schema` is not valid SQL.
Two mechanisms cover that, and **both must stay**:

1. **Allow-list (primary).** Each level is validated against the level above
   before use: catalog against `SHOW CATALOGS`, schema against that catalog's
   `information_schema.schemata`, and so on. Only names the lakehouse itself
   reported reach SQL text.
2. **Identifier grammar (defence in depth).** `LakehouseIdentifiers` rejects
   anything that is not a bare Presto identifier — including on the first,
   not-yet-validated use, since the validation query must itself interpolate the
   catalog to run at all.

Officer-supplied identifiers additionally pass **two gates**
(`LakehouseRegistryService.validateColumn`): the table must be *registered*, and
the column must exist *live* and not be hidden. Neither alone is sufficient —
the registry can name a since-dropped table, and live introspection alone would
let an officer reach any table on the cluster.

## Injection safety (non-negotiable)

- The compiler emits **only parameterised SQL** (`?` placeholders + an ordered
  parameter list).
- Officer **values** are always bound parameters, never string-concatenated.
- Officer **identifiers** reach SQL only after passing the allow-list and
  grammar gates above.

## Cross-type comparison

Two tables rarely agree on a type. The same account number is `varchar` in one
table and `bigint` in another; an income is `varchar` where it reads as a
number. **Presto does not coerce across type families**, so these comparisons
did not return zero rows — they failed the whole query
(`'=' cannot be applied to varchar, bigint`; `lower(bigint)` →
`Unexpected parameters`).

- `compiler/SqlTypeFamily` + `compiler/TypeCoercion` are the single place that
  decides a cast. **Within** a family nothing is emitted — Presto already
  coerces integer↔bigint and varchar(20)↔varchar(50), and a cast there would
  only change results. An **UNKNOWN** type (row/array/map/varbinary) is left
  exactly as it is rather than guessed at.
- **Number vs text compares as NUMBERS by default** (`TRY_CAST` on the text
  side). A value stored as a number has already lost its leading zeros, so
  `'0123'` and `123` are the same identifier recorded twice; comparing as text
  would systematically miss exactly the rows being looked for. `TRY_CAST`, never
  `CAST`: one unparseable row must not fail the match.
- **Admin override per column** — `analysis_column_metadata.compare_as`
  (`AUTO`/`NUMBER`/`TEXT`). An explicit setting on *either* side wins; if the
  two sides conflict, **TEXT wins** (it can represent every value, so it can
  lose matches but never nulls a side away).
- **Fuzzy pairs always compare as text** — Levenshtein is a string measure — so
  a non-text side is cast for the blocking key and the similarity alike.
- **Boolean and temporal fields are never coerced.** `TRY_CAST` would turn the
  `'Y'`/`'N'` flags this data is full of into silent NULLs, and a date's text
  format is anybody's guess. Better to fail loudly than answer wrongly.

## The match engine

### Column groups

A match criterion is a **group** of 1..N columns per side, so the two sides need
not be the same size — one `full_name` against `first_name` + `last_name`. A
group with one column on each side emits byte-identical SQL to the plain
criterion pair it replaced.

- **COMBINE** folds the many side with
  `array_join(filter(ARRAY[...], x -> x IS NOT NULL AND x <> ''), sep)` — NOT
  `concat_ws`, which would leave a doubled separator where a middle name is NULL
  and charge every such row a Levenshtein edit. Column order is the officer's
  and it matters.
- **ANY_OF** matches when one side equals any of the other's columns, and is
  emitted with **`CROSS JOIN UNNEST`, NEVER as `OR` in the ON clause**. A
  disjunctive ON costs Presto the hash join and drops it to a nested loop over
  the cross product. Each ANY_OF side projects a `matched_on` column naming the
  column that matched, since two columns holding one value legitimately return
  the pair twice.
- **Do not re-add `LEFT JOIN UNNEST` for ANY_OF.** PrestoDB 0.297 fails at
  analysis with "UNNEST on other than the right side of CROSS JOIN is not
  supported" — the SQL *parses* and is submitted, so `EmittedSqlParsesTest`
  cannot catch it. It is also unnecessary: unlike COMBINE's `filter(...)` array,
  the ANY_OF array is positional (`ARRAY[a, b]`) and never empty, so an all-null
  candidate row yields N null-keyed rows and survives `CROSS JOIN UNNEST`
  regardless.
- A **multi-column COMBINE always compares as text**, so `compare_as` does not
  apply to it: reading a concatenated name as a number would `TRY_CAST` every
  row to NULL and return nothing, silently.
- **Fuzzy is decided per GROUP, not per column.** Registered columns decide as a
  bloc — an unregistered column beside a registered one gets no vote, so a
  name-pattern guess can never overturn an explicit admin setting, and the guess
  applies only when nothing in the group is registered. When registrations
  inside a group **disagree, fuzzy wins**: a group wrongly forced exact returns
  almost nothing and reads as "these datasets do not overlap", while one wrongly
  made fuzzy returns extra rows that carry `match_score_pct` and are tunable
  with the officer's own threshold. Visible over silent — the same trade
  `CompareAs.resolve` makes when TEXT wins. **`RecordMatchService.isGroupFuzzy`
  and the frontend's `pairIsFuzzy` must stay in lockstep.**
- Caps: 8 groups, `SRSE_ANALYSIS_MAX_GROUP_COLUMNS` (4) columns per side per
  group, `SRSE_ANALYSIS_MAX_ANYOF_GROUPS` (2) ANY_OF groups per side — two
  UNNESTs on one side cross-multiply that side's rows.

### Join types

`joinType` on the request, default INNER; omitted/null deserialises as INNER so
stored requests stay valid. **INNER SQL must stay byte-identical** to
pre–join-type behaviour (bare `JOIN`, fuzzy similarity in WHERE).

- For outer joins, **any predicate referencing a nullable side belongs in ON,
  not WHERE** — especially fuzzy Levenshtein, which used to live in WHERE and
  silently converted LEFT back to INNER by filtering away unmatched rows.
- **Dedup** is rejected with RIGHT/FULL — partitioning on `source_*` columns
  collapses unmatched rows (NULL keys) into one partition.
- **Self-join:** pick the same registered table on both sides. Not a separate
  feature.

### Post-join column comparison

`ComparisonGroup` is deliberately separate from `MatchGroup` so comparison pairs
cannot reach the ON emitter. Values are projected in SELECT only
(`cmp_<n>_source`, `cmp_<n>_target`, `cmp_<n>_match`, and `cmp_<n>_score_pct`
only when the comparison is fuzzy); exact compares use
`NOT (a IS DISTINCT FROM b)` so both-null reads as a match. On **outer** joins
only, `match_status` (`MATCHED` / `NO_TARGET` / `NO_SOURCE`) is emitted from
join-key nullability, and no-counterpart rows get **NULL** verdicts, not false.
`mismatchOnly` filters with `match_status <> 'MATCHED' OR NOT (all verdicts)` so
unmatched rows are not dropped by three-valued logic. **`match_status` is
omitted for INNER** so no-comparison requests stay byte-identical.

### Multi-target

`POST /api/analysis/match-multi` is **N × two-table JOIN, never an N-way join** —
one hub table, one target table per sub-match. Each target carries its own
`joinType` and delegates to `RecordMatchService`, so outer-join predicate
routing is not duplicated. **Dedup must reference the hub table only.** Partial
failure is **per target** in the NDJSON stream; **`match-multi.csv` is
all-or-nothing** (one failed target aborts the download). Each target's SQL
rides its own `started` progress event, never the `meta` line — `meta` is
serialised and flushed before any target has been planned, so anything it
promised about them could only be null.

**Target↔target and arbitrary N-way joins remain out of scope.** The hub model
is what gives per-target failure, per-target budget, and hash joins at scale.
Reopen only with table statistics the engine can use, the fan-out pre-check
below, and officer-pinned join order. Measured locally: a well-keyed 3-table
chain costs 120,000 rows, while the same tables chained on a 7-value column cost
3.4 billion — the risk is the key, not the chain. See `docs/chain-fanout-probe.md`.

## Guardrails

- **Query timeout** (`SRSE_QUERY_TIMEOUT_SECONDS`, default 180) is applied to
  every analytical query before execution, including lakehouse browsing. It is
  the only remaining safety net against a runaway match. It is set on a shared
  singleton `JdbcTemplate`, so a path that forgets to apply it silently inherits
  whatever the last caller set — this caused a real 30-second failure on the
  admin page.
- **Fan-out guard.** Before executing, `RecordMatchService` estimates equi-join
  output as `sourceRows × targetRows / ∏ max(sourceDistinct, targetDistinct)`
  per group. Inputs come from **the engine's catalog statistics** (`SHOW STATS`)
  when `ANALYZE` has been run, and are computed live (`count(*)` +
  `approx_distinct`) when it has not — the fallback is silent and automatic. A
  **fuzzy** group always computes: it joins on the blocking-key expression, and
  no statistic describes `substr(lower(col), 1, n)`. Above
  `SRSE_ANALYSIS_MAX_ESTIMATED_ROWS` (default 50,000,000) the request is
  **refused with the estimate in the message** — not left to time out. Measured
  against ground truth: within 0.9% on a unique key (19,813 vs 20,000) and
  0.00003% on a low-cardinality column (571,457,142 vs 571,457,293).
- **The match is deliberately uncapped server-side.** An earlier top-500
  pre-sample made matches unfindable at scale. Large results are handled where
  they actually hurt — the browser: past **10,000 rows** the grid, its filters
  and the charts are not rendered and the buffered rows are dropped, and the
  result is offered as a CSV download instead. That download
  (`POST /api/analysis/match.csv`) **re-runs the match and streams straight from
  the engine to the file**, so it is the COMPLETE result, never a
  re-serialisation of what the screen was holding. Reading the NDJSON stream
  stops at 200,000 rows, after which the on-screen count is a lower bound
  (`200000+`) but the CSV remains complete.
- `SRSE_ANALYSIS_BLOCKING_PREFIX_LEN` (default 3) controls fuzzy blocking.
  Longer prefixes block harder and cost recall on typos in the first N
  characters; shorter ones explode the candidate set.
- **Join-key suggestions** (`POST /api/analysis/suggest-keys`): metadata-only by
  default; the optional overlap probe samples the **source** only
  (`TABLESAMPLE BERNOULLI`) and scans the **target in full** per pair, capped by
  `SRSE_ANALYSIS_MAX_PROBED_PAIRS` and the query timeout. Source key-likeness
  uses one full-table `approx_distinct/count` on the source — **not sampled**,
  because sampling inflates mid-cardinality ratios and floats attributes up
  beside real keys.

### Dead configuration — do not build on it

`srse.guardrails.cohort-cap`, `srse.guardrails.preview-sample-size` and
`srse.breakdown.age-band-column` are still bound but nothing reads them: the
cohort endpoint, the rules preview and the fixed district/gender/age-band
breakdown all left with SRSE. Remove them when convenient; do not treat them as
product behaviour.

## Data scoping and audit (designed, NOT yet built)

Decisions are recorded in `docs/PRODUCT_PLAN.md` §7 and its open-questions
tables. Summary of what is settled, because it constrains schema design now:

- **Hierarchy levels are configurable per deployment** (A1). Levels are data,
  not code; the scope predicate is built dynamically and bindings reference
  level ids, never hardcoded column names.
- **An officer holds multiple assignments** (A2), possibly at different levels
  and on more than one dimension (geography, department). A16 — whether
  department is a second orthogonal dimension — is **open**; the design assumes
  it is.
- **A scope is a subtree** (A3). A district officer sees the district and
  everything beneath it. Store the assigned node and expand downward at query
  time via a materialised path, so "descendants of X" is a prefix match and an
  assignment survives reorganisation below it.
- **Deny by default** (A4). A table is scoped unless an admin explicitly flags
  it as shared reference data. Enforcement must reach browse and column listing,
  not only query execution.
- **A join never relaxes a filter** (A5). A scoped table may join a shared
  reference table with the scoped side still filtered; two scoped tables apply
  both. Unbound, unflagged tables are denied outright.
- **The audit log stores the query SHAPE, never bound values** (A6). No Aadhaar
  numbers, no names. Log access is itself scoped to the requesting officer's
  subtree (A10).
- **Bind each table at the coarsest level it carries** (A17). A district officer
  querying a table with a `district_code` column gets `district_code = ?` and one
  bound value; the same officer against a table carrying only `village_code`
  needs thousands of values in an `IN` list. Prefer the coarsest covering column;
  cap and refuse clearly when expansion would be unreasonable.

## Java 17 code-style expectations (backend)

- **Records** for AST nodes and immutable DTOs.
- **Sealed interfaces** for node hierarchies.
- **Switch expressions** for operator emitters.
- **Text blocks** for SQL templates.
- Lombok is available but records supersede it for simple value types.
- Generate idiomatic Java 17 — **do NOT** emit Java 8 idioms.

## Error contract

`config/ApiExceptionHandler` is a global `@RestControllerAdvice` mapping
`IllegalArgumentException` → **400**, `IllegalStateException` → **409**,
`UnknownFieldException` → **400**, `UnconfiguredFieldException` → **503**.
`DataAccessExceptionHandler` separately distinguishes an unreachable engine
(**503**, retryable) from a query that ran and failed (**500**, carrying the
driver message and the failing SQL).

This matters more than it looks. The fork removed the advice along with the
package it happened to live in, and 62 throw sites across the match service,
the registry and the identifier grammar silently began returning anonymous
500s — including the fan-out refusal, whose entire purpose is to say why. Keep
validation failures mapped.

## Testing discipline

A green suite has repeatedly failed to catch defects here. Three classes were
invisible to unit tests: an **unsupported construct** that parses, **wrong
arithmetic** in an estimate, and **unbindable SQL** with a placeholder count
mismatch.

- `EmittedSqlParsesTest` runs every emitted shape through
  `com.facebook.presto:presto-parser`, test-scoped and pinned to the driver's
  version. **Syntax only** — it proves nothing about types resolving or rows
  being right.
- `AnalysisEmittedSqlPrestoValidateIT` runs `EXPLAIN (TYPE VALIDATE)` for
  analyzer-level checking. Not in the default build:
  `SRSE_PRESTO_INTEGRATION=true mvn -f backend/pom.xml test -Dtest=AnalysisEmittedSqlPrestoValidateIT`
- **Manual runs against the real container are recorded, not automated**,
  because they need `prestodb/presto:0.297`. Re-run them by hand when this seam
  changes: all four join types (including a fuzzy pair, an ANY_OF with an
  all-NULL candidate row on the preserved side under LEFT, and a source row with
  no target match under LEFT), both column-group modes cross-catalog, and the
  post-join comparison NULL semantics. Recorded results are in `FORK.md` history
  and SRSE's `CLAUDE.md` at `srse-pre-fork`.
- Build commands: `mvn -f backend/pom.xml test` (there is **no root pom**;
  `-pl backend` does not work), and `npm run build` / `npm run test` in
  `frontend/`. The backend needs **JDK 17** — the enforcer rejects anything else.
- `npm run lint` currently reports 6 pre-existing `react-hooks` errors in
  `analysis/page.tsx` and `LakehouseCascade.tsx`, inherited from SRSE. Not a
  regression; do not treat a clean lint as an acceptance criterion until they
  are fixed.

## Known broken and unfinished

- **The age filter is a live UI control that always fails.** The Analysis page
  still offers an age-range checkbox. It resolved `age_years` through SRSE's
  field catalogue, which is gone; `StubFieldResolver` now throws for every key,
  so ticking it returns `400 Unknown or unmapped field key: age_years`. Either
  drop the control or back it with a real field concept (`PRODUCT_PLAN.md` 1.3).
- **Admin config backup is removed, not trimmed.** It bundled field mappings and
  schemes alongside registrations and column metadata, and a half-edited import
  path silently lost admin config on restore. Reinstate carrying only
  connections, registrations and column metadata (1.2).
- **`/admin456` has no route-level guard.** Every endpoint it calls enforces
  `SRSE_ADMIN` server-side, so an officer reaching the URL sees failing panels
  rather than data — but the page itself is unguarded and unlinked. A real
  guard arrives with §7.
- `DataMode` (`SYNTHETIC`/`LIVE`) survives only as a display label via
  `EnvironmentLabelResolver`; the per-environment field mapping it used to key
  is gone.

## Environment cheat-sheet

| | Local (laptop) | Client Dev |
|---|---|---|
| Presto | local container (`prestodb/presto:0.297`) | on-prem PrestoDB 0.297 |
| Operational store | local DB2 container | on-prem DB2 |
| Auth | mock JWT issuer (`?role=admin` mints admin+officer; **seam only, not an ACL**) | SSO, not yet wired |
| Frontend origin | `http://localhost:3000` | deployment origin |

`SRSE_FRONTEND_ORIGINS` must match the origin the **browser** loads the frontend
from, and `NEXT_PUBLIC_API_BASE` is baked into the client bundle at build time.
A mismatch shows up as "Failed to fetch" on every call, with a 403 on the CORS
preflight and nothing useful in the backend log.
