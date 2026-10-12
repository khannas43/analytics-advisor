# Analytics Advisor — build plan

Working plan. Numbered so items can be added, split or struck. Estimates are
developer-days unless stated; **R** = reused from SRSE, **N** = new build,
**R+** = reused but needs extending.

Target UX: `docs/Analytics_Advisor.html` (a mockup — hardcoded data, browser-only
logic, six rows per table). Treat it as a picture of the destination, not a spec.

---

## Status at 2026-10-12

Verified against the code on 2026-10-12. Since the 2026-09-25 status below, also
built: AA-18 exports and saved queries, AA-19 gateway panels and backup/restore,
AA-20 prototype layout (sidebar, theme toggle, i18n mechanism with an English and
a Hindi catalog), and external-database onboarding with an execution router
(Presto / direct JDBC / federation). **New work is in §10.** Scheduled runs (6.8)
are **deferred by decision**.

Verified **not** built: self-service password reset (`OtpPurpose` has no reset
value), the 30-minute idle timeout (`TOKEN_TTL` is still a hardcoded 8 hours in
`SessionTokenService`), `ANALYZE` scheduling, a per-catalog health panel, and
active archiving of a deactivated user's audit entries (today it is only a
read-time exclusion).

## Status at 2026-09-25

**Built and verified against a running stack.** §1 fork cleanup (bar the package
rename), §3 labels and the Database Overview, §4 Extract Records, §5 Report
Analysis including Group By, §7 in full — users, scoping and audit — and
everything inherited from SRSE that survived the fork.

Every product and audit decision is settled. `docs/OPEN_DECISIONS.md` and
`docs/OPEN_DECISIONS_AUDIT.md` hold the reasoning.

**Deferred by decision, not blocked:**

- **7.1a.7 SMS sender** — the seam and a loud placeholder exist; email carries
  the OTP meanwhile.
- **7.1d.3 idle timeout** — needs a choice between short-lived tokens with
  refresh and server-side activity tracking. Not a config value.

**Remaining work, all of it unblocked:** §2.5 metadata snapshot (Aa1, settled
2026-10-12), §6.4–6.8 exports and saved queries, §8 the UX shell (8.3
English/Hindi is the largest single item left at 10 days), §9.3 performance
testing at realistic volumes, §9.5 backup and restore, and §2.3 source-system
operations.

Legend in the tables below: ✅ done · ◐ partly done · ⏸ waiting on something else.

---

## 0. Decisions — ANSWERED 2026-09-25

Full framing and the reasoning behind each in `docs/OPEN_DECISIONS.md`.

| # | Decision | Answer |
|---|---|---|
| 0.1 | Operational database | **Not mandated — the product must run on either.** No client requirement for DB2, so PostgreSQL becomes the default and reference database, and the schema stays vendor-neutral so DB2 remains a supported target. See 1.7. |
| 0.2 | Query engine | **PrestoDB 0.297 only.** Revisit if a non-watsonx.data deployment appears; the SQL emitter is centralised, so a dialect seam costs the same later as now. |
| 0.3 | "Source System" | **A display label on registered tables** — exactly how `layer` already works. Not a catalog, and not an entity owning its own connection. One lakehouse connection reaching many catalogs. |
| 0.4 | Tenancy | **Single tenant.** Separation between departments is a scope dimension (0.7), not a tenant boundary. No owner column on any table. |
| 0.5 | Users | **Built in-house from scratch.** No existing directory, no SSO at this stage, access limited to a small number of users. Local accounts with password management. Keep the existing `AuthMode` seam so SSO can be added without touching `SecurityConfig`. |
| 0.6 | Row limits | **Keep the inherited limits** — 10,000 rendered, 200,000 streamed, complete CSV beyond. Measured at crore scale rather than guessed. |
| 0.7 | Scope dimensions (was A16) | **Department is a SECOND AXIS, orthogonal to geography.** A deployment defines N dimensions, each its own tree; a user holds assignments in each; the predicate is AND across dimensions, OR within one. |
| 0.8 | External databases | **A Super Admin connects a business database; that connection is not the application's own database.** Same-database questions run on the saved read-only JDBC connection. A question that mixes sources runs on Presto only when each involved database has a Presto catalog alias saved in Admin. Without that alias the question is refused. See §2.4. **Supported types: see 0.13** (this row used to list six). |
| 0.13 | Supported database matrix (2026-10-12) | **PostgreSQL, DB2 and the Lakehouse (PrestoDB) are the supported matrix.** Every verification, test and document covers those three only. The MySQL, MariaDB, SQL Server and Oracle entries stay in `ExternalDataSourceType` as **dormant code**, and are **hidden from the Add Connection UI** (the API must also refuse them, not only the dropdown). Revisit when a client names a fourth database. The database choice is still open at product level, so nothing may imply the dormant four work. |
| 0.14 | Scheduled runs (6.8) | **Deferred** (2026-10-12). The scope question below stays recorded. |

### Product extension — metadata snapshot & performance (Aa1) — settled 2026-10-12

Stakeholder note `docs/Aa1.pdf`. Full build spec: `docs/briefs/AA-21-metadata-snapshot-and-monitoring.md`.

| # | Decision |
|---|---|
| 0.9 | **Authoritative store:** Liquibase snapshot tables in the operational DB (vendor-neutral, 0.1). Optional **JSON export** is generated from those tables for downstream consumers; **never read back** as source of truth. |
| 0.10 | **Staleness:** snapshot is **display-only** for QB column names/types (7-day TTL). Operational overrides and scope apply immediately. **Live `validateColumn` on every run.** No snapshot or past TTL → live read (no hard sync gate). Sync on registration + Super Admin “Sync now”. UI: “Metadata as of …” and failed-sync badge. **Scope/deny checks before cache read.** |
| 0.11 | **Distinct values:** always **live, scoped, not cached** initially. **`value_picker_enabled`** per column (default off); backend enforces. Admin **suggestions** from `approx_distinct`/count only — never auto-enable. Future distinct cache only on shared-reference tables if ever added. |
| 0.12 | **Monitoring:** **AA-initiated queries only** (in-memory registry per backend instance; document single-instance limit). Super Admin only; shape/tables, never bound values (A6). Cluster-wide Presto UI stays with ops. **History:** add **duration** to existing audit rows, not a second store. |

### Answered since

| # | Question | Impact |
|---|---|---|
| 0.5c | **ANSWERED: SMS *and* email** — one code, both paths, one window. See §7.1a. The SMS gateway is an external dependency; start procurement early. |

---

