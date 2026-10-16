# Analytics Advisor demo script (DEMO-02)

Presenter click-path. Counts and times are from the M2 1,000,000-row run on
2026-10-16 (`docs/demo/SCENARIO_RESULTS.md`, `run-scenarios.sh --include-fuzzy`).
Agent A's data is final (`docs/demo/DATA_PROFILE.md`).

Ordering builds to the headline fuzzy (scenario 2), then the same question
scoped, then the rest of the engine, then governance.

### Measured results (M2, product API)

| # | What | Rows | Wall | Fan-out guard |
|---|---|---:|---:|---|
| 1 | INNER / LEFT / RIGHT / FULL (id, cross-type) | 855,000 / 1,004,250 / 900,000 / 1,049,250 | 14s / 12s / 12s / 11s | passed |
| **2** | **Fuzzy name ≥85% + exact mobile, unscoped** | **675,387** | **14.7s** | **passed** |
| **3** | **Same, jaipurofficer (JP)** | **222,170** | **10.6s** | **passed** |
| 2b | Name-only (no mobile) | — | — | **skipped** (~735M estimate > 50M cap) |
| 4 | COMBINE `full_name` vs first+last @ 85% + exact mobile↔phone_primary | 36,274 | 20.4s | passed |
| 5 | ANY_OF mobile vs phones, INNER | 300,000 | 13s | passed |
| 6 | mismatch-only vs all LEFT compares | 328,815 / 1,004,250 | 41s | passed |
| 7 | Group-by district; ration dedup | 7 districts; 370,515 | 23s | passed |
| 8 | Multi-target bank + pension | 855,000 + 300,000 | 28s | status target HTTP 400 (~450B) |
| 9 | Suggest-keys meta + probe | 20 + 20 | 25s | — |
| 10 | Fan-out on `status`; complete CSV | refused; CSV 855,000 (~87 MB) | 29s | refused (correct) |
| 11 | superadmin / Jaipur / Sanganer | 855,000 / 273,579 / HTTP 400 | ~15s / 12s | passed |
| 12 | Audit (superadmin / jaipuradmin) | 93 / 27 events | — | — |
| 13 | Saved query + CSV/XLSX | 500-row rerun | 17s | passed |

## Pre-demo checklist

1. **Postgres** (operational + `analytics_demo`): `docker compose up -d postgres`
2. **Demo data + Presto** (Agent A):
   ```bash
   docker compose -f docker/demo/docker-compose.yml up -d
   ./docker/demo/init-db.sh
   ROWS=20000 ./docker/demo/seed.sh          # M1 rehearsal
   # ROWS=1000000 ./docker/demo/seed.sh      # M2 show
   ./docker/demo/smoke.sh
   ```
3. **Backend flags** (local auth, demo Presto, 20-minute timeout, OTP in the log):
   ```bash
   # in repo-root .env  (see scripts/demo/demo.env.example)
   SRSE_AUTH_MODE=local
   SRSE_OTP_SENDER=log
   SRSE_QUERY_TIMEOUT_SECONDS=1200
   SPRING_MVC_ASYNC_REQUEST_TIMEOUT=1260000
   SRSE_PRESTO_URL=jdbc:presto://localhost:8083   # host-run backend
   # dockerised backend: jdbc:presto://host.docker.internal:8083
   ```
   Restart backend after changing any of these. Inventory: `scripts/demo/TIMEOUTS.md`.
4. **Product setup** (idempotent): `./scripts/demo/setup.sh`
   - Second run must be a no-op. `--reset` then `setup.sh` restores the contract.
5. **OTP**: `jaipurofficer` has MFA on. Login returns a challenge; the six digits
   are in the backend log (`OTP [EMAIL] to … — Your SRSE verification code is: ######`).
   `docker logs --since 2m <srse-backend-container>`
6. **Warm-up** (do this before the room sits down): run scenario 1 INNER and
   scenario 2 so Presto has the pages hot —
   `./scripts/demo/run-scenarios.sh --include-fuzzy --only 1 2`
7. Frontend at http://localhost:3000 with `NEXT_PUBLIC_AUTH_MODE=local`.

### Accounts (passwords in `docs/RUNNING_LOCALLY.md`)

| User | Role | Scope | MFA |
|---|---|---|---|
| `superadmin` | Super Admin | none (sees every district) | off |
| `jaipurofficer` | Officer | Jaipur (`JP`) subtree | **on** |
| `sanganeruser` | Officer | Sanganer taluka (`SG`) | off |
| `jaipuradmin` | Admin + audit reader | Jaipur subtree | off |

