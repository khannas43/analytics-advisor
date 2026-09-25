# Brief AA-06 — Users, roles and the organisation hierarchy (backend)

**Repo:** `analytics-advisor` · **Covers:** `PRODUCT_PLAN.md` 7.1.1–7.1.4, 7.1b, 7.1c.1–7.1c.2
**Follows:** AA-05 (`1dd7f69`)

## Scope

The data model and local authentication. **Backend only.**

**Out of scope, deliberately:** admin screens (7.1.5 — a later brief), scoped
admins (7.1.6), MFA (7.1a — needs the gateways), audit archival (7.1c.3–4 —
needs the audit log), and **the scope predicate itself (7.2)**. This brief
builds the model that 7.2 will read. It must not inject a filter into a single
query.

Read `PRODUCT_PLAN.md` §0 and §7 first. Every decision below is settled there;
do not re-open one, and flag it if something here contradicts it.

## Ground rules

- **Vendor-neutral schema** (decision 0.1). Liquibase changesets under
  `db/changelog/changes/`, Liquibase types only, no PostgreSQL-only or DB2-only
  DDL, **no JSONB**. It must run on both. AA-05 set this up; follow it.
- **Hibernate `validate` is on.** An entity that drifts from the changelog fails
  at boot. That is the point — do not switch it off to make something pass.
- **Single tenant** (0.4). No owner or tenant column on anything.
- Do not touch `analysis/`, `compiler/` or `lakehouse/`.

## Part 1 — The hierarchy, as N orthogonal dimensions

Decision 0.7: geography and department are **separate axes**, each its own tree.
The model must not hardcode either, and must not assume two.

```
scope_dimension   id, code (GEOGRAPHY, DEPARTMENT), name, display_order
scope_level       id, dimension_id, depth, name          -- District=1, Taluka=2, Village=3
scope_node        id, dimension_id, level_id, parent_id, code, name, path
```

- **`path` is a materialised path** (e.g. `/RJ/JAIPUR/SANGANER/`). Decision A3
  makes a scope a *subtree* — a district officer sees the district and
  everything under it — so "descendants of X" must be a prefix match, not a
  recursive walk. Index it.
- **Levels are data, not code** (decision 0.1/A1). Nothing may hardcode
  "District" or assume three levels. A deployment defines its own.
- **Every dimension has exactly one root node.** This matters, see Part 3.

## Part 2 — Users, roles, assignments

```
app_user               id, username, password_hash, email, mobile,
                       active, mfa_required, must_change_password,
                       failed_login_count, locked_until,
                       created_at, updated_at, last_login_at
app_role               id, code (SUPER_ADMIN, ADMIN, OFFICER), name
user_role              user_id, role_id
user_scope_assignment  user_id, scope_node_id
```

- **Login is by `username`, not email.** Email and mobile are *contact channels*
  for the OTP in 7.1a; a user changing their email must not change how they log
  in. Keep them separate.
- **`user_scope_assignment` is many-to-many and spans dimensions.** One user may
  hold "Jaipur District" and "Health Department" simultaneously (decision A2).
  Do not add a dimension column to `app_user` — that is the single-axis model
  0.7 rejects.
- **`app_user` rows are never hard-deleted** (7.1c). No delete endpoint, no
  cascade that could remove one. The audit log will reference them and must keep
  a real author forever. Add the `active` flag and a deactivate operation only.
- `mfa_required` ships now, unused. 7.1a switches it on; having the column from
  the start avoids a migration against a populated table.

## Part 3 — The assignment semantics, stated once and written down

7.2 will build the predicate. It needs these rules fixed now, and they belong in
a Javadoc on the assignment service, not only in this brief.

- **AND across dimensions, OR within one.** A user assigned Jaipur + Alwar
  (geography) and Health (department) sees
  `(Jaipur OR Alwar) AND (Health)`.
- **An assignment covers the node and all its descendants** (A3), via the `path`
  prefix.
- **No assignment in a dimension means NO access in that dimension, not "all".**
  Whole-dimension access is granted by assigning the dimension's **root node**,
  which is why Part 1 requires one.

That last rule is the load-bearing one and it is a deliberate trade. The
alternative — treating an absent assignment as unrestricted — means the day
someone adds a third dimension, every existing user silently gains full access
to it. With this rule they instead lose access until an admin grants it. **A
new dimension locking people out is a visible, fixable problem; a new dimension
silently widening everyone's reach is not.** Same instinct as A4's
deny-by-default.