## 1. Foundation (carried from the fork)

| # | Activity | Type | Est |
|---|---|---|---|
| 1.1 | ✅ (AA-01) Restore RBAC + controller tests removed at fork (6 slices). **Highest priority — live behaviour is currently untested.** | N | 2 |
| 1.2 | ✅ (AA-04) Restore admin config backup, trimmed to connections + registrations + column metadata | N | 2 |
| 1.3 | ✅ (AA-03) Decide and act on the inherited age filter (drop, or back with a real field concept) | N | 1 |
| 1.4 | ✅ Rewrite `CLAUDE.md` for this product | N | 1 |
| 1.5 | ✅ (AA-02) Strip SRSE screens from the frontend (Rule Engine page, scheme panels, field-mapping editor) | N | 2 |
| 1.6 | Rename package root once both repos stop trading fixes | N | 1 |
| 1.7 | ~~**Make the operational store portable**~~ Done (AA-05): Liquibase baseline, PostgreSQL default, `ddl-auto: validate`, DB2 still supported. | Y | 3 |

---

## 2. Connections — the two planes

The product talks to **two separate systems**, and conflating them is the most
expensive mistake available. SRSE already separates them and that separation
carries over intact.

### 2.1 Operational database — the product's own store

Holds what the product knows, not what it analyses: registered tables, column
metadata (business names, fuzzy flags, comparison mode), saved queries, users,
roles, audit. Small, transactional, ORM-shaped (JPA).

| # | Activity | Type | Est |
|---|---|---|---|
| 2.1.1 | Confirm engine (0.1) and migrate if moving off DB2 | N | 3 |
| 2.1.2 | Connection config via environment variables + pooled datasource | R | 0 |
| 2.1.3 | Admin screen: view connection, test, edit, live-swap without restart | R | 0 |
| 2.1.4 | Credentials never returned to the browser; write-only password fields | R | 0 |
| 2.1.5 | Schema migrations discipline (numbered SQL, documented "why not automatic") | R | 0 |
| 2.1.6 | New tables: saved queries, users/roles, audit log | N | 3 |

### 2.2 Analytical lakehouse — the data being queried

One connection, many catalogs. Every query is pushed down; rows are never pulled
into the app tier to be processed.

| # | Activity | Type | Est |
|---|---|---|---|
| 2.2.1 | PrestoDB JDBC connection, **catalog-agnostic URL** (no catalog pinned in the URL) | R | 0 |
| 2.2.2 | SSL / truststore support for secured endpoints | R | 0 |
| 2.2.3 | Admin screen: view, test, edit, live-swap the analytical connection | R | 0 |
| 2.2.4 | Browse the live cluster: catalogs → schemas → tables → columns with types | R | 0 |
| 2.2.5 | Registry: admin registers the subset users may query; registration is per table, columns re-read live | R | 0 |
| 2.2.6 | Two gates on every identifier: registered **and** live-existing **and** not hidden | R | 0 |
| 2.2.7 | Identifier grammar validation (catalog/schema names are interpolated, never bound — Presto has no placeholder for them) | R | 0 |
| 2.2.8 | Trino support, if 0.2 says yes | N | 4 |
| 2.2.9 | `ANALYZE` scheduling so the fan-out guard reads catalog statistics instead of computing them | N | 2 |

### 2.5 Registered-table metadata snapshot (Aa1 — decisions 0.9–0.10)

Sync **physical column names and types** for **registered tables only** (plus
`jdbc_*` registrations via the external metadata path). Reduces Presto
`information_schema` / `DESCRIBE` traffic on every QB cascade open.

| # | Activity | Type | Est |
|---|---|---|---|
| 2.5.1 | Liquibase: snapshot header + column rows (plain types); sync run log (success/failure per table) | **Y** (changelog `015`) | 2 |
| 2.5.2 | `MetadataSyncService`: sync all registered tables; on table registration; cron default weekly | **Y** | 3 |
| 2.5.3 | `LakehouseRegistryService.listColumnsForDisplay`: scope + registration first; then snapshot if fresh else live; execution paths stay on `listColumnsLive` | **Y** | 3 |
| 2.5.4 | Super Admin: “Sync now”, last run status, optional JSON manifest download | **Y** (Table Registry admin section) | 2 |
| 2.5.5 | QB UI: “Metadata as of …”, warning if last sync failed | **Y** | 1 |

**Known limits (2.5):** Database Overview column expand stays **live** (`listColumnsLive`). Manifest and officer status expose no credentials; stale snapshot columns may still appear in pickers until TTL or re-sync — `validateColumn` / match still gate on live. Overlapping sync runs on multiple backend instances skip when another run is `RUNNING` and younger than 120 minutes (single-instance default today).

### 2.3 How source systems connect

**Each source system is a Presto catalog**, configured on the Presto server, not
in this application. A CRM on MySQL and an ERP on Oracle become two catalogs;
one JDBC connection reaches both, and cross-system joins work natively.

| # | Activity | Type | Est |
|---|---|---|---|
| 2.3.1 | Document the catalog-per-source-system model and what ops must configure | N | 1 |
| 2.3.2 | Connection health panel — per catalog reachable/unreachable | N | 2 |
| 2.3.3 | Decide behaviour when a catalog disappears (registered tables pointing at it) | N | 1 |

### 2.4 Connected business databases

This is separate from both planes above.

- The **application database** is the operational PostgreSQL (or DB2) store. It holds users, scopes, saved queries, audit events, and the list of registered tables. Officers never query it from Query Builder.
- A **connected business database** is a read-only JDBC connection a Super Admin adds under Admin → Data Sources. Its password is encrypted with `SRSE_EXTERNAL_SOURCE_SECRET_KEY` and is never sent back to the browser.
- **Direct JDBC** answers a question whose tables all come from one connected database.
- **Presto federation** answers a question that mixes the lakehouse with a connected database, or mixes two connected databases. It runs only when a Presto catalog alias was saved for each connected database in the question. Configuring that catalog is an operations task on the Presto server. Saving the alias in Admin does not create the catalog.

Workflow, in order:

1. Super Admin → Data Sources → Add connection. Test runs before the connection is saved.
2. Browse catalogs, schemas, tables, and columns.
3. Register the tables officers should be able to use. Each registration gets a stable logical address `jdbc_<id>.schema.table`. The connection's display name is not that address and does not go into the query.
4. Grant access on purpose. A new registration is not shared. A Super Admin can see it. A scoped officer cannot until either the table is bound to their scope in Table Registry, or a Super Admin marks it as a shared reference. Do not mark it shared merely because it was registered.
5. Officers pick the logical table in Query Builder and run, page, sort, and export as they do for lakehouse tables. Removing a connection is blocked while any of its tables stay registered.