Officer-facing tables live at `demo_pg.demo.*`. The Data Source also registered
`jdbc_<id>.demo.*` and left those **unbound** — that is the deny-by-default exhibit.

---

## Suggested order (25–35 minutes)

Open with the problem, not the product: two systems that should agree about the
same person, and do not. Then Admin (so the audience sees the allow-list), then
Query Builder, then the 1M fuzzy, then scope, then the rest.

### 0. Frame (1 min)

> We are reconciling Rajasthan welfare records. One gold beneficiary file, a
> silver bank extract, a pension register, ration cards. Nobody in this room
> writes SQL. The engine does. Synthetic data, obviously fake IDs.

### 1. Data Sources + Registry — `/admin456?section=data-sources` then `registry`

**Pick:** Data Sources → `Demo Business DB` (PostgreSQL, `demo_ro`,
`analytics_demo`, Presto alias `demo_pg`). Click Test if asked.

**Say:** The operational database is not this database. Officers never get a
connection string. They get five registered tables.

**Then:** Table Registry → `demo_pg.demo.beneficiary` (GOLD) and the three SILVER
tables. Source System label `Rajasthan Welfare Demo`. Open scope on beneficiary:
bound at `district_code` (and taluka/village, because the table carries them).
`district_master` is shared reference.

**Fallback:** if Test fails, Postgres is down or `demo_ro` was not created —
rerun `./docker/demo/init-db.sh`. If `demo_pg` is missing from the cascade, the
backend is still pointed at the Iceberg Presto (8081), not 8083.

### 2. Column settings — `/admin456?section=columns`

**Pick:** `full_name` / `account_holder_name` fuzzy on; `account_no` hidden;
`beneficiary_ref` compare-as **NUMBER** (varchar vs bigint); `district_code` /
`status` / `gender` value-picker on.

**Say:** Fuzzy is an admin decision per column, then per group. Number-vs-text
defaults to numbers so `'000123'` and `123` are the same person.

**Fallback:** settings live in the operational store; a stale snapshot cannot
override them. If a picker column is missing, it was not flagged
`value_picker_enabled`.

### 3. Users and MFA — `/admin456?section=users`

**Pick:** `jaipurofficer` — Jaipur, MFA on. Mention `sanganeruser` (taluka) and
`jaipuradmin` (scoped admin + audit).

**Say:** Scope is a subtree. A district officer sees the district and everything
under it. MFA is a per-user toggle; the code is emailed and SMSed.

### 4. Scenario 1 — join types, cross-type key (superadmin)

**Screen:** `/query-builder` → Report Analysis.

**Pick:** Source `demo_pg.demo.beneficiary.beneficiary_id` (bigint). Target
`demo_pg.demo.bank_account.beneficiary_ref` (varchar, zero-padded). Display
name, district, income, status. Run INNER, then LEFT, then mention RIGHT/FULL.

**Say:** Same identifier, two types. Presto will not coerce that; we `TRY_CAST`
the text side as a number so one dirty row cannot fail the match.

**Expect:** INNER **855,000** (~14s) < LEFT **1,004,250** (~12s) < FULL
**1,049,250** (~11s). RIGHT **900,000**. LEFT unmatched rows carry
`match_status = NO_TARGET` and **NULL** comparison verdicts, not false.

**Fallback:** if the query 400s on types, `beneficiary_ref` lost its NUMBER
override — re-run `setup.sh`. If it 503s, Presto 8083 is down.

### 5. Scenario 2 — fuzzy name ≥85% + exact mobile (superadmin)  ★ headline

**Pick:** Clear the id pair. Two join groups, INNER:
1. `full_name` vs `account_holder_name`, fuzzy **85%**
2. `beneficiary.mobile` vs `bank.mobile`, **exact**

Display both names and both mobiles. Run. **Do not** run name-only (2b) —
prefix-3 name fan-out is ~735 million, above the 50 million guard.

**Say:** A million beneficiaries, nine hundred thousand accounts, names with
planted typos. Blocking is the first three characters. The mobile AND is what
keeps the candidate set honest and the fan-out guard quiet. This is the
question that used to mean a data-science engagement.

**Expect:** **675,387** rows in **14.7s**. Fan-out guard **passed** (the query
ran; it was not refused). Same count as Agent A's Presto-direct rehearsal
(675,387 in ~3s, `DATA_PROFILE.md`) — the extra ~12s is product planning.
If it refuses with an estimated-row message, the guard fired — do not raise it.

