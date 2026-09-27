# Running Analytics Advisor locally

Two ways, depending on what you want to look at.

## A. The whole stack — quickest look

```bash
cd analytics-advisor
docker compose up --build
```

Frontend at **http://localhost:3000**, backend on 8080.

This runs `auth-mode=mock`, which is the inherited development seam: there is no
login screen. Mock JWTs identify **`mock-officer`** (`STATE_OFFICER` only) or
**`mock-admin`** (`?role=admin` → `SRSE_ADMIN` + `STATE_OFFICER`). On startup the
backend ensures matching **`app_user`** rows exist so **audited admin mutations**
(registration POST, scope binding writes, etc.) resolve a real actor. **Row-level
scoping is still not exercised in mock mode** — use local auth (§B) for that.

Good for: the Analysis tab, the admin page, connections, registrations, column
metadata, scope-binding configuration, exports.

## B. Local auth — everything built in §7

This is the one to use if you want to see login, users, roles, the hierarchy and
row-level scoping actually working.

```bash
# database + lakehouse only
docker compose up -d postgres presto metastore minio seed

# backend, with real accounts switched on
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -q -DskipTests package
JAVA_HOME=$(/usr/libexec/java_home -v 17) java -jar target/srse-backend-0.1.0.jar \
  --server.port=8080 \
  --spring.profiles.active=local \
  --srse.auth-mode=local \
  --srse.datasource.operational.jdbc-url=jdbc:postgresql://localhost:5433/srse \
  --srse.datasource.operational.username=srse \
  --srse.datasource.operational.password=srse_local_pw \
  --srse.datasource.operational.driver-class-name=org.postgresql.Driver \
  --srse.presto.url=jdbc:presto://localhost:8081 \
  --srse.presto.user=srse \
  --srse.bootstrap.super-admin-password='ChooseSomething1!'

# frontend, pointed at local auth
cd ../frontend
NEXT_PUBLIC_AUTH_MODE=local NEXT_PUBLIC_API_BASE=http://localhost:8080 npm run dev
```

If the frontend dev server uses a port other than 3000, add that origin on the backend
(CORS is deny-by-default):

```bash
SRSE_FRONTEND_ORIGINS=http://localhost:3000,http://localhost:3001 \
  SRSE_AUTH_MODE=local docker compose up -d srse-backend
```

For Docker, `NEXT_PUBLIC_*` values are baked at **image build** time (see `frontend/Dockerfile`).
Rebuild the frontend when switching auth mode:

```bash
NEXT_PUBLIC_AUTH_MODE=local docker compose build srse-frontend
SRSE_AUTH_MODE=local docker compose up -d srse-backend srse-frontend
```

**JDK 17 is required** — the build enforcer rejects anything else. There is no
root pom, so `mvn -pl backend` does not work; use `-f backend/pom.xml` or run
from inside `backend/`.

The bootstrap password is only used on the **first** start against an empty
`app_user` table. On a database that already has users it is ignored, and if the
table is empty and you supply nothing the backend refuses to start rather than
coming up unreachable.

## What is already in the dev database

The PostgreSQL volume already carries fixtures from verification runs:

| Account | Password | What it shows |
|---|---|---|
| `superadmin` | `Restored$Sup1` | Unscoped — sees all 7 districts, 200,000 rows |
| `jaipurofficer` | `JaiOffic$1x` | Scoped to Jaipur — only Jaipur district rows (strict subset of the unscoped population). **MFA is enabled on this account**, so login returns a challenge and you read the six-digit code from the backend log (`LoggingOtpSender`). Turn MFA off from the admin user screen if it is in the way. |
| `jaipuradmin` | `JaipurNew$1` | A **scoped admin**: manages only users inside Jaipur |
| `sanganeruser` | `Officer$New1` | Taluka scope, for the deny-by-default case |

Plus a Geography dimension with District and Taluka levels, and
`iceberg.srse.beneficiary` registered and bound at District to the `district`
column.

Run the backend with `--srse.otp.sender=log` to see MFA without a mail or SMS
gateway. It is the default locally and the only place the digits are ever
written.

**The clearest single demonstration:** log in as `jaipurofficer`, run a match on
`beneficiary` keyed on `id` with `district` as a display column, and note the row
count. Then do exactly the same as `superadmin`. The scoped run must return
**fewer rows than the unscoped run** (same request shape; only permitted districts
in SQL), not a fixed seed count.

## Worth knowing

- A scope binding matches the officer's node **code** against the column's
  values. They must agree exactly — a node coded `JAIPUR` against data holding
  `Jaipur` matches nothing, and nothing currently warns you.
- `sanganeruser` holds a **taluka**, and `beneficiary` is bound only at
  **district**, so that table is correctly invisible to them: the rows carry no
  taluka value, so no filter could be exact. That is deny-by-default working,
  not a fault.

## Before deploying: prove the app boots

`OperationalSchemaMatchesEntitiesTest` starts the real Spring context against the
local operational database with `ddl-auto: validate`, so a Liquibase changeset
that disagrees with a JPA entity fails the build rather than the deploy. It runs
in the ordinary suite and takes about four seconds.

It **skips** when no database is listening on 5433, printing why. So the one
command that matters before a deploy is:

```bash
docker compose up -d postgres
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f backend/pom.xml test
```

A green suite with that database up means the schema and the entities agree. A
green suite **without** it does not — the check silently skipped.

### On the `*IT` tests

`OperationalStoreLiquibaseIT`, `OperationalStoreLiquibaseDb2IT` and
`AnalysisEmittedSqlPrestoValidateIT` never run under `mvn test`. Surefire's
default includes are `*Test`, `Test*`, `*Tests` and `*TestCase`; nothing named
`*IT` matches, so their environment-variable guards are never even consulted.
They run only when named explicitly with `-Dtest=`, and the Testcontainers ones
additionally need a Docker socket the JVM can reach, which Docker Desktop on
macOS does not always provide.

Treat them as tools to reach for deliberately, not as coverage.