Fuzzy matching, folded name groups, and any other Presto-only function still need the catalog alias even when every table is on one connected database. MySQL and MariaDB tables that have no schema are registered under the database name, and that name must be a plain identifier (letters, digits, underscores).

---

## 3. Metadata & hierarchy

### 3.1 The hierarchy question (decision 0.3)

The prototype shows **Source System → Layer → Table Group → Table**. The physical
address is **Catalog → Schema → Table**. These are not the same thing, and the
physical address must stay authoritative — it is what goes into SQL.

Proposal: keep `catalog.schema.table` as the address, and treat Source System,
Layer and Table Group as **labels on a registered table**, stored in the
operational database and used only for browsing and filtering. Layer already
works exactly this way in SRSE.

| # | Activity | Type | Est |
|---|---|---|---|
| 3.1.1 | ✅ (AA-15) Extend registration with source-system and table-group labels | R+ | 2 |
| 3.1.2 | ✅ (AA-15) Admin UI to manage the label vocabulary | N | 2 |
| 3.1.3 | ✅ (AA-15) Browse hierarchy driven by labels, addresses unchanged underneath | R+ | 2 |
| 3.1.4 | ✅ (AA-15) Database Overview page — drill down to attributes and data types | N | 3 |
| 3.1.5 | ✅ Column metadata: business name, hide, fuzzy flag, comparison mode | R | 0 |
| 3.1.6 | ◐ (Aa1 / 0.11) `value_picker_enabled` on column metadata; admin UI + sync job cardinality **suggestions** (never auto-enable). *Flag, gate and UI done 2026-10-12: `analysis_column_metadata.value_picker_enabled` (changelog 014, default false); `column-values` is refused with a 400 ("Type the value instead…") for any column without it, after the registered/live/not-hidden check and before any scope or SQL work; the admin column editor has a "Value list" checkbox; the Extract rule picker and the Report value-filter picker hide for disabled columns (typed values still work in rules; the Report filter shows a note, en + hi); an admin PUT or a config restore that omits the field keeps the stored value, and the config bundle now carries it. **Behaviour change to announce:** every picker is OFF until an admin enables it per column, including on existing deployments. **Not done:** cardinality suggestions (needs the AA-21 snapshot/sync job); the Report value filter has no typed-value alternative, so a column without a list cannot be filtered there.* | N | 2 |

---

## 4. Query Builder — Extract Records

| # | Activity | Type | Est |
|---|---|---|---|
| 4.1 | ✅ (AA-13) Source / destination table pickers through the hierarchy | R+ | 1 |
| 4.2 | ✅ (AA-13) Multi-select attributes per side | R+ | 2 |
| 4.3 | ✅ Primary key selection (this is the join key) | R | 0 |
| 4.4 | ✅ (AA-13) Single-source mode (one table, no join) | N | 2 |
| 4.5 | ✅ (AA-13) **Rules on source and destination** — operators, AND-combined | R+ | 4 |
| 4.6 | ✅ Live SQL preview before execution | R | 0 |
| 4.7 | ✅ Run, stream results, progress | R | 0 |
| 4.8 | ✅ Fan-out guard — refuse an unrunnable query with an estimate, not a timeout | R | 0 |

---

## 5. Query Builder — Report Analysis

| # | Activity | Type | Est |
|---|---|---|---|
| 5.1 | ✅ (AA-16) Value filter — pick attribute, keep selected values (server-side) | N | 3 |
| 5.2 | ✅ Fuzzy matching, Levenshtein, threshold % | R | 0 |
| 5.3 | ✅ (AA-16) Fuzzy options: case-sensitive, ignore spaces | N | 1 |
| 5.4 | ✅ (AA-16) Fuzzy against typed text rather than a second column | N | 2 |
| 5.5 | ✅ (AA-14) **Group By + aggregators (SUM/COUNT/AVG/MIN/MAX)** — server-side SQL, interacts with guardrails | N | 8 |
| 5.6 | ✅ Column comparison with match/mismatch verdicts and no-counterpart handling | R | 0 |
| 5.7 | ✅ Show mismatches only | R | 0 |

---

## 6. Results & exports

| # | Activity | Type | Est |
|---|---|---|---|
| 6.1 | ✅ Results grid: sort, per-column filter, column visibility, paging | R | 0 |
| 6.2 | ✅ Large-result behaviour: stop rendering past a threshold, offer the complete file instead | R | 0 |
| 6.3 | ✅ CSV export — complete result, streamed, not a re-serialisation of the screen | R | 0 |
| 6.4 | ✅ (AA-18) Excel export | N | 3 |
| 6.5 | ✅ (AA-18) JSON export | N | 1 |
| 6.6 | ✅ (AA-18) XML export | N | 1 |
| 6.7 | ✅ (AA-18) Saved queries — name, reopen, share | N | 5 |
| 6.8 | ⏸ **DEFERRED by decision (0.14).** Scheduled runs / delivery. When reopened, settle first: whose scope does a scheduled run execute under? The author's is a standing scope leak; the recipient's means the same schedule yields different data per viewer. Working lean: the recipient's scope, evaluated at delivery. | N | ? |

---

## 7. User management, data scoping and audit

### 7.1 Users and the organisation hierarchy

Built in-house — no Keycloak. That means we own password storage, reset, lockout,
sessions and everything else an identity product would have given us.

| # | Activity | Type | Est |
|---|---|---|---|
| 7.1.1 | ✅ (AA-06) Org hierarchy: levels (State → District → Taluka → …) and nodes with parents | N | 4 |
| 7.1.2 | ✅ (AA-06) Users, roles, and assignment to one or more hierarchy nodes | N | 5 |
| 7.1.3 | ✅ (AA-06) Local authentication: password hashing, login, logout, session expiry | N | 4 |
| 7.1.4 | ◐ (AA-06) Password lifecycle — admin-set initial, expiry and lockout done; **self-service reset waits on 7.1a's delivery channel** | N | 5 |
| 7.1.5 | ✅ (AA-07) SuperAdmin / Admin screens to create users, assign roles and scopes, deactivate | N | 6 |
| 7.1.6 | **ANSWERED: yes — an Admin is scoped, a SuperAdmin is not.** An Admin manages only users within their own subtree and may grant only scopes they themselves hold. See AA-07 Part 0. | N | 3 |