**Fallback:** still running at 10 minutes — leave it, talk through scenarios 5
on a second tab (ANY_OF is ~13s). If Tomcat returns 503 at ~30s,
`SPRING_MVC_ASYNC_REQUEST_TIMEOUT` was not applied.

### 6. Scenario 3 — same fuzzy+mobile, scoped (jaipurofficer)

**Pick:** Log out. Log in as `jaipurofficer`. Read OTP from the backend log.
Re-run the *exact* same two groups (name ≥85% + exact mobile).

**Say:** Same SQL shape. The engine added `district_code IN ('JP')`. The count
must be strictly smaller. That is the product, not a filter the officer remembered
to apply.

**Expect:** **222,170** rows in **10.6s**, strictly less than unscoped 675,387.
Fan-out guard **passed**. SQL carries `district_code IN ('JP')` on both sides.

**Fallback:** MFA code missing → `--srse.otp.sender=log`. Zero rows → node code
is not `JP` (scope matches codes, not names).

### 7. Scenario 4 — COMBINE (superadmin)

**Pick:** Two join groups, INNER:
1. `beneficiary.full_name` vs pension `first_name` + `last_name`, COMBINE,
   space separator, fuzzy **85%**
2. `beneficiary.mobile` vs `pension.phone_primary`, **exact**

**Say:** The two sides do not have to be the same width. Order is the officer's
and it matters — Levenshtein is order-sensitive. We fold with `array_join`,
not `concat_ws`, so a missing middle name is not charged an edit. The mobile
AND keeps the candidate set in the same league as scenario 2.

**Expect:** **36,274** rows in **20.4s**, under 10 million. SQL contains
`array_join` and `src.mobile = tgt.phone_primary`.

### 8. Scenario 5 — ANY_OF (superadmin)

**Pick:** `beneficiary.mobile` vs pension `phone_primary` / `phone_secondary`.
Mode ANY_OF. **Join type INNER** (say this out loud).

**Say:** The mobile sits in either phone column. We do **not** put `OR` in the
ON clause — that costs Presto the hash join. We `CROSS JOIN UNNEST`. Under LEFT,
ANY_OF also returns extra rows for unmatched candidate columns; that is why
this demo stays on INNER.

**Expect:** **300,000** rows in **13s**. SQL contains `CROSS JOIN UNNEST`, no OR.

### 9. Scenario 6 — post-join compare (superadmin)

**Pick:** Back to the id join, LEFT. Comparison groups: income, status, and a
fuzzy name compare. Toggle mismatch-only.

**Say:** Comparisons are not join keys. They never enter ON. Unmatched LEFT rows
get NULL verdicts. Mismatch-only keeps no-counterpart rows — three-valued logic
would otherwise drop them.

**Expect:** mismatch-only **328,815** < all LEFT **1,004,250** (~41s).

### 10. Scenario 7 — dedup, group-by, filters, pickers (superadmin)

**Pick:** beneficiary × ration_card on `beneficiary_id`. Dedup on ration
`beneficiary_id` (the seed plants ~8% duplicate persons). Filter status = ACTIVE
via the value picker. Group by `district_code`, COUNT(*).

**Say:** Dedup is a view — nothing is deleted. The picker is live distincts,
admin-gated, and already scoped.

**Expect:** 7 district rows; picker lists `JP`; ration dedup **370,515** (~23s).
Dedup cannot be combined with group-by — run them as two requests.

**Fallback:** picker 400 "not available" → column is not `value_picker_enabled`.

### 11. Scenario 8 — multi-target (superadmin)

**Pick:** Hub beneficiary. Targets: bank (`beneficiary_ref`) and pension
(`beneficiary_id`). Do **not** type an unregistered table — that 400s the
whole request before the stream starts.

**Say:** This is N two-table joins, never an N-way join. The 120-second
multi-match budget is the wall clock for the whole stream.

**Then (optional, if time):** add a third target joining on `status`. That is a
~450-billion-row fan-out. Today `planMultiMatchAudited` plans every target
*before* the stream, so the whole request is HTTP 400 with the estimate — not
an in-band NDJSON error. CSV export of a multi-target is all-or-nothing.

**Expect:** bank **855,000** + pension **300,000** in **28s**. The status target
is HTTP 400 with the ~450-billion estimate.

### 12. Scenario 9 — suggest keys (superadmin)

