# Brief AA-11 — Audit capture and query-shape recording

**Repo:** `analytics-advisor` · **Covers:** `PRODUCT_PLAN.md` 7.3.1, 7.3.6
**Follows:** AA-10

## Scope

Capture the settled A7 event set into the audit log, with the query recorded as
a **shape** and never as values.

**Out of scope, and why:** 7.3.2 (storage sizing and retention), 7.3.3
(tamper-evidence), 7.3.4 (viewer), 7.3.5 (export), 7.3.8 (scoped viewing). All
of those wait on **A9**, which is still open — see Part 5, which asks you to
make that answer cheap to apply rather than to pre-empt it.

## What already exists

AA-10 created a minimal `audit_event` — `id`, `occurred_at`, `actor_user_id`,
`target_user_id`, `action_type`, `detail` — with a single writer,
`AuditService.recordUserContactChange`. **Extend that**; do not start a parallel
table. It is missing the source IP, the tables and columns touched, the query
shape, and the outcome.

## Part 0 — The trap: never record a value

Decision A6: the log stores the query **shape** with placeholders and never the
bound values, so Aadhaar numbers and names never land in it.

`RecordMatchService` produces exactly what is needed — parameterised SQL with
`?` placeholders plus a separate ordered parameter list. **Record the SQL, drop
the list.**

**`renderQueryForDisplay` must never be used for audit.** It substitutes the
bound values back in for readability, which is right for the SQL preview and
catastrophic here — it would write every officer-supplied value into the log and
turn the audit table into the personal-data store A6 exists to prevent. That
method is one autocomplete away from the correct one, so write the test that
asserts a bound value never reaches a persisted row.

Assert it with a real value that would be unmistakable, an Aadhaar-shaped
12-digit string, and search the whole written row for it — not just the SQL
column.

## Part 1 — The event set (A7, settled)

| Action | When |
|---|---|
| `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGOUT`, `ACCOUNT_LOCKED` | authentication |
| `MFA_CHALLENGE_ISSUED`, `MFA_VERIFIED`, `MFA_FAILED` | second factor |
| `QUERY_EXECUTED` | match, multi-match, comparison summary |
| `QUERY_PREVIEWED` | `match.sql` — planned, not run |
| `QUERY_REFUSED` | fan-out ceiling, scope denial, validation refusal |
| `EXPORT` | **every CSV download** |
| `USER_CREATED`, `USER_UPDATED`, `USER_DEACTIVATED`, `PASSWORD_RESET`, `ROLE_GRANTED`, `SCOPE_GRANTED`, `CONTACT_CHANGED` | admin actions |
| `SCOPE_BINDING_CHANGED`, `SHARED_REFERENCE_SET`, `TABLE_REGISTERED`, `TABLE_UNREGISTERED` | admin actions that change who can see what |

**Do not log metadata browsing** — the registry cascade fires on every dropdown
and would outnumber everything above by an order of magnitude. This is a
decision, not an oversight; put a comment on the cascade saying so, or someone
will "fix" it.

**`EXPORT` is the one not to compromise on.** It is the moment data leaves the
system and the entry an investigation actually needs.

## Part 2 — Columns to add

```
source_ip            varchar(45)      -- IPv6-capable
outcome              varchar(16)      -- SUCCESS / FAILURE / REFUSED
target_tables        varchar(2048)    -- qualified names touched, comma-separated
query_shape          varchar(8192)    -- parameterised SQL, placeholders only
scope_summary        varchar(512)     -- the officer's effective scope at the time
```

`scope_summary` matters more than it looks: an entry saying an officer ran a
query is far less useful than one saying what they were entitled to see when
they ran it, especially after their assignments change.

Keep the existing columns. Liquibase, vendor-neutral, no JSONB (decision 0.1).

## Part 3 — Source IP, which is not `request.getRemoteAddr()`

Behind a reverse proxy, `getRemoteAddr()` is the proxy, and every entry records
the same useless address. But `X-Forwarded-For` is a **client-supplied header**
and trusting it blindly lets anyone forge their own audit trail.