**Assignment semantics — settled, and the specification §7.2 builds to.**

1. **AND across dimensions, OR within one.** Jaipur + Alwar (geography) and
   Health (department) resolves to `(Jaipur OR Alwar) AND (Health)`.
2. **An assignment covers its node and every descendant** (A3), matched by the
   `scope_node.path` prefix rather than a recursive walk.
3. **No assignment in a dimension means NO access in that dimension** — never
   "all". Whole-dimension access is granted by assigning that dimension's root
   node, so every dimension has exactly one.
4. Levels are data; nothing hardcodes a level name or assumes a depth (A1).

Rule 3 is the load-bearing one and it is a deliberate trade, confirmed
2026-09-25. Treating an absent assignment as unrestricted would mean that the
day a third dimension is added, every existing user silently gains the run of
it. This way they lose access until an admin grants it: a new dimension locking
people out is visible and fixable, one quietly widening everyone's reach is
not. Same instinct as A4's deny-by-default.

### 7.1a Multi-factor authentication (decision A12)

**Settled:** MFA is **required**, delivered as a **one-time password**, and
**an admin controls it per user** — some accounts require a second factor,
others do not. It is an attribute of the user record, not a global switch.

| # | Activity | Type | Est |
|---|---|---|---|
| 7.1a.1 | `user.mfa_required` flag, admin-editable from the user screen | N | 1 |
| 7.1a.2 | **Contact verification** — a mobile number and an email address are proved to work (a code sent to each) before MFA can be switched on for that account. There is no enrollment step as such: a delivered OTP has no shared secret to set up, so what has to be established is that both addresses reach the person. | N | 2 |
| 7.1a.3 | Verification step at login, between password and session issue | N | 2 |
| 7.1a.4 | **Recovery** — an admin corrects a user's mobile number or email and re-verifies it. This is the whole recovery path for a delivered OTP: a changed number or a dead mailbox locks the officer out, and only an admin can move them. Log every such change to the audit trail — editing where a second factor is delivered is exactly the move an attacker would make. | N | 1 |
| 7.1a.5 | Rate-limit and expire codes; lock after repeated failures | N | 1 |
| 7.1a.6 | SMTP sender | N | 1 |
| 7.1a.7 | ⏸ **Deferred by decision, not blocked.** The placeholder stands: `srse.otp.sender=sms` refuses to start and `SmsOtpSenderNotConfigured` never pretends to send, so the gap cannot be reached by accident. Email delivery carries MFA meanwhile. Implement when a gateway contract exists. | N | 3 |
| 7.1a.8 | `OtpSender` seam + a log-only sender so the stack runs offline without either gateway | N | 1 |

**Defaults taken unless told otherwise:** the second factor is checked at
**login**, not per action; a code expires in **5 minutes** and is single-use; an
admin turning the flag on does not invalidate that user's current session; a
code is re-sendable, with a cooldown, because non-delivery is expected often
enough that "request another" must be an ordinary action rather than a dead end.

**Channel — SETTLED: SMS *and* email.**

One code, both delivery paths, one validity window. Sending to both is
deliberate rather than redundant: SMS delivery is not guaranteed and an
undelivered message is otherwise a failed login, so email is the standing
fallback and no user is stranded by a carrier.

Consequences:

- The user record carries **both a mobile number and an email address**, and
  both are mandatory for any account with `mfa_required` set. An admin cannot
  turn the flag on for a user missing either — the UI must refuse it rather
  than create an account that cannot log in.
- **One code, not two.** Generate once, store once, deliver twice. Two codes
  with two windows is a race, and whichever arrives second invalidates the one
  the user is typing.
- **Delivery failure must be visible.** If both channels fail, the login
  attempt fails with a message saying so; it must never silently wait for a
  code that was never sent.
- **`OtpSender` is a seam with three implementations** — SMTP, SMS gateway, and
  a log-only sender selected by config. The log sender keeps the local stack
  runnable with no gateway at all, the same way `AuthMode=mock` keeps auth
  runnable without SSO. Do not make a working laptop build depend on an SMS
  contract.
- **The SMS gateway is an external dependency**: a contract, credentials, and
  probably a sender-ID registration. It is the long pole in §7.1a and the only
  item here that cannot be started unilaterally.



The prototype has none of this. It is now the largest workstream in the product,
and §7.2 is the riskiest thing in the whole plan.

### 7.1b Bootstrapping the first SuperAdmin (decision A13)

**Settled:** the SuperAdmin's password is **supplied at configuration time**,
when the app is deployed. It is not defaulted in code and not printed at boot.

| # | Activity | Type | Est |
|---|---|---|---|
| 7.1b.1 | ✅ (AA-06) Bootstrap on first start **only when the user table is empty**; never on later boots, so a redeploy cannot resurrect or reset the account | N | 1 |
| 7.1b.2 | ✅ (AA-06) Refuse to start if no bootstrap password was supplied and no users exist — fail loudly rather than run unreachable or, worse, open | N | 1 |
| 7.1b.3 | ✅ (AA-06) Force a password change at first login | N | 1 |

**The trap to avoid — MFA and the first login.** §7.1a makes MFA a per-user
flag, and a delivered OTP needs a verified mobile and email. If the bootstrap
SuperAdmin is created with `mfa_required` on and no verified contacts, **nobody
can log in and there is no second admin to fix it.** The account is created
with the flag **off**; the admin verifies their own contacts at first login and
turns it on. The bootstrap path is the one place that ordering matters, and it
is the one place with no way back if it is wrong.

**Never** log the bootstrap password, echo it in a startup banner, or write it
to the config export (§AA-04 already establishes that secrets do not leave in
the bundle).

### 7.1c Deactivation and audit archival (decision A15)

**Settled:** users are **never deleted**, only deactivated; a deactivated user's
audit log is **archived, not removed**.

| # | Activity | Type | Est |
|---|---|---|---|
| 7.1c.1 | ✅ (AA-06) `user.active` flag; deactivation ends live sessions and refuses login | N | 1 |
| 7.1c.2 | ✅ (AA-06) Deactivated users stay visible to admins and stay resolvable as the author of an audit entry | N | 1 |
| 7.1c.3 | Archive a deactivated user's audit entries — moved out of the working set, retained and retrievable | N | 3 |
| 7.1c.4 | Saved queries owned by a deactivated user: retained, and re-assignable by an admin rather than orphaned | N | 2 |

**Archived means retained, not hidden and not deleted.** An audit log that can
be emptied by deactivating its subject is not an audit log — deactivation would
become the way to erase a trail. Archiving takes entries out of the default
view so the working set stays fast; the entries themselves survive and an admin
can still produce them.

