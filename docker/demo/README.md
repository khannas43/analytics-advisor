# Demo stack (DEMO-01)

PostgreSQL business data in database **`analytics_demo`** on the **existing** root
`postgres` service (host **5433**). Minimal **PrestoDB 0.297** federates that database
via catalog **`demo_pg`** only (no Iceberg/Hive/MinIO).

## Prerequisites

- Root stack Postgres running: `docker compose up -d postgres` from repo root.
- **Minimum RAM:** 16 GB for M2 (1M beneficiary × ~700k bank fuzzy benchmark); Presto JVM is **6G** (`docker/demo/presto/jvm.config`).
- `psql` and `docker` on PATH.

## Quick start (M1 — 20k rows)

```bash
docker compose -f docker/demo/docker-compose.yml up -d
./docker/demo/init-db.sh
ROWS=20000 ./docker/demo/seed.sh
./docker/demo/smoke.sh
```

## Full demo load (M2 — 1M rows)

```bash
./docker/demo/init-db.sh --reset   # optional clean slate; never touches srse
ROWS=1000000 ./docker/demo/seed.sh
./docker/demo/profile.sh   # writes docs/demo/DATA_PROFILE.md (blocking prefix 3 unless overridden)
```

## Connection strings

| Use | URL |
|-----|-----|
| App / Admin JDBC (direct Postgres) | `jdbc:postgresql://localhost:5433/analytics_demo` |
| Presto (federation) | `jdbc:presto://localhost:8083` (catalog `demo_pg`, schema `demo`) |
| Presto CLI example | `presto --server localhost:8083 --catalog demo_pg --schema demo` |

Postgres from **inside** the demo Presto container uses `host.docker.internal:5433`
(see `presto-catalog/demo_pg.properties`).

## Credentials

| Role | User | Password file |
|------|------|----------------|
| Seed / owner | `srse` | Root compose default password `srse_local_pw` (override via `PGPASSWORD`) |
| Read-only (Presto + app data source) | `demo_ro` | `docker/demo/secrets/demo_ro.password` (created by `init-db.sh`) |

## Ports

| Service | Host port |
|---------|-----------|
| Demo Presto | **8083** (root product Presto stays on 8081; verify stack uses 8082) |

## Milestones (recorded)

| Milestone | UTC timestamp | Notes |
|-----------|---------------|-------|
| M1 seed `ROWS=20000` | 2026-10-16T09:27:12Z | ~0.5s seed; smoke Presto count 20000 |
| M2 seed `ROWS=1000000` | 2026-10-16 (rev.) | ~15s seed, 900k bank; see `docs/demo/DATA_PROFILE.md` |

## Verify `srse` untouched

```bash
./docker/demo/verify-srse-unchanged.sh
```
