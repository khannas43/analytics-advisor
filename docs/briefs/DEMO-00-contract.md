# DEMO-00 — Postgres-only full demo: shared contract

Read by **both** Cursor agents (DEMO-01 data, DEMO-02 setup + scenarios). If you need to
change anything here, change this file first and say so in your hand-off — the other
agent is building against it.

## Goal

A complete, impressive demo of Analytics Advisor that runs on a laptop with **PostgreSQL
only** (no Iceberg, Hive, MinIO). Every product feature is shown, **including fuzzy name
matching on ~1,000,000 records**, which is a headline selling point. Synthetic data only.

## Settled by the product owner (2026-10-16)

- Business data lives in a **new database `analytics_demo` on the EXISTING Postgres
  container** (compose service `postgres`, host port 5433, the one that holds `srse`).
  Never put business tables in `srse` (CLAUDE.md: two data planes, never conflated).
- Federation route: a **minimal Presto** (0.297 only) whose **only catalog** is
  `demo_pg` -> `analytics_demo`. The Data Source is saved in Admin with Presto catalog
  alias `demo_pg`. Fuzzy, ANY_OF, COMBINE and number-vs-text need Presto.
- Fuzzy on 1M rows may be slow. Raise the timeout rather than weaken anything:
  `SRSE_QUERY_TIMEOUT_SECONDS=1200` (20 min). **Do not** loosen the fan-out guard,
  blocking prefix or any other guardrail without writing down the measured reason.
- DB2 is out of scope. Rajasthan welfare story, synthetic data (obviously fake IDs).
- MFA stays ON for `jaipurofficer` (OTP read from backend log, `--srse.otp.sender=log`).
- Audit viewer is part of the demo.

## Data contract (schema `demo` in database `analytics_demo`)

All names lower-case, vendor-neutral types. Seed must be **deterministic** (same output every run).

| Table | Rows | Key columns |
|---|---|---|
| `beneficiary` (hub, SCOPED) | 1,000,000 | `beneficiary_id` bigint PK; `jan_aadhaar_no` varchar(12) fake; `full_name`; `father_name`; `gender`; `dob` date; `district_code` varchar(4); `district`; `taluka_code`; `taluka`; `village_code`; `department_code`; `annual_income` numeric; `mobile` varchar(10); `address`; `status` |
| `bank_account` (SCOPED) | ~900,000 (rev. 2026-10-16: 700k could not cover 85% of 1M) | `account_id` PK; `beneficiary_ref` **varchar** = beneficiary_id left-padded with zeros (cross-type vs bigint); `account_holder_name` (~18% single-edit typos, ~5% reordered/abbreviated); `declared_income`; `mobile`; `bank_name`; `ifsc`; `account_no`; `district_code`; `status` |
| `pension_register` (SCOPED) | ~300,000 | `pension_id` PK; `beneficiary_id` bigint; `first_name`; `last_name`; `phone_primary`; `phone_secondary` (beneficiary mobile sits in either); `pension_type`; `monthly_amount`; `district_code` |
| `ration_card` (SCOPED) | ~400,000 | `card_id` PK; `beneficiary_id` (**~8% duplicated persons** for dedup); `head_name`; `family_size`; `district_code` |
| `district_master` (SHARED reference) | 7 | `district_code`; `district_name`; `division` |

Distribution requirements (these make scenarios work — verify with a profile query):

- 7 districts incl. Jaipur (`district_code` e.g. `JP`), Jodhpur, Udaipur, Kota, Ajmer, Bikaner, Alwar; Sanganer is a **taluka** of Jaipur.
- ~85% of beneficiaries have >=1 bank account (measure DISTINCT linked beneficiaries, not rows); orphan bank rows <= 8%. ~12% of matched pairs have a planted **income mismatch**; ~4% a status mismatch.
- **`full_name` must be a realistic two-token name (first + last)** drawn from separate first/last pools with skew, so the most common full name occurs at most ~0.05% of rows (<= ~500) and distinct full names >= ~85% of rows. The BASE beneficiary name must be clean (typos live only on the bank/pension side, as planted variants); no NULL/blank `full_name`. `bank.mobile` equals the beneficiary mobile for ~80% of linked rows (not 100%).
- **Names must have a long tail** (head names ~35% of rows, remainder rare/synthetic) so the fuzzy blocking key (first 3 chars, `SRSE_ANALYSIS_BLOCKING_PREFIX_LEN`) does not collapse into a few giant blocks. Reuse the idea in `docker/seed/seed.py` (`_long_tail_name_expr`, `_vary_name_expr`).
- Department dimension: 5 departments (`department_code`), on `beneficiary` only.
- Indexes on keys and `district_code`; run `ANALYZE` after load.
- Roles: seed as `srse`/owner; create read-only role `demo_ro` for the Presto catalog and for the app's saved Data Source.

## Fixed names other work relies on

| Thing | Value |
|---|---|
| Database / schema | `analytics_demo` / `demo` |
| Presto catalog | `demo_pg`, file `docker/demo/presto-catalog/demo_pg.properties` |
| Data source display name | `Demo Business DB` |
| Layer tag on registrations | `SILVER` for bank/pension/ration, `GOLD` for beneficiary |
| Source System label | `Rajasthan Welfare Demo` |
| Users | `superadmin` (unscoped), `jaipurofficer` (Jaipur subtree, MFA on), `sanganeruser` (Sanganer taluka), `jaipuradmin` (scoped admin). Passwords: see `docs/RUNNING_LOCALLY.md` |
| Scope dimensions | `Geography` (District -> Taluka -> Village), `Department` (flat). AND across dimensions, OR within |

## Ownership (no overlapping edits)

- **DEMO-01 (data agent)** owns `docker/demo/**` and `docs/demo/DATA_PROFILE.md`.
- **DEMO-02 (setup agent)** owns `scripts/demo/**`, `docs/demo/DEMO_SCRIPT.md` and
  `docs/demo/SCENARIO_RESULTS.md`.
- Neither edits backend/frontend source unless a real product defect is found. If so:
  do **not** patch around it silently — write it up in your hand-off with the failing
  request/SQL, and stop on that item.

## Milestones / hand-off

- **M1 (DEMO-01, first, quickly):** compose + minimal Presto + seed with `ROWS=20000`
  (all tables scaled proportionally). DEMO-02 starts testing against this.
- **M2 (DEMO-01):** full 1,000,000 load; `DATA_PROFILE.md` with row counts, overlap %,
  mismatch %, name-block-size distribution, and the measured time of the fuzzy query
  directly in Presto.
- **M3 (DEMO-02):** idempotent setup + scenario runner green on M1 data; re-run on M2.
- Both: report honestly — if a scenario fails or is slow, say so with numbers.