**Open, to settle when §7.3 is built:** who may read an archived log. A10 scopes
audit access to the requesting officer's own subtree, but a deactivated user may
have been in a part of the hierarchy the current reader cannot see. Assumed
**SuperAdmin only**, since a scoped reader would otherwise gain visibility
through an archive that they never had while the user was active.

### 7.1d Session policy (decision A14)

**Settled:** concurrent sessions allowed, **30-minute idle timeout**, **8-hour
absolute lifetime**, admin forced logout.

| # | Activity | Type | Est |
|---|---|---|---|
| 7.1d.1 | ✅ 8-hour absolute lifetime — already in `SessionTokenService` (`TOKEN_TTL`), though **hardcoded**; move it to configuration | R | 0 |
| 7.1d.2 | ✅ Admin forced logout — already works via the session version, which deactivation bumps | R | 0 |
| 7.1d.3 | **30-minute idle timeout** — see below, this is the real work | N | 3 |
| 7.1d.4 | Make both timeouts configurable, since government deployments often mandate specific values | N | 1 |

**The idle timeout is not a config value.** Sessions are stateless JWTs, and a
JWT cannot express "expires 30 minutes after the last request" — the token is
already issued and the server keeps nothing. Two ways to get one:

- **Short-lived access tokens plus a refresh token.** Standard, but it is a
  second token type and a refresh endpoint, and it interacts with the MFA
  challenge flow AA-10 just built.
- **Server-side last-activity tracking.** Simpler to reason about and it reuses
  the session-version machinery already there, but it puts a write on every
  authenticated request unless throttled.

Pick one deliberately when 7.1d.3 is scheduled. Both are ordinary work; neither
is a setting.

### 7.2 Row-level data scoping — the hard part

A district officer must see only their district; a taluka officer only their
taluka. **This has to be enforced in the emitted SQL, server-side.** Filtering in
the UI is not enforcement — anyone who can call the API bypasses it.

The engine needs to know, for each registered table, which column carries the
district, which carries the taluka, and so on. That is a per-table scope binding
held in the operational database — structurally similar to the field mapping
that was removed at the fork, for a different purpose.

| # | Activity | Type | Est |
|---|---|---|---|
| 7.2.1 | ✅ (AA-08) Scope-binding model: per registered table, which column holds each hierarchy level | N | 4 |
| 7.2.2 | ✅ (AA-08) Admin UI to define scope bindings, plus the explicit shared-reference-data flag (A4) | N | 3 |
| 7.2.2a | ✅ (AA-08) Deny-by-default enforcement: an unbound, unflagged table is invisible to scoped users on every path, including browse and column listing | N | 3 |
| 7.2.3 | ✅ (AA-09) Inject the scope predicate into **every** emitted query | N | 8 |
| 7.2.4 | ✅ (AA-09) Cover every path that touches data — match, multi-target, comparison summary, CSV and other exports, SQL preview, suggest-keys probe, fan-out guard, column value lists | N | 6 |
| 7.2.5 | ✅ (AA-09) Join policy — settled by construction: each side is filtered in its own derived table, so a join can narrow but never widen (A5) | N | 3 |
| 7.2.6 | ⏸ Group By and aggregates filter before aggregating — **nothing to do until 5.5 exists**; revisit with it | N | 2 |
| 7.2.7 | ✅ (AA-09) Show the injected predicate in the SQL preview — a preview that hides it is misleading | N | 1 |
| 7.2.8 | ✅ (AA-09) Adjust fan-out estimates for the scope filter, or it will refuse queries that would have run | N | 2 |
| 7.2.9 | ✅ (AA-09) Negative tests: prove a scoped user cannot reach another district through *any* endpoint | N | 5 |

**Note on 7.2.9.** This is the test suite that matters most. Every other feature
fails visibly; this one fails silently and looks like working software.

### 7.3 Audit log

**Settled 2026-09-25 — A7 (what is logged) and A8 (retention).**

**Logged:** authentication (login, logout, failed login, lockout), every query
that returns data (match, multi-match, comparison summary), **every export**,
queries planned but not run (SQL preview, fan-out refusals), and every admin
action that changes who can see what (user create/edit, role and scope grants,
scope bindings, password resets, shared-reference flag).

**Not logged: metadata browsing.** The registry cascade fires on every dropdown
interaction, so logging it would outnumber meaningful entries by an order of
magnitude while recording only that someone opened a list of table names the
registry already permits them. If browse coverage is ever wanted, add a daily
rollup rather than per-call rows.

**Retention: keep everything.** At the event set above — roughly 200 events per
officer per day across ~50 officers — this is about 2.5 million rows a year,
which needs no archival strategy. **Build the retention setting anyway**, even
defaulted to never-purge: adding deletion to a table that is append-only by
design is far harder than enabling it later.

**Audit write failure (Q6, settled):** an action whose audit row cannot be
written is **refused** when it is an admin action or an export, and **proceeds
with a loud alarm** when it is a query. An unrecorded export is the case the log
exists for; an unrecorded query is a gap in a record of intent, and refusing it
would turn a logging fault into an outage on the product's main path.

**Tamper-evidence (A9, settled): append-only at the application layer.** No
UPDATE or DELETE path for `audit_event`, ever — enforce it structurally, not by
convention. **No hash chain.** A chain applied retroactively would prove nothing
anyway, since it would hash rows that could already have been altered; chaining
can only ever start from a point in time, so it is a clean future addition
rather than something to leave room for.

**Archived logs (settled): SuperAdmin only.** A deactivated user may have sat
somewhere the current reader cannot see, so scoping alone does not answer it —
and an archive must not hand a reader visibility they never had while the user
was active. See `docs/OPEN_DECISIONS_AUDIT.md`. A9 is the one that must be settled
before 7.3.2 is built.

