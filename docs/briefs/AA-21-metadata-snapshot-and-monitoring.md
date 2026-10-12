# Brief AA-21 — Metadata snapshot, value pickers, active queries (Aa1)

**Repo:** `analytics-advisor` · **Source:** `docs/Aa1.pdf` · **Decisions:** `PRODUCT_PLAN.md` §0 (0.9–0.12), settled **2026-10-12**

## Why this brief exists

Stakeholders asked to cut Presto metadata and distinct-value load while keeping
data correct and scoped. Four decisions are signed off; this brief is the build
spec. It **narrows** the prior “re-read live columns on every call” rule for
**display only**; execution gates are unchanged.

---

## Part 1 — Snapshot store (0.9)

### Authority

- **Source of truth:** Liquibase-managed tables in the **operational** plane
  (PostgreSQL default, DB2 supported — decision 0.1).
- **Plain column types only** — no JSONB operators, no dialect-specific DDL.
- **Optional JSON export:** generated on demand (admin download or scheduled
  export). **Never imported** or read back as authority. The PDF “master JSON”
  is this artifact.

### Suggested schema (names illustrative; fit existing naming in changelog)

- **`metadata_sync_run`** — `id`, `started_at`, `finished_at`, `trigger`
  (`SCHEDULED` | `REGISTRATION` | `MANUAL`), `overall_outcome`.
- **`table_column_snapshot`** — `(catalog_name, schema_name, table_name,
  column_name, data_type, synced_at)` plus FK or run id to the sync that wrote
  the row. Replace-all per table on successful sync (delete prior rows for that
  table, insert fresh set).

### Sync job

- **Scope:** every row in `registered_table` (lakehouse and external `jdbc_*`
  addresses), using the same introspection paths as today:
  - Presto-backed registrations → existing browse/describe path.
  - External → `ExternalDataSourceMetadataService`.
- **Schedule:** `@Scheduled` cron, default **weekly**; config
  `srse.metadata.sync.cron`, `srse.metadata.sync.enabled`.
- **Triggers:**
  - After successful **table registration** (async or same transaction post-commit).
  - Super Admin **“Sync now”** (`POST /api/admin/metadata/sync` or similar).
- **Failure:** per-table error recorded on the run; **retain last good snapshot**
  for that table; set run-level “degraded” for UI badge.

### JSON manifest export (optional)

Example shape:

```json
{
  "generatedAt": "2026-10-12T…",
  "syncRunId": 42,
  "tables": [
    {
      "catalog": "iceberg_data",
      "schema": "jan_aadhar_txn",
      "table": "table1",
      "columns": [{ "name": "district_code", "dataType": "varchar" }]
    }
  ]
}
```

---

## Part 2 — Query Builder reads snapshot for display (0.10)

### Cached (snapshot, 7-day TTL)

- Physical **column name** and **data type** shown in Query Builder cascades
  and attribute pickers.

### Never cached (operational DB, immediate)

- Table **registered** or not (officer allow-list).
- **`analysis_column_metadata`:** hidden, business name, fuzzy, compare-as,
  **`value_picker_enabled`** (Part 3).
- **Scope bindings** and shared-reference flag — officer visibility.

### Order of operations on `listColumns` (mandatory)

1. Officer may see this table at all (registry + scope / deny-by-default).
2. Load admin metadata overlays from JPA.
3. If snapshot exists and `synced_at` within TTL → merge snapshot column names/types
   with overlays (hide, labels).
4. Else → **live** introspection (today’s behaviour).

Snapshot is **global per table**; step 1 ensures a scoped officer never sees
columns for a table they cannot access.

### Run time (unchanged safety)

- **`validateColumn`** before SQL emit: registered **and** column exists **live**
  **and** not hidden.
- Dropped upstream column after sync → **400** with clear message, not silent
  wrong results.

### TTL and fallback

- Default TTL **7 days** (`srse.metadata.snapshot.ttl-days`).
- **No snapshot or expired:** fall back to live read — **do not** refuse QB until
  sync runs (new registration must not be an outage).

### UI

- “**Metadata as of** &lt;date&gt;” near table/column pickers.
- **Warning badge** if the latest sync run failed or is degraded.

### Out of scope for snapshot

- Database Overview lazy expand may stay live or adopt snapshot later; decide in
  implementation — if adopted, same TTL rules apply.

---

## Part 3 — Distinct values / value pickers (0.11)

### Principle

Distinct values are **data**, not metadata. **No cache** in the first slice.

### `value_picker_enabled`

- New nullable/boolean column on **`analysis_column_metadata`**, default **false**
  for new and existing rows (behaviour change: pickers off until admin enables).
- **Backend:** `ColumnDistinctValuesService.plan()` returns **400** if flag false.
- **Frontend:** `RuleColumnValuePicker` / `ValueFilterPicker` only when enabled;
  free-text entry remains (already supported).

### Admin UX

- Column editor: checkbox “Allow value picker (low cardinality)”.
- After sync (or dedicated lightweight stats pass on **flagged columns only**),
  show **suggestions**: e.g. high `approx_distinct/count` → “Unlikely suitable”.
  **Never auto-enable.**

### Future (not in first slice)

- Distinct-value cache **only** if ever needed, **only** on tables marked
  **shared reference** (unscoped — no cross-district leak).

---

## Part 4 — Active queries & audit duration (0.12)

### Active queries dashboard

- **Who:** Super Admin only (`SRSE_ADMIN` / super role).
- **What:** queries **started by this backend** (match, multi-match, column-values,
  comparison summary, exports’ Presto leg, etc.) — register at start, clear on
  complete/timeout/error.
- **Fields:** internal id, user id/name, endpoint label, table summary (from
  request shape), `startedAt`, state (`RUNNING` | `DONE` | `FAILED`), optional
  error summary (no bound values).
- **Storage:** **in-memory** per JVM — document that multi-container deploys show
  **one instance only**; move to DB only if horizontal scale is required.
- **Not in scope:** cluster-wide `system.runtime.queries` correlation on Presto
  0.297 — leave to Presto UI / ops.

### History

- Extend existing **`audit_event`** (or capture path): record **duration ms** on
  `QUERY_EXECUTED` (and exports where applicable). **No second store.**

---

## Testing checklist

- Scoped officer: cannot list columns for out-of-scope table even if snapshot
  row exists.
- Hidden column: absent from picker immediately after admin hide, regardless of
  snapshot age.
- Run after upstream column drop: 400 at validate, not Presto opaque failure.
- Expired snapshot: live read succeeds without manual sync.
- `value_picker_enabled` false: column-values 400; UI text entry works.
- Active registry: entry appears during slow query, disappears after; no PII in
  dashboard payload.

---

## Estimated effort (from plan)

| Slice | Plan refs | Est |
|---|---|---|
| Snapshot schema + job + listColumns | §2.5 | ~10 d |
| Value picker flag + suggestions | §3.1.6 | ~2 d |
| Active queries + audit duration | §7.3.9–7.3.10 | ~4 d |

Build order: **3.1.6 (picker gate)** → **2.5 (snapshot)** → **7.3.9–10 (ops UI)**.
