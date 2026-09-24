# Brief AA-04 — Restore the admin config backup, trimmed

**Repo:** `analytics-advisor` · **Closes:** `FORK.md` TODO 1 / `PRODUCT_PLAN.md` 1.2
**Follows:** AA-03 (`f14c5f7`)

## Why

An admin configures connections, registers lakehouse tables and sets column
metadata by hand on `/admin456`. Without an export, a redeploy or a fresh
operational volume means re-entering all of it. SRSE had this; it was removed at
the fork rather than trimmed, because the bundle carried field mappings and
schemes alongside the parts worth keeping, and a half-edited import silently
loses config.

Restore it carrying **three sections only**: connections, lakehouse
registrations, analysis column metadata.

The originals are at `srse-pre-fork` and are a good starting point — read them
before writing:

```
git show srse-pre-fork:backend/src/main/java/gov/rajasthan/smart/srse/web/AdminConfigBundle.java      #  79 lines
git show srse-pre-fork:backend/src/main/java/gov/rajasthan/smart/srse/web/AdminConfigController.java  #  51
git show srse-pre-fork:backend/src/main/java/gov/rajasthan/smart/srse/web/AdminConfigService.java     # 410
git show srse-pre-fork:backend/src/test/java/gov/rajasthan/smart/srse/web/AdminConfigControllerTest.java # 104
```

Every dependency they need still exists: `ConnectionOverrideStore`,
`AnalyticalConnectionService`, `OperationalConnectionService`,
`RegisteredTableRepository`, `AnalysisColumnMetadataRepository`, and
`LakehouseRegistryService.importRegistration` (still present, still
null-layer tolerant).

## Part 1 — `AdminConfigBundle`

Carry over `ConnectionBundle`, `ConnectionPlane`, `RegisteredTableEntry` and
`AnalysisColumnMetadataEntry` unchanged. **Drop** `FieldCatalogEntry`,
`FieldColumnMappingEntry` and `SchemeEntry`, and the `FieldTier` / `FieldDataType`
imports that go with them.

- `CURRENT_SCHEMA_VERSION` becomes **`"2.0"`**. The shape changed; a version that
  lies about it is worse than no version.
- Keep natural keys only — no database ids — so a bundle round-trips across
  redeploys and fresh volumes. That is the whole point of the format.
- **Keep `dataMode` in the bundle** as an informational field. It no longer keys
  anything (the per-environment mapping left with the fork) but it tells whoever
  opens the file which deployment it came from. Do not make import branch on it.

## Part 2 — `AdminConfigService`

Trim `export()` and `importConfig()` to the three surviving sections. Drop the
`FieldCatalogRepository`, `FieldColumnMappingRepository`,
`FieldColumnMappingService`, `SchemeRepository` and `FieldResolver`
constructor dependencies, and the `@CacheEvict(cacheNames = "fieldMappings")` —
that cache went with the mappings.

**Keep import as upsert/merge. There is no `deleteAll` in the original and there
must not be one.** An import adds and updates; it never removes what is already
there. An admin importing a partial bundle must not lose registrations the
bundle does not mention.

Three changes of substance beyond trimming.

### 2a. An absent section must be reported, not silently skipped

The original guards every section with `if (bundle.X() != null)`, so a section
missing from the file is skipped in total silence. That is precisely the failure
`FORK.md` warns about: import a bundle exported by a different build, get a
partial restore, and be told nothing.

Add a `skipped` list to `ImportResult` naming every section that was absent or
empty, and have the UI show it. "Registrations: not present in this file" is a
sentence an admin can act on; silence is not.

### 2b. A 1.0 bundle must be accepted and its dead sections named

`validateSchemaVersion` currently rejects anything that is not the current
version. A 1.0 bundle is a real thing that real deployments have on disk, and
its connections, registrations and column metadata are still perfectly valid.

Accept `"1.0"` and `"2.0"`. For a 1.0 bundle, import the three sections that
survive and add `fieldCatalog`, `fieldColumnMappings` and `schemes` to the
`skipped` list with a reason ("not part of this product"). Reject anything else
with the message the original used.

### 2c. Export must not emit connection passwords

This is the one the original got wrong.
`planeFromOverrides` reads `overrides.getProperty(prefix + ".password", "")`, so
whenever an admin has set a connection override — exactly when this feature is
used — the exported JSON contains the DB2 and Presto passwords in clear text.
The file is a browser download. It lands in Downloads, gets emailed, gets
attached to a ticket.

- **Export writes `null` for both passwords.** Never the value, never a
  round-trippable placeholder.
- **Import treats an absent or null password as "leave the existing one
  alone"** — it must not overwrite a working connection with a blank. Only a
  non-blank password in the file changes anything.
- Say so in the UI: the download restores everything except the two passwords,
  which are re-entered once after import.

If full fidelity is genuinely wanted later, that is an opt-in
`?includeSecrets=true` with an explicit warning — **not** the default, and not
part of this brief.

## Part 3 — `AdminConfigController`

Restore as-is at `/api/admin/config` with `GET /export` and `POST /import`.
Rename the download to `analytics-advisor-config-<date>.json`.

No `SecurityConfig` change is needed: `/api/admin/**` already requires
`SRSE_ADMIN`. **Confirm this with a test rather than by reading the matcher** —
add an officer-token case to `SecurityConfigRbacTest` expecting 403 on both
endpoints.

## Part 4 — Frontend

Restore `ConfigBackupPanel` in `src/app/admin456/page.tsx` and its two calls in
`src/lib/adminApi.ts`. AA-02 removed both; `git show 100a930^:...` has them.

Render it at the top of `AdminPage` as before, and wire `onImported` to the
existing `refresh` callback so registrations and column metadata reload together
— the comment above that shared state explains why they must.

The panel must show the `skipped` list from 2a and the password note from 2c.
An import that silently did half the job is the thing this brief exists to
prevent.

## Part 5 — Tests

Restore `AdminConfigControllerTest`, trimmed to the three sections. Add:

- **Round trip**: export → import into a store with different contents → the
  three sections match, and nothing that was already there was removed.
- **Password masking**: with a connection override set carrying a password,
  the exported JSON has `null` for it (assert on the serialised JSON, not the
  record — this is about what reaches the file).
- **Password preservation**: importing a bundle with a null password leaves the
  existing connection working.
- **1.0 acceptance**: a 1.0 bundle imports its three surviving sections and
  reports the other three as skipped.
- **Absent section**: a bundle with no `registeredTables` reports it skipped and
  leaves existing registrations intact.
- **RBAC**: officer token → 403 on export and import.

## Acceptance

- `mvn -f backend/pom.xml test` green with **JDK 17** (no root pom; `-pl backend`
  does not work). Report the new count — it was **345**.
- `npm run build` and `npm run test` clean in `frontend/`.
- `grep -rn "FieldCatalog\|FieldColumnMapping\|SchemeEntry\|SchemeRepository" backend/src/main/java/gov/rajasthan/smart/srse/web/`
  returns **nothing**.
- Update `FORK.md` TODO 1 to done, and remove the admin-config bullet from
  `CLAUDE.md`'s "Known broken and unfinished".

## Manual check

Configure something on `/admin456` — register a table, set a column's business
name and comparison mode. Export. Confirm the file contains the registration and
the column metadata and **does not contain either password**. Change something,
import the file back, and confirm the change is restored and nothing else was
lost.