| # | Activity | Type | Est |
|---|---|---|---|
| 7.3.1 | ✅ (AA-11) Capture: user, action, tables and columns touched, timestamp, source IP. **Exports are the entry not to compromise on** — a download is the moment data leaves the system. | N | 4 |
| 7.3.2 | ✅ (AA-12) Storage and indexing, sized for the A7 event set; retention setting present but defaulted to never-purge | N | 3 |
| 7.3.3 | ✅ (AA-11) Append-only, structurally — A9 settled as application-layer append-only, no hash chain. The repository extends Spring Data's bare `Repository` so no delete is inherited, guarded by a test. | N | ? |
| 7.3.4 | ✅ (AA-12) Log viewer with filters, granted to officers as an RBAC permission | N | 5 |
| 7.3.5 | ✅ (AA-12) Export the log, and audit that export too | N | 2 |
| 7.3.6 | ✅ (AA-11) Store the query shape with placeholders; strip bound values before writing (A6) | N | 2 |
| 7.3.7 | ✅ (AA-12) Access control and retention on the log. Lighter than it would have been: with values stripped it is not a personal-data store, but it still shows who looked at what | N | 2 |
| 7.3.8 | ✅ (AA-12) Scope the log viewer to the requesting officer's subtree (A10) | N | 3 |
| 7.3.9 | (Aa1 / 0.12) Record **query duration** on existing audit events for executed queries | N | 1 |
| 7.3.10 | (Aa1 / 0.12) Super Admin **active queries** panel — in-memory registry of AA-started analytical work; shape/tables only | N | 3 |

---

## 8. UX shell

| # | Activity | Type | Est |
|---|---|---|---|
| 8.1 | Three-page IA + collapsible sidebar | N | 3 |
| 8.2 | Dark / light theme | N | 3 |
| 8.3 | **English / Hindi** — every string, Devanagari font, native-speaker review | N | 10 |
| 8.4 | Empty, loading and error states throughout | R+ | 3 |
| 8.5 | Responsive behaviour | N | 3 |
| 8.6 | **Configuration page: SMS gateway section** — endpoint, credentials, sender ID, plus a "send a test message" action | N | 2 |
| 8.7 | **Configuration page: email gateway section** — SMTP host, port, credentials, from-address, plus a "send a test message" action | N | 2 |

**8.6 / 8.7 follow the pattern that already exists.** Connection settings are
edited on the admin page and persisted through `ConnectionOverrideStore` to a
properties file on a mounted volume, then read back at boot by
`ConnectionOverrideEnvironmentPostProcessor`, so an edit survives container
recreation rather than only a restart. Gateway settings use the **same store
and the same file** under their own prefixes — not a second mechanism.

Two rules carry over with it:

- **These credentials are secrets and must not leave in the config export.**
  AA-04 established that for the DB2 and Presto passwords; an SMS API key and an
  SMTP password are the same kind of thing and the same masking applies.
- **A "test" action belongs beside each**, the way the connection editor tests
  before saving. A gateway that is only discovered to be misconfigured when an
  officer cannot log in is a gateway configured too late.

---

## 9. Non-functional

| # | Activity | Type | Est |
|---|---|---|---|
| 9.1 | ✅ Query timeouts, row caps, fan-out ceiling — all configurable | R | 0 |
| 9.2 | ✅ SQL emission validated against the engine's own analyzer, not just a parser | R | 0 |
| 9.3 | Performance testing at realistic volumes (not 200k synthetic rows) | N | 5 |
| 9.4 | ✅ Containerised deployment, environment configuration | R | 0 |
| 9.5 | Backup and restore of the operational database | N | 2 |

---

## 10. Post-AA-20 slices (added 2026-10-12)

From a code review against this plan. Aa1 work (§2.5, 3.1.6, 7.3.9–7.3.10, brief
AA-21) is tracked where it already sits and is not repeated here. Supported
databases for every item: **PostgreSQL, DB2, Lakehouse (0.13).**

### 10.A Correctness and security (do first)

| # | Activity | Type | Est |
|---|---|---|---|
| 10.A.1 | ◐ **Scope enforcement on the direct-JDBC route.** `AnalysisExecutionRouter` sends single-source external queries straight to JDBC, bypassing Presto. Prove scope predicate, deny-by-default and identifier gates hold on that path. Negative tests as 7.2.9: a scoped officer must not reach out-of-scope rows through a PostgreSQL or DB2 registration via match, export, SQL preview, value lists and suggest-keys. **Silent-leak risk — highest priority.** *2026-10-12: `ExternalScopeEnforcementTest` proves, on PostgreSQL and DB2 through the real router, that the predicate and its bind value survive routing for match, fan-out estimates, CSV export, the value picker, **multi-target sub-matches** (`MultiTargetRecordMatchService.toSingleMatch` → `planMatch`), and **SQL preview** (`renderQueryForDisplay` on routed SQL). No-entitlement → `WHERE 1 = 0`. Still needs a run against real PostgreSQL/DB2 (10.A.4). Suggest-keys on external tables: refused at API — see 10.A.2(b).* | N | 4 |
| 10.A.2 | ◐ Dialect check on the direct route for PostgreSQL and DB2 (quoting, row limiting, cast). Presto-only constructs (fuzzy, folded groups) must refuse clearly when no catalog alias exists, not fail with a driver error. *Findings 2026-10-12: (a) ✅ FIXED: `TRY_CAST` Presto-only — see prior note; (b) ✅ FIXED 2026-10-12: `JoinKeySuggestService` refuses `jdbc_*` via `AnalysisExecutionRouter.containsExternal` with 400 before Presto; (c) ✅ RESOLVED by 10.A.4 on DB2 11.5.8 and PostgreSQL 16: `ORDER BY … NULLS FIRST`, unquoted lower-case scope identifiers (DB2 folds them onto its upper-case columns) and `CAST(x AS VARCHAR)` without a length all work.* | N | 3 |
| 10.A.3 | ✅ **Hide the four dormant types (0.13).** Remove MySQL, MariaDB, SQL Server and Oracle from the Add Connection UI **and** make the API refuse them. Keep the enum entries. Scrub docs and tests that imply they work. *Done 2026-10-12: `ExternalDataSourceType.supported()` + `parseSupported`; the type list offers two entries and `create` refuses the rest with a 400. Existing rows of a dormant type, if any, are untouched. Docs scrub not yet done.* | N | 1 |
| 10.A.4 | ✅ Re-run the manual real-Presto checks (CLAUDE.md "Testing discipline") with a PostgreSQL and a DB2 source added; record results. *Done 2026-10-12 against PostgreSQL 16, **DB2 11.5.8 (amd64 under emulation)** and Presto 0.297 (stack: `docker/verify/docker-compose.yml`; test: `ExternalDirectRealEngineIT`, opt-in via `SRSE_REAL_ENGINE_IT=true`, 11/11 pass). Verified on BOTH engines through the real router: INNER/LEFT/RIGHT/FULL return the expected rows (3/5/4/6); a scoped officer sees nothing from another district through any join type, and an unmatched row that is the officer's own survives a LEFT join; a grouped count aggregates only the officer's rows; the value picker's `ORDER BY … NULLS FIRST` and case-insensitive `lower(CAST(x AS VARCHAR)) LIKE` work; fuzzy and number-vs-text are refused on the direct route. On PostgreSQL only: the same fuzzy and `TRY_CAST` queries run correctly through Presto federation (3 rows each). **Not re-run:** the iceberg/lakehouse cross-catalog, ANY_OF all-NULL and post-join comparison checks — nothing in this work touched Presto SQL emission. **Finding — DB2 cannot federate:** PrestoDB 0.297 ships no DB2 connector (plugins: mysql, oracle, postgresql, redshift, sqlserver, … no db2), so a DB2 source can never get a catalog alias. Consequently for DB2 sources fuzzy matching, number-vs-text comparison, date coercion, SUM over text and ANY_OF are always refused, and DB2 cannot be joined to the lakehouse or to another database. Decision 0.13's "DB2 supported" therefore means direct single-source queries without those features; decide whether that is acceptable, and say so in admin documentation.* | N | 2 |

