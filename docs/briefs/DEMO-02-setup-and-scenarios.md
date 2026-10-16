# DEMO-02 — Product setup, scenario runner, demo script (Cursor agent B)

Read `docs/briefs/DEMO-00-contract.md`, `CLAUDE.md`, `docs/PRODUCT_PLAN.md` (§7 scoping,
§10 external sources) and `docs/RUNNING_LOCALLY.md`. Agent A (DEMO-01) is building the
database in parallel; start from the contract and test on its M1 (20k-row) data.

## Deliverables (all under `scripts/demo/`, plus `docs/demo/`)

1. **`setup.sh` (or `.mjs`/`.py`; no new heavy deps)** — idempotent, drives the product
   **through its admin API** (`/api/admin/data-sources`, `/api/admin/lakehouse/registrations`,
   `/api/admin/scope/*`, `/api/admin/users/*`; read the controllers). Re-runnable; `--reset`
   removes only what it created. It must:
   - save the Data Source `Demo Business DB` (PostgreSQL, role `demo_ro`, DB `analytics_demo`)
     with Presto catalog alias `demo_pg`; run the connection test;
   - register the five contract tables with business names, layer tags, Source System label,
     **fuzzy flags** on name columns, `compare_as` overrides where needed (e.g. `beneficiary_ref`
     varchar vs `beneficiary_id` bigint), hidden columns (e.g. `account_no` hidden), and
     `value_picker_enabled` on categorical columns;
   - create scope dimensions/levels/nodes (Geography: District -> Taluka -> Village;
     Department: flat), bind each scoped table at its **coarsest** column (`district_code`),
     flag `district_master` as shared reference;
   - create users `jaipurofficer` (Jaipur subtree, MFA on), `sanganeruser` (Sanganer taluka),
     `jaipuradmin` (scoped admin), keep `superadmin`.
2. **Config for 20-minute fuzzy:** document and apply `SRSE_QUERY_TIMEOUT_SECONDS=1200`,
   and find every other timeout between browser and engine (Next.js proxy, Tomcat/async,
   fetch/AbortController in the frontend, any reverse proxy in compose) that would cut a
   20-minute stream first. List each, with file:line, and the value you set. Frontend
   changes only if a hard-coded limit exists; keep them minimal and call them out.
3. **`run-scenarios.*`** — executes every scenario below through the real API (log in as
   the relevant user, same endpoints the UI uses), records row count, wall time, and the
   emitted SQL where available, and writes `docs/demo/SCENARIO_RESULTS.md`:

   | # | Scenario | User |
   |---|---|---|
   | 1 | INNER / LEFT / RIGHT / FULL: beneficiary x bank on id (cross-type varchar vs bigint) | superadmin |
   | 2 | **Fuzzy name match**, beneficiary x bank, score threshold, 1M unscoped (record time) | superadmin |
   | 3 | Same fuzzy, scoped | jaipurofficer (must return strictly fewer rows) |
   | 4 | COMBINE: `full_name` vs `first_name`+`last_name` (pension) | superadmin |
   | 5 | ANY_OF: `mobile` vs `phone_primary`/`phone_secondary` | superadmin |
   | 6 | Post-join compare: income, status; `mismatchOnly`; fuzzy comparison | superadmin |
   | 7 | Dedup on ration_card; group-by district; filters; value pickers | superadmin |
   | 8 | Multi-target: beneficiary vs bank + pension (+ one deliberately failing target) | superadmin |
   | 9 | Suggest-keys (metadata and overlap probe) | superadmin |
   | 10 | Guardrails: fan-out refusal on a low-cardinality key (message includes estimate); CSV export > 10k rows complete; 10k render cutoff | superadmin |
   | 11 | Scoping: same query as superadmin / jaipurofficer / sanganeruser; unbound table denied; shared reference join does not relax filter | all |
   | 12 | Audit: every action above appears; shape only, no bound values; jaipuradmin sees only own subtree | superadmin, jaipuradmin |
   | 13 | Saved query create/run; exports (CSV/XLSX) | superadmin |

   Each scenario asserts an expected property (not just 200 OK): row-count relationships,
   scoped < unscoped, NULL verdicts on unmatched rows under outer joins, etc.
4. **`docs/demo/DEMO_SCRIPT.md`** — presenter's click-path per scenario (screens, what to
   pick, what to say, the expected count/time from the results file), ordering that builds
   to the 1M fuzzy match, a pre-demo checklist (containers, backend flags, OTP from log,
   warm-up query), and a fallback for each risky step.

## Acceptance

- `setup.sh` twice in a row is a no-op the second time; `--reset` then `setup.sh` works.
- All 13 scenarios pass on M1 data, then on M2 (1M) data, with times recorded.
- Scenario 2 on 1M either completes (record time) or fails with a clear reason you report.
- No guardrail was loosened except the query timeout.

## Rules

- Don't touch `docker/demo/**` (agent A). Need a data change? Write it in your hand-off.
- Don't edit backend/frontend source to make a scenario pass. A product defect gets
  reported with the failing request and SQL, then you move on.
- Quirk to know: under LEFT, ANY_OF returns extra rows for unmatched candidate columns
  (`PRODUCT_PLAN.md` 10.A.4). Pick scenario 5 settings and the narration accordingly.
