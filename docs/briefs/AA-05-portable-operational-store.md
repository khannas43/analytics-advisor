# Brief AA-05 — Make the operational store portable

**Repo:** `analytics-advisor` · **Closes:** `PRODUCT_PLAN.md` 1.7 · **Follows:** AA-04 (`c298710`)

## Why, and why now

Decision 0.1: the client does not mandate a database. The product must run on
**PostgreSQL and DB2 both**, with PostgreSQL as the default and reference.

Today none of that is true:

- `application.yml` pins `org.hibernate.dialect.DB2Dialect`.
- The schema is built by `ddl-auto: update`, whose own comment calls it "local
  convenience only".
- `docs/migrations/*.sql` are three hand-run files, DB2-only, with no record of
  what has been applied where.
- Only the DB2 driver is on the classpath.

**`ddl-auto: update` is not merely untidy — it has already failed silently
here.** From `docs/migrations/001`:

> `ddl-auto: update` cannot perform this change and does not report that it
> failed to. It adds NULLABLE columns happily, but DB2 rejects
> `ADD COLUMN ... NOT NULL` on an existing table, so `catalog_name` and
> `schema_name` are silently skipped. The application then starts cleanly and
> every query against the table fails at runtime with SQLCODE=-206.

**Do this now because the schema is two tables.** §7 adds users, scope
assignments, the audit log and saved queries. Converting two tables is an
afternoon; converting them after §7, with live audit history, is not.

## Decisions taken — build to these

**Use Liquibase, not Flyway.** Flyway runs SQL, so supporting two databases
means either restricting every migration to the intersection of both dialects
or maintaining two parallel sets that will drift. The divergence is real and
already present: both current entities use `Boolean`, which is `BOOLEAN` on
PostgreSQL and awkward on DB2. Liquibase generates vendor-appropriate DDL from
one changelog, which is exactly the problem here.

**`ddl-auto: validate`, not `none`.** Liquibase owns the schema, but validate
makes Hibernate check the entities against it at boot, so a changelog that
drifts from an entity fails at startup rather than at runtime in front of an
officer — which is precisely how SQLCODE=-206 got out.

**PostgreSQL becomes the local default.** It also removes a real irritation: the
DB2 image has no arm64 build, so on Apple Silicon it runs under emulation and
takes minutes to first boot.

## Part 1 — Dependencies and configuration

- Add `org.postgresql:postgresql` and `org.liquibase:liquibase-core`. Keep the
  DB2 JCC driver — DB2 remains supported.
- **Remove the hardcoded dialect** from `application.yml`. Hibernate detects it
  from the connection; pinning it is what makes the app DB2-only.
- `ddl-auto: update` → **`validate`**.
- Default `srse.datasource.operational.*` to PostgreSQL. DB2 stays reachable by
  overriding the URL, user and driver, exactly as now.

## Part 2 — Changelog

`src/main/resources/db/changelog/`, a master changelog including per-change
files.

**Changeset 001 — baseline.** The current schema: `registered_table` and
`analysis_column_metadata`, in the shape the entities describe *after* the three
existing migrations. Derive it from the entities and the migration files
together, not from `ddl-auto` output on a fresh database — check it against a
DB2 instance that has had all three applied.

Fold `docs/migrations/001–003` in, then leave those files where they are with a
note pointing at the changelog. They are the historical record of why the schema
looks as it does; 001's explanation of the silent DB2 failure is worth keeping.

**Existing databases must not be re-created.** A deployment already carrying
these tables needs the baseline marked as applied, not run. Use Liquibase's
`changelogSync` for that and **document the exact command** in
`docs/CONFIGURATION_GUIDE.md` — an operator upgrading a live instance needs it,
and getting it wrong means a failed startup against a populated database.

**Write vendor-neutral changesets.** Liquibase types (`BOOLEAN`, `BIGINT`,
`VARCHAR`, `TIMESTAMP`), not raw SQL. Where raw SQL is unavoidable, use
Liquibase's `dbms` attribute to supply both, and say in a comment why.
No JSONB, no vendor-specific defaults — decision 0.1 forbids them.

## Part 3 — Local stack

- Add a `postgres:16` service to `docker-compose.yml` and point the backend at
  it by default. Keep the named volume pattern.
- **Keep DB2 available**, moved behind a compose profile or an override file, so
  the DB2 path can still be exercised. It is a supported target, not a legacy
  one.
- Update `docker-compose.client-dev.yml` consistently.
- Say in `docs/CONFIGURATION_GUIDE.md` how to run against each.

## Part 4 — Prove it on both

**There is currently no test that touches a database at all** — no H2, no
Testcontainers, no test `application.yml`. All 354 tests mock their
repositories. So "the schema is portable" would otherwise be an untested claim,
and this brief exists because an untested schema claim already bit once.

- Add **Testcontainers PostgreSQL** and one integration test that starts an
  empty database, runs the changelog, and asserts Hibernate's `validate` passes
  and both repositories round-trip a row.
- **DB2 stays manual** — the image is emulated and slow, and putting it in the
  default build would make every test run miserable. Add a DB2 profile to the
  same test, off by default, and record a run of it in the brief report.
- Keep the default `mvn test` fast. Gate the container test the way
  `AnalysisEmittedSqlPrestoValidateIT` is gated, and say how to run it.

## Acceptance

- `mvn -f backend/pom.xml test` green on **JDK 17** (no root pom). Report the
  count — it was **354**.
- Backend starts against an **empty PostgreSQL**, creates the schema via
  Liquibase, passes `validate`.
- Backend starts against the **existing DB2** after `changelogSync`, passes
  `validate`, and the existing registrations and column metadata are still
  there and readable. **This is the one that matters** — do not report done
  without it.
- `grep -rn "DB2Dialect\|ddl-auto: update" backend/src/main/resources` returns
  nothing.
- `npm run build` unaffected.
- Report the DB2 manual run, and anything in the changelog you could not express
  neutrally.

## Do not

- Do not drop DB2 support or delete its driver.
- Do not "simplify" by regenerating the baseline from `ddl-auto` on an empty
  database — that loses the three migrations and reintroduces the exact drift
  001 documents.
- Do not touch the **analytical** plane. Presto is unaffected: no ORM, no
  migrations, no dialect. Only the operational datasource changes.