### 10.B Identity

| # | Activity | Type | Est |
|---|---|---|---|
| 10.B.1 | ✅ Move `TOKEN_TTL` (hardcoded 8h) into configuration; also covers 7.1d.1 / 7.1d.4. *Done 2026-10-12: `srse.session.absolute-lifetime` / `idle-timeout` / `activity-write-interval` (env `SRSE_SESSION_*`), validated at startup.* | R+ | 1 |
| 10.B.2 | ✅ **Idle timeout (7.1d.3)** — recommend server-side last-activity tracking with a throttled write (≤ once a minute), reusing the session-version machinery. Rejected: refresh tokens (second token type, interacts with the MFA challenge flow). *Done 2026-10-12: nullable `app_user.last_activity_at` (changelog 013); `SessionVersionValidationFilter` refuses a session idle past the timeout with 401 + `X-Auth-Reason: idle-timeout`, and stamps activity at most once a minute with a conditional bulk update; every login resets the stamp. **Known limits:** tracked per USER, not per session, so activity in one browser keeps that user's other concurrent sessions alive (a per-session timeout needs a session table); local auth mode only; idle expiry is not written to the audit log. **Follow-up:** show the reason on the login page (en/hi strings; the header must be added to CORS `Access-Control-Expose-Headers`). The JWT signing key fix is below (10.B.7).* | N | 3 |
| 10.B.3 | **Self-service password reset (7.1.4)** — add a `PASSWORD_RESET` value to `OtpPurpose` and reuse the existing sender, 5-minute window and cooldown. Identical response whether or not the account exists (no enumeration). Audited. | N | 4 |
| 10.B.4 | **Audit archival on deactivation (7.1c.3)** — explicit archive state, retained and SuperAdmin-only; replaces today's read-time exclusion. | N | 3 |
| 10.B.5 | Saved queries owned by a deactivated user: admin reassign action (7.1c.4) | N | 2 |
| 10.B.6 | ✅ Verify scope applies before aggregation under Group By (7.2.6 — 5.5 now exists); add negative tests. *Verified 2026-10-12, no leak: both the single-source and two-table builders put each side's scope in its own derived table in FROM, and GROUP BY is appended after the join, so the filter applies before aggregation and cannot be nullified by an outer join. `GroupedScopeEnforcementTest` pins it for INNER/LEFT/FULL (distinct per-side bind values, GROUP BY after both derived tables, outer WHERE stays `TRUE`), for an unentitled officer (`1 = 0`, never the raw table) and for the grouped CSV; `ExternalScopeEnforcementTest` adds the grouped case on PostgreSQL and DB2. Mutation-checked: dropping one side's scope fails 17 of 23. Not covered: small-group disclosure (a group of 1 reveals one record) — a product question, not a scope bug.* | N | 2 |
| 10.B.7 | ✅ **Stable JWT signing key.** The key was generated per boot, so every restart signed everyone out and a second instance could not validate tokens. *Done 2026-10-12: `SRSE_SESSION_SIGNING_KEY` (Base64, ≥ 32 bytes) is read by `SessionTokenService`; `SessionSigningKeyStartupValidator` makes `srse.auth-mode=local` refuse to start without one (as for the bootstrap password); `mock` mode keeps a random per-boot key with a warning. Bad keys fail without echoing the value. `scripts/deploy-docker.sh` generates one into `.env`. **Operational:** an existing `local` deployment must set the key before upgrading or the backend will not start; changing the key signs everyone out. No dual-key rotation yet.* | N | 1 |

### 10.C Operability

| # | Activity | Type | Est |
|---|---|---|---|
| 10.C.1 | Per-catalog and per-saved-connection health panel (2.3.2), plus the behaviour when a catalog disappears (2.3.3) — registered tables stay listed but flagged unavailable | N | 3 |
| 10.C.2 | `ANALYZE` scheduling (2.2.9), admin-controlled, so the fan-out guard reads statistics instead of computing live | N | 2 |
| 10.C.3 | Document the catalog-per-source model and the Presto catalog-alias setup for ops (2.3.1) | N | 1 |

### 10.D Polish and housekeeping