**Pick:** Suggest keys, beneficiary vs bank, metadata first, then probe.

**Say:** Metadata is free. The probe samples the source and scans the target,
capped. It should surface `beneficiary_id` ~ `beneficiary_ref` despite the
type mismatch.

**Expect:** 20 metadata pairs and 20 probe pairs in **25s**. Top metadata is
same-name columns (`district_code`, `mobile`, `status`).

### 13. Scenario 10 — guardrails (superadmin)

**Pick:** Join beneficiary to bank on `status` (two values). Run.

**Say:** We estimate `source × target / distinct` from catalog stats. Above
50 million we refuse with the number in the message — we do not wait 20 minutes
to time out. That guard was **not** raised for this demo.

**Then:** export the *id* match as CSV. “The grid stops at 10,000. This file is
the complete result, re-run from the engine.”

**Expect:** refusal contains “about 450,000,000,000 rows” (limit 50,000,000).
CSV of the id match is **855,000** rows / ~87 MB in **21s**, not 10,000.

### 14. Scenario 11 — three users, deny-by-default, shared reference

**Pick:** Same INNER id match as scenario 1, as superadmin, then jaipurofficer,
then sanganeruser.

**Say:** Superadmin sees every district. Jaipur is a strict subset. Sanganer is
a taluka officer: bank is bound only at district, so that table is invisible —
we will not hand them the whole of Jaipur. That is deny-by-default.

**Then:** try `jdbc_<id>.demo.beneficiary` (the Data Source registration, never
bound). Jaipur officer is refused.

**Then:** join beneficiary to `district_master` (shared). Jaipur still sees only
Jaipur. A shared reference never relaxes the scoped side.

**Expect:** superadmin **855,000**; jaipurofficer **273,579**; sanganeruser
**HTTP 400** (bank not visible at taluka). Shared-ref join still scoped: Jaipur
**320,000** < superadmin **1,000,000**. Unbound `jdbc_*` denied.

### 15. Scenario 12 — audit (`/admin456?section=audit`)

**Pick:** As superadmin, filter from the start of the demo. Point at
QUERY_EXECUTED / EXPORT / LOGIN / MFA lines. Open a query_shape: placeholders,
no Aadhaar, no names.

**Then:** log in as `jaipuradmin`. Same viewer. Sanganer is a taluka of Jaipur,
so `sanganeruser` **is** in this subtree. `superadmin` is not.

**Say:** We log the shape, never the bound values. The log is itself scoped.

**Expect:** superadmin sees the day's QUERY_*/EXPORT/LOGIN/MFA lines (93 in the
verification run). jaipuradmin sees the Jaipur subtree including `sanganeruser`
(27 events), never `superadmin`. Shapes have `?`, no mobiles.

### 16. Scenario 13 — saved query + export (superadmin)

**Pick:** Save the INNER id match. Re-open it. Export CSV and Excel.

**Say:** A saved query is the request, not a cached result. Export re-runs.

**Expect:** Re-run returns the 500-row slice in **17s**; CSV and XLSX both
non-empty.

---

## Risky-step fallbacks (quick)

| Symptom | Likely cause | What to do on stage |
|---|---|---|
| “Failed to fetch” / CORS 403 | `SRSE_FRONTEND_ORIGINS` ≠ the browser origin | Use http://localhost:3000; restart backend |
| Login 404 | `SRSE_AUTH_MODE` is still `mock` | Restart with `local` |
| No OTP in the log | sender is not `log`, or wrong container | `docker logs --since 2m` on the backend container |
| `demo_pg` missing | backend still on Iceberg Presto :8081 | `SRSE_PRESTO_URL=jdbc:presto://localhost:8083` |
| Fuzzy dies at ~30s | Tomcat async timeout | `SPRING_MVC_ASYNC_REQUEST_TIMEOUT=1260000` |
| Fuzzy refused with a huge estimate | name-only (2b) or `status` join | Do not raise the guard; add exact mobile as in scenario 2 |
| Scoped count = unscoped | bindings missing or node code ≠ `JP` | Re-run `setup.sh` |
| Sanganer sees Jaipur bank rows | would be a product defect — stop and say so | Do not demo past it |
| Data source test fails | `analytics_demo` / `demo_ro` missing | `./docker/demo/init-db.sh` |

## After the room

```bash
./scripts/demo/run-scenarios.sh --include-fuzzy   # writes SCENARIO_RESULTS.md
# 2b (name-only) is never run — it trips the 50M fan-out guard.
```