- Add a configurable list of trusted proxy addresses. Honour `X-Forwarded-For`
  **only** when the immediate peer is in it; otherwise record `getRemoteAddr()`.
- Default the list to empty, so an unconfigured deployment records the direct
  peer and never a forged header.
- Take the **rightmost** untrusted address in the chain, not the leftmost — the
  leftmost is the one a client can write freely.

Say in `CONFIGURATION_GUIDE.md` that this must be set when deployed behind a
load balancer, or every entry will name the balancer.

## Part 4 — Writing

- **One row per event, written when the outcome is known.** Do not write a row
  and update it later: the log is append-only by intent, and 7.3.3 may make that
  structural.
- For a **streamed** match the outcome arrives long after the SQL is planned.
  Write `QUERY_EXECUTED` at plan time — the auditable fact is what was asked for
  — and a separate `QUERY_REFUSED` row if it is refused before execution.
- Capture at a **choke point**, not at each call site. AA-09 made the match
  planner the single place SQL is produced; do the same here, or paths will be
  missed exactly as they would have been for the scope filter.
- **Never log the OTP digits, a password, or a password hash.** AA-10 restricts
  digits to the log-only sender; the audit log must not become a second route.

## Part 4a — When the audit write fails (Q6, settled)

**Refuse for admin actions and exports. Proceed for queries.**

| Action group | Behaviour |
|---|---|
| Admin actions — user create/update/deactivate, password reset, role and scope grants, contact changes, scope bindings, registration | Write the audit row **in the same transaction as the mutation**. If the write fails the whole thing rolls back, so nothing happens that was not recorded. |
| **Export** | Write and commit the audit row **before the first byte is streamed**, and refuse the download if it fails. |
| Query execution and preview | Write; on failure **proceed** and raise an alarm. |

**The export case is the one to get right, because a stream cannot be rolled
back.** By the time rows are going out it is too late to refuse, and an
after-the-fact write that fails leaves data gone and unrecorded — the exact case
Q6 exists to prevent. So the row is committed first and the download only starts
once it is safely there.

**"Proceed" must not mean "proceed quietly."** A silent fallback is
indistinguishable from a working audit log, which is worse than no log because
it is trusted. On a failed query-audit write: log at ERROR with a distinctive
marker, and surface it in the health endpoint so a deployment can alert on it.
An operator must be able to answer "was the log complete over this period?"
without reading application logs line by line.

## Part 5 — Leave A9 cheap to answer

A9 (tamper-evidence) is open and may require a hash chain. Retrofitting one onto
a populated table means backfilling every row.

Add `prev_hash` and `row_hash` as **nullable** columns now, unused. If A9 comes
back as "ordinary table" they cost two unused columns; if it comes back as
"hash chain", enabling it is a code change rather than a data migration.

Do **not** implement chaining now. Do not add UPDATE or DELETE paths for
`audit_event` in any case — application-level append-only is worth having
whatever A9 says.

## Part 6 — Tests

- A bound value never reaches a persisted row — Part 0, with a 12-digit string.
- One row per event for each action type, with the right outcome.
- `X-Forwarded-For` honoured from a trusted peer, ignored from an untrusted one.
- Metadata browsing writes **nothing**.
- Export writes a row.
- A refused query writes `QUERY_REFUSED` and not `QUERY_EXECUTED`.
- No UPDATE or DELETE path exists for `audit_event` — a structural test in the
  spirit of `DecisionScenarioRoutesGuardTest`.

## Acceptance

- `mvn -f backend/pom.xml test` green on **JDK 17**. Report the count — **440**.
- `npm run build` / `npm run test` clean.
- Live against PostgreSQL: log in, run a match, export the CSV, change a user's
  contact, and show the resulting rows. Confirm the `query_shape` column holds
  `?` placeholders and the row contains no officer-supplied value anywhere.
- Report the rows-per-action counts you saw, and any action in Part 1 you could
  not wire.

## Do not

- Do not use `renderQueryForDisplay` for audit.
- Do not log metadata browsing.
- Do not add an update or delete path for audit rows.
- Do not implement the hash chain — only leave room for it.