| # | Activity | Type | Est |
|---|---|---|---|
| 10.D.1 | **Hindi coverage audit.** These files do not reference the i18n catalog and probably carry hardcoded English: `AdminIdentityPanel`, `AuditLogPanel`, `OtpGatewayPanel`, `TableScopeBindingEditor`, `LakehouseCascade`, `ValueFilterPicker`, `AnalysisResultsGrid`, `ComparisonPairsEditor`, `MultiTargetJoinCanvas`, `ChartsSection`, `VennDiagram`, `MultiSelectDropdown`. Not yet opened — some may only render data. Then native-speaker review (8.3). | N | 6 |
| 10.D.2 | Repair the frontend toolchain (`npm run lint` crashed loading ESLint's `ajv` on 2026-10-12 — likely a broken `node_modules`; try `npm ci`), then fix the pre-existing `react-hooks` errors | N | 1 |
| 10.D.3 | Route-level guard for `/admin456`; remove dead `srse.guardrails.*` and `age-band-column` config | N | 1 |
| 10.D.4 | Reconcile `CLAUDE.md` with this plan: its "Known broken" note on `RuleCompiler` is stale since AA-13, and its data-sources paragraph names two database types where 0.13 names three platforms | N | 0 |

**Order:** 10.A → 10.B → Aa1 (AA-21: value-picker flag first, then snapshot,
then QB read switch, then active queries) → 10.C → 10.D. 10.A comes first
because the direct-JDBC path is new and is the only item where a defect leaks
data silently.

**Deferred, not scheduled:** 6.8 scheduled runs (0.14); 7.1a.7 SMS sender (awaiting
a gateway contract); 1.6 package rename; 2.2.8 Trino.

---

## Sequence

**Phase 1 — Make it safe and ours (≈2 weeks)**
1.1, 1.2, 1.3, 1.5, 0.x decisions, 2.1.1 if moving database.
Restoring the RBAC tests comes first: shipping features on untested authorisation
is the one thing here that could go badly wrong quietly.

**Phase 2 — Make it look like the prototype (≈3 weeks)**
8.1, 8.2, 3.1.x, 4.1–4.4, 6.4–6.6.
Mostly frontend. Ends with something demonstrable.

**Phase 3 — Make it do the new things (≈4 weeks)**
5.5 (Group By — the largest single item), 5.1, 4.5, 2.3.x.
Mostly backend, and the real engineering.

**Phase 4 — Make it a product (≈3 weeks)**
7.x, 6.7, 8.3, 9.3.

**Phase 5 — Users, scoping and audit (≈8–10 weeks)**
All of §7. Row-level scoping (7.2) should not be retrofitted late: every query
path built before it exists is a path that has to be revisited and re-proved.
Consider bringing 7.2.1–7.2.3 forward into Phase 3 so later features are built
against it from the start.

**≈20–22 weeks with two developers**, excluding items marked `?` and any
multi-tenancy. Add contingency: this codebase has repeatedly shown defects that
only appear against a real engine, never in unit tests — and §7.2 is the one
area where such a defect is a data breach rather than a bug.

---

## Open questions — user management, scoping and audit

Answers change the design, not just the estimate. **A4 is the one that leaks
data if left unanswered.**

### A. Data scoping

| # | Question | Why it matters |
|---|---|---|
| A1 | **DECIDED: configurable per deployment.** Levels are data, not code. | The scope predicate is built dynamically from whatever levels a deployment defines, and scope bindings reference level ids rather than hardcoded column names. |
| A2 | **DECIDED: an officer can hold multiple assignments** — district, taluka, department and so on. | Larger than first asked. "Department" is not a geographic level, which implies **two or more orthogonal dimensions** rather than one tree. See open question A16 — this is the last modelling question. |
| A3 | **DECIDED: yes — a scope is a subtree.** District → Taluka → Village/City: a district officer sees the district and everything under it; a village officer sees only that village. | Scope is stored as a node and expanded to descendants at query time, so the assignment survives reorganisation of levels beneath it. Use a materialised path so "descendants of X" is a prefix match rather than a recursive walk. |
| A4 | **DECIDED: deny by default.** A registered table with no scope binding is invisible to a scoped user unless an admin explicitly flags it as shared reference data. | Closes the silent-leak path: a table can only escape scoping by a deliberate admin act, never by omission. The flag must be per table and visible in the admin UI, so "why can everyone see this?" always has an answer. |
| A5 | **DECIDED (my judgement): allowed, with every scoped side still filtered.** A4 already denies unbound tables outright, so the dangerous case cannot be built. Joining a scoped table to a **shared reference** table is permitted and the scoped side keeps its predicate; joining two scoped tables applies both. | The filter is never relaxed by the presence of another table — a join can narrow the result, never widen it. |

### B. Audit

| # | Question | Why it matters |
|---|---|---|
| A6 | **DECIDED (revised): store the query SHAPE, never the bound values.** The SQL is recorded with placeholders; Aadhaar numbers, names and other typed filter values are not stored. | Keeps personal data out of the audit log entirely, so the log never becomes a second copy of the data needing the same protection. The trade, accepted: you can see *what an officer did* structurally — which tables and columns, which operators — but not the exact value they searched for. |
| A7 | **ANSWERED (as audit Q1): log everything except metadata browsing.** Authentication, queries, exports, refusals and admin actions. See §7.3. | Settled 2026-09-25. |
| A8 | **ANSWERED (as audit Q2): keep everything.** ~2.5M rows/year at the A7 event set; retention setting present but defaulted to never-purge. See §7.3. | Settled 2026-09-25. |
| A9 | **ANSWERED: append-only at the application layer.** No UPDATE or DELETE path for audit rows, ever. **No hash chain** — revisit only if a rule later demands it. | Settled 2026-09-25. Unblocks 7.3.2 onward. |
| A10 | **DECIDED: yes, the audit log is scoped.** A district officer with log access sees entries for users within their own subtree, not other districts'. | Without this the log leaks exactly what the scoping prevents. Open sub-question: an entry written by a *state-level* user who queried one district's data — does it appear to that district's log viewer? Simplest rule is to scope by the acting user's node, not by the data they touched. |

### D. Follow-on from A2

| # | Question | Why it matters |
|---|---|---|
| A16 | **ANSWERED as decision 0.7: yes, a second orthogonal dimension.** Confirmed 2026-09-25; the model built in AA-06 is N dimensions, each its own tree. | Built. |

### E. Consequence worth designing around

| # | Note |
|---|---|
| A17 | **NOT A QUESTION — implemented.** Bind each table at the coarsest level that still covers the officer's scope. Built in AA-08 and corrected in `7839346`, where a binding coarser than the officer's own scope was found to widen their view and is now rejected as unusable. | Done. |

### C. Identity and accounts

| # | Question | Why it matters |
|---|---|---|
| A11 | **ANSWERED: fully standalone local accounts.** No existing directory, no SSO at this stage, access limited to a small number of users. | Build password management, reset and lockout in-house. Keep the `AuthMode` seam so SSO can be added later without touching `SecurityConfig`. |
| A12 | **ANSWERED: yes — OTP, required, with a per-user admin toggle.** See §7.1a. Channel still open (0.5c). | Adds an enrollment flow, a verification step at login, a recovery path, and possibly an external gateway. |
| A13 | **ANSWERED: the SuperAdmin password is supplied when the app is configured**, not defaulted in code. See §7.1b. | Bootstrap runs once, on an empty user table. A shipped default password is the single worst thing this product could do, so there must not be one. |
| A14 | **ANSWERED: concurrent sessions allowed, 30-minute idle timeout, 8-hour absolute lifetime, admin forced logout.** The 8 hours and forced logout already exist; **the idle timeout does not and is not free** — see §7.1d. | Settled 2026-09-25. |
| A15 | **ANSWERED: soft delete — deactivation only. A deactivated user's audit log is archived, never removed.** See §7.1c. | A user row is never physically deleted, so audit entries keep a real author forever. |
