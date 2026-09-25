# Backup and restore — operational database

The operational store holds application configuration that is **not** in the
lakehouse: users, roles, scope assignments, lakehouse registrations, analysis
column metadata, saved queries, and the audit log (seventeen `public` tables at
AA-19). JDBC connection overrides and OTP gateway credentials live on a
**separate mounted volume** (`connection-overrides.properties`) — they are **not**
included in a database dump.

This document covers **PostgreSQL** (default local and many deployments) and
**DB2** (supported production target). Procedures below were exercised on
PostgreSQL 16 (2026-09-25): `pg_dump` → empty database → `pg_restore` →
`OperationalSchemaMatchesEntitiesTest` against the restored database.

---

## PostgreSQL

### Prerequisites

- Client tools matching the server major version when possible (`pg_dump`,
  `pg_restore`, `psql`).
- Credentials with `SELECT` on all application tables and permission to create a
  database for restore drills.
- Local compose maps host **`localhost:5433`** → container `5432`, database
  **`srse`**, user **`srse`**, password **`srse_local_pw`**.

### Backup (custom format, recommended)

Custom format (`-Fc`) compresses well and lets `pg_restore` run in parallel or
restore selected objects.

```bash
export PGPASSWORD=srse_local_pw
pg_dump -h localhost -p 5433 -U srse -Fc -f srse_operational_$(date +%Y%m%d).dump srse
```

Plain SQL is acceptable for small stores:

```bash
pg_dump -h localhost -p 5433 -U srse -f srse_operational_$(date +%Y%m%d).sql srse
```

### Restore into an empty database

1. Create a **new empty** database (never overwrite the live DB in place until
   you have verified the restore).

```bash
export PGPASSWORD=srse_local_pw
psql -h localhost -p 5433 -U srse -d postgres -c "CREATE DATABASE srse_restore OWNER srse;"
```

2. Restore from custom format:

```bash
pg_restore -h localhost -p 5433 -U srse -d srse_restore --no-owner --role=srse srse_operational_YYYYMMDD.dump
```

3. Point the application at the restored database (example for a one-off local
   run):

```bash
export SRSE_OPERATIONAL_JDBC_URL=jdbc:postgresql://localhost:5433/srse_restore
export SRSE_OPERATIONAL_USER=srse
export SRSE_OPERATIONAL_PASSWORD=srse_local_pw
export SRSE_OPERATIONAL_DRIVER=org.postgresql.Driver
# start backend as usual
```

4. Confirm Liquibase does not fight the snapshot: on first boot against a
   restored DB, Liquibase should report **no pending changesets** when the
   backup came from the **same application version**. If the app is **newer**,
   it may apply missing changesets forward (see [Migration state](#databasechangelog-and-application-version)).

**Verified locally:** after restore, `public` contained **17** base tables and
**22** rows in `databasechangelog`. One benign `pg_restore` warning appeared
(`transaction_timeout` — a PostgreSQL 16 session parameter not recognised by an
older client); all tables and data restored correctly.

### Cutover (production pattern)

- Stop traffic to the application (or run against a read-only copy).
- Take a final backup.
- Restore to the target instance or rename databases (`srse` → `srse_old`,
  `srse_restore` → `srse`) per your runbook.
- Restart the backend with unchanged JDBC URL if you replaced the database in
  place.
- Reconcile users, scopes, and audit expectations (see [Security consequences](#security-consequences-of-a-restore)).
- Record the restore event in the audit log manually if the app was down during
  cutover.

---

## DB2

Use your DBA’s standard backup tool (`BACKUP DATABASE`, storage snapshots, or
vendor backup appliances). The application schema is Liquibase-managed and
vendor-neutral; table names match PostgreSQL except where legacy DB2 column
names are mapped in JPA (`002-db2-legacy-column-names.yaml`).

### Logical export (small / drill)

For a **portable logical copy** suitable for dev drills (not a substitute for
enterprise DB2 backup policy):

```bash
db2 connect to SRSEDB user db2inst1 using '<password>'
db2 "EXPORT TO srse_operational.del OF DEL MODIFIED BY coldel0x2c nochardel \
  SELECT * FROM APP_USER"
# repeat per table, or use db2look + db2move for full schema+data
```

Most teams will prefer **`db2 backup database SRSEDB to /path`** and
**`db2 restore database SRSEDB from /path taken at … into SRSEDB`** on a
**separate** database or instance for verification.

### Restore drill (empty database)

1. Create an empty database (or restore backup image to a **new** database name).
2. If restoring **only data** into an empty schema created by Liquibase on a
   fresh install, ensure the backup’s `DATABASECHANGELOG` matches or plan a
   forward migration (see below).
3. Point `SRSE_OPERATIONAL_JDBC_URL` (or `SRSE_DB2_URL`) at the restored
   database and start the application on the **same or older** build as the
   backup, then upgrade forward if needed.

DB2 restore was **not re-run** in the AA-19 exercise (local default is
PostgreSQL); adapt hostname, port `50000`, and paths to your environment using
the same cutover and security checks as PostgreSQL.

---

## What is not in the database backup

| Asset | Location |
|--------|-----------|
| Operational / analytical JDBC passwords | `connection-overrides.properties` on the mounted config volume |
| OTP SMTP and SMS gateway secrets | Same file (`srse.otp.smtp.*`, `srse.otp.sms.*`) |
| Presto truststores, `DATA_MODE`, guardrail env vars | Deployment environment / secrets store |

Back up the config volume (or secret store entries) on the same schedule as the
database, and restore them **after** the database cutover when rebuilding an
environment.

---

## Security consequences of a restore

### 1. Audit log rollback

The audit log is **append-only by design**. Restoring an earlier snapshot
**silently removes every audit entry written after that moment**. Compliance
and incident response depend on completeness; treat a restore as creating an
**intentional gap**. After cutover, **record the restore itself** (timestamp,
operator, backup identifier, approximate lost window) — in the audit log once the
app is up, or in an external change record if the app was stopped.

### 2. Resurrected access

A restore reverts **all** identity and authorization state to the backup time:

- Deactivated users become **active** again.
- Revoked scope grants **return**.
- Password changes after the backup **revert** (possibly to a compromised or
  rotated-out password).

After any restore, a SuperAdmin must **re-check** user `active` flags, roles,
scope assignments, and force password resets where policy requires it.

### 3. `DATABASECHANGELOG` and application version

Liquibase’s `DATABASECHANGELOG` (and lock table) travel with the data. Restoring
a backup from an **older** application version restores that migration history;
starting a **newer** binary may apply missing changesets automatically, or fail
validation if scripts changed incompatibly.

**Safe pattern:**

- Restore into the **same** application version that took the backup, **or**
  an **older** version, then **upgrade forward** through normal releases.
- Avoid pointing a **newer** app at a **partially migrated** or **newer-than-app**
  changelog without a DBA/Liquibase review.

---

## Checklist after restore

- [ ] Application starts; Liquibase status acceptable for your version pairing.
- [ ] SuperAdmin login works; spot-check officer scope on Analysis.
- [ ] User active/deactivated state matches current policy.
- [ ] Scope assignments and saved-query grants reviewed.
- [ ] `connection-overrides.properties` restored from volume/secret backup.
- [ ] Restore event documented; audit gap window understood.
- [ ] OTP gateways tested (SMTP panel test message) if email MFA is required.

---

## Related

- Connection and volume layout: `docs/CONFIGURATION_GUIDE.md`
- Admin config export (passwords and OTP gateways **excluded**): AA-04 bundle on
  the admin page — re-enter JDBC and gateway secrets after redeploy if the volume
  was lost.