Write a test for each of these four rules now, against the assignment service,
even though nothing consumes them yet. They are the specification 7.2 builds to.

## Part 4 — Local authentication

Decision 0.5: in-house, no directory, no SSO. The existing `AuthMode` seam
(`mock` / `rajsewadwar`) gains **`local`**.

- **`POST /api/auth/login`** — username + password, returns a token. Keep the
  existing JWT shape and `MockJwtService`'s issuing approach so `SecurityConfig`
  and the frontend's `authToken.ts` need no structural change.
- **Keep `mock` working.** Local dev and every existing `@WebMvcTest` depend on
  `/api/auth/mock-login`. Do not remove it; add alongside.
- **BCrypt** via Spring Security's `BCryptPasswordEncoder` (strength 12).
  Already on the classpath, no new dependency. Never store or log a plaintext
  password, and never return a hash from any endpoint.
- **Lockout:** count failed attempts, lock for a configured period after a
  configured number. Both configurable, sensible defaults.
- **Do not leak whether a username exists.** Unknown user and wrong password
  must return the same message *and* do the same work — hash a dummy password on
  the unknown-user path so response timing does not distinguish them. An
  enumerable login on a government system is a finding.
- **A deactivated user cannot log in**, and deactivation invalidates existing
  sessions rather than letting them run to expiry.
- **`must_change_password`** forces a change before any other endpoint is
  usable.

## Part 5 — Password lifecycle (7.1.4)

- Admin sets an initial password; it lands with `must_change_password = true`.
- Self-service change: requires the current password.
- Configurable expiry; expired behaves like `must_change_password`.
- **Self-service *reset* — if you build it, it needs a delivery channel, and
  that is 7.1a's gateway work.** Do not invent a second one here. Until then,
  reset is an admin action. Say so in the report rather than building a stub.

## Part 6 — Bootstrap (7.1b)

- Create the first SuperAdmin on startup **only when `app_user` is empty**.
  Never on later boots — a redeploy must not resurrect or reset the account.
- Password comes from configuration at deploy time. **No default in code**,
  never logged, never in a startup banner.
- **Refuse to start** when the table is empty and no bootstrap password was
  supplied. Failing loudly beats coming up unreachable, and beats coming up open.
- `must_change_password = true` on that account.

**The trap, which has no way back.** Create it with **`mfa_required = false`**.
7.1a needs a verified mobile and email to deliver an OTP; a bootstrap SuperAdmin
with MFA on and no verified contacts cannot log in, and there is no second admin
to fix it. The admin verifies their own contacts and turns MFA on afterwards.

## Part 7 — Changelog and tests

- One changeset per table, or one coherent changeset for the set — either is
  fine, but it must apply cleanly to an **empty PostgreSQL** and to an existing
  DB2 that has AA-05's baseline.
- Extend `OperationalStoreLiquibaseIT` (Testcontainers PostgreSQL) to cover the
  new tables: changelog applies, `validate` passes, the repositories round-trip.
- **Do not point any test at a shared database.** AA-05's guard refuses
  `SRSEDB` by name after this test renamed columns under a running SRSE; use a
  dedicated database.
- Unit tests for: the four assignment rules in Part 3, password hashing and
  verification, lockout, the deactivated-user path, username enumeration
  returning identical responses, and bootstrap running once and only once.

## Acceptance

- `mvn -f backend/pom.xml test` green on **JDK 17** (no root pom; `-pl backend`
  fails). Report the count — it was **359**.
- Backend starts against an empty PostgreSQL, applies the changelog, passes
  `validate`, creates the bootstrap SuperAdmin.
- Restarting does **not** create a second one.
- Starting with an empty user table and no bootstrap password **fails to start**,
  with a message saying why.
- `POST /api/auth/login` issues a working token; the wrong password and an
  unknown username are indistinguishable in body and status.
- `grep -rn "District\|Taluka\|Village" backend/src/main/java` finds no
  hardcoded level names.
- `npm run build` unaffected — no frontend work in this brief.

## Report

Say explicitly which of Part 3's four rules you have tests for, and flag
anything in the schema you could not express vendor-neutrally.
