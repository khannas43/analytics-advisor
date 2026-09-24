# Brief AA-03 — Remove the age filter

**Repo:** `analytics-advisor` · **Closes:** `FORK.md` TODO 3 / `PRODUCT_PLAN.md` 1.3
**Follows:** AA-02 (`100a930`), CLAUDE.md rewrite (`7e7b0e8`)

## Why

The Analysis page still offers an age-range checkbox. It always fails. It
resolved the abstract field key `age_years` through SRSE's field catalogue,
which left with the fork, so `StubFieldResolver` throws for every key.

Verified against the running backend:

```
POST /api/analysis/match.sql   ageFilter: null
  → 200  SELECT src.id AS "source_id" … FROM iceberg.srse.beneficiary src JOIN … WHERE TRUE

POST /api/analysis/match.sql   ageFilter: {"minAge":18,"maxAge":60,"unit":"YEARS"}
  → 400  Unknown or unmapped field key: age_years
```

An officer who ticks the box gets an error naming a field key that means nothing
to them. This is user-facing, not tidy-up.

**Decision: remove it.** A scheme-specific age control has no place in a general
analysis product, and `PRODUCT_PLAN.md` 4.5 (rules on source and destination,
operators AND-combined) and 5.1 (value filter) supersede it with a generic
per-column filter. Removing a broken control loses nothing; the history keeps it.

## Scope — read this before deleting anything

**Do NOT delete `FieldResolver`, `StubFieldResolver`, `RuleCompiler`, `Ast`,
`CompiledQuery` or `AliasRebase`, even though the age filter is their only
remaining caller.**

- `RuleCompiler` is a live `@Component` whose constructor injects
  `FieldResolver`. Deleting the resolver breaks the compiler.
- `PRODUCT_PLAN.md` 4.5 marks the rules feature **R+ — reused and extended**.
  `RuleCompiler` + `Ast` are that basis, and `AliasRebase` (with its 14 tests)
  solves the same problem rules will hit: rebasing a column expression onto a
  side alias, where "everything after the last dot" is the wrong answer.

Delete only what is specific to *age*.

## Part 1 — Backend

**Delete these files:**

```
git rm backend/src/main/java/gov/rajasthan/smart/srse/analysis/AgeFilterSpec.java \
       backend/src/main/java/gov/rajasthan/smart/srse/compiler/AgeYearsExpression.java \
       backend/src/test/java/gov/rajasthan/smart/srse/compiler/AgeYearsExpressionTest.java
```

**`analysis/RecordMatchService.java`** — remove the age plumbing. Sites (line
numbers pre-edit, verify before cutting):

| Lines | What |
|---|---|
| 573–574 | `if (req.ageFilter() != null) validateAgeFilter(...)` |
| 593–606 | FULL rejection, and the LEFT/RIGHT preserved-side checks |
| 608–632 | `validateAgeFilterOnPreservedSide` |
| 634–641 | `validateAgeFilter` |
| 679 | the `appendAgeFilter(...)` call |
| 1004–1048 | `appendAgeFilter` |
| — | the `AGE_UNITS` constant and `ageDivisor(...)` |
| 4, 7, 114, 122 | the `AliasRebase` and `FieldResolver` imports, the `fields` field, and the constructor parameter |
| 48, 57 | the Javadoc paragraphs describing the age filter |

Removing the `FieldResolver` constructor parameter changes the bean signature —
update every construction site, including tests.

**`RecordMatchRequest`** and **`MultiTargetRecordMatchRequest`** — drop the
`ageFilter` component. Both have a secondary constructor for backward
compatibility; keep that pattern intact for the remaining components so stored
requests still deserialise. An `ageFilter` key arriving in old JSON must be
**ignored, not rejected** — confirm Jackson is configured to ignore unknown
properties, and if it is not, say so rather than adding strict failure.

**`MultiTargetRecordMatchService`** — remove the age checks at ~237–238, ~249
and ~314.

**`config/ApiExceptionHandler`** — leave both `FieldResolver` mappings in place.
`RuleCompiler` still throws them and 4.5 will.

## Part 2 — Frontend

**`src/app/analysis/page.tsx`** — remove `ageFilterEnabled`, `minAge`, `maxAge`,
`ageUnit` state, the checkbox and its inputs (~1888–1900), the `ageFilter` keys
in both request builders (~917, ~956), and the FULL-join warning at ~1644 that
exists only because FULL rejects an age filter.

**`src/lib/analysisApi.ts`** and **`src/lib/multiTargetMatchBuild.ts`** — drop
`AgeFilterSpec` and the `ageFilter` field.

These are files AA-02 protected. They are in scope **for this change only**.

## Part 3 — Tests

`RecordMatchServiceTest` carries ~38 age-filter lines; `EmittedSqlParsesTest`,
`RecordMatchControllerTest`, `MultiTargetRecordMatchServiceTest`,
`JoinKeySuggestProbeTest`, `JoinKeySuggestServiceTest`, `RuleCompilerTest`,
`AliasRebaseTest` and `AnalysisEmittedSqlPrestoValidateIT` reference it more
lightly.

- Delete assertions **about age behaviour**.
- Where age was incidental — a request fixture that happened to set
  `ageFilter: null` — just drop the field and keep the test.
- **`AliasRebaseTest` and `RuleCompilerTest` must still pass unchanged.** If
  they don't, you have deleted too much.

## Part 4 — Record the landmine

`RuleCompiler` resolves **abstract field keys** through `FieldResolver`. This
product has no field keys: officers pick `catalog.schema.table.column` through
the registry cascade. So `RuleCompiler` cannot run as it stands — `StubFieldResolver`
throws for every key — and activity 4.5 will have to replace that seam with
registry-validated qualified columns, going through
`LakehouseRegistryService.validateColumn` exactly as the match engine does.

Add that to `CLAUDE.md` under "Known broken and unfinished", replacing the age
filter entry. Update `FORK.md` TODO 3 to done.

## Acceptance

- `mvn -f backend/pom.xml test` green. **Use JDK 17** — the enforcer rejects
  anything else, and there is no root pom, so `-pl backend` does not work.
- `npm run build` and `npm run test` clean in `frontend/`.
- `grep -rn "ageFilter\|AgeFilter\|age_years\|AgeYears" backend/src frontend/src`
  returns **nothing**.
- `grep -rn "FieldResolver\|RuleCompiler\|AliasRebase" backend/src/main` still
  returns hits — these survive deliberately.
- Report the new backend test count (was 365) and which tests you deleted rather
  than adjusted.

## Manual check

With the backend running, `POST /api/analysis/match.sql` with a plain two-column
match must still return SQL and **200**. Load `/analysis` and confirm the age
checkbox is gone and a match still runs end to end.
