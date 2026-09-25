# Analytics Advisor — build plan

Working plan. Numbered so items can be added, split or struck. Estimates are
developer-days unless stated; **R** = reused from SRSE, **N** = new build,
**R+** = reused but needs extending.

Target UX: `docs/Analytics_Advisor.html` (a mockup — hardcoded data, browser-only
logic, six rows per table). Treat it as a picture of the destination, not a spec.

---

## Status at 2026-09-25

**Built and verified against a running stack:** the fork cleanup (§1 bar the
package rename), the whole of user management and data scoping (§7.1, §7.1b,
§7.1c.1–2, §7.2), and everything inherited from SRSE that survived the fork —
the match engine, the lakehouse registry, connection management, exports.

**In flight:** §7.1a MFA (brief AA-10 with the developer), minus the SMS sender.

**Blocked on someone outside the team**, and both worth starting now because
nothing else waits behind them:

- **SMS gateway contract** — credentials and sender-ID registration. Gates
  7.1a.7 only; the seam is built around it.
- **A9, tamper-evidence** — a compliance position, not an engineering choice.
  Gates 7.3.2, and retrofitting a hash chain onto a populated append-only table
  is far more expensive than designing it in. See `docs/OPEN_DECISIONS_AUDIT.md`.

**Not started:** §2.3 source-system ops, §3 label vocabulary and the Database
Overview page, §4 Extract Records, §5 Report Analysis (5.5 Group By is the
largest single item left), §6.4–6.8 exports and saved queries, §7.3 audit log,
§8 UX shell, §9.3/9.5.

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

### 2.3 How source systems connect

**Each source system is a Presto catalog**, configured on the Presto server, not
in this application. A CRM on MySQL and an ERP on Oracle become two catalogs;
one JDBC connection reaches both, and cross-system joins work natively.

| # | Activity | Type | Est |
|---|---|---|---|
| 2.3.1 | Document the catalog-per-source-system model and what ops must configure | N | 1 |
| 2.3.2 | Connection health panel — per catalog reachable/unreachable | N | 2 |
| 2.3.3 | Decide behaviour when a catalog disappears (registered tables pointing at it) | N | 1 |

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
| 3.1.1 | Extend registration with source-system and table-group labels | R+ | 2 |
| 3.1.2 | Admin UI to manage the label vocabulary | N | 2 |
| 3.1.3 | Browse hierarchy driven by labels, addresses unchanged underneath | R+ | 2 |
| 3.1.4 | Database Overview page — drill down to attributes and data types | N | 3 |
| 3.1.5 | Column metadata: business name, hide, fuzzy flag, comparison mode | R | 0 |

---

## 4. Query Builder — Extract Records

| # | Activity | Type | Est |
|---|---|---|---|
| 4.1 | Source / destination table pickers through the hierarchy | R+ | 1 |
| 4.2 | Multi-select attributes per side | R+ | 2 |
| 4.3 | Primary key selection (this is the join key) | R | 0 |
| 4.4 | Single-source mode (one table, no join) | N | 2 |
| 4.5 | **Rules on source and destination** — operators, AND-combined | R+ | 4 |
| 4.6 | Live SQL preview before execution | R | 0 |
| 4.7 | Run, stream results, progress | R | 0 |
| 4.8 | Fan-out guard — refuse an unrunnable query with an estimate, not a timeout | R | 0 |

---

## 5. Query Builder — Report Analysis

| # | Activity | Type | Est |
|---|---|---|---|
| 5.1 | Value filter — pick attribute, keep selected values (server-side) | N | 3 |
| 5.2 | Fuzzy matching, Levenshtein, threshold % | R | 0 |
| 5.3 | Fuzzy options: case-sensitive, ignore spaces | N | 1 |
| 5.4 | Fuzzy against typed text rather than a second column | N | 2 |
| 5.5 | **Group By + aggregators (SUM/COUNT/AVG/MIN/MAX)** — server-side SQL, interacts with guardrails | N | 8 |
| 5.6 | Column comparison with match/mismatch verdicts and no-counterpart handling | R | 0 |
| 5.7 | Show mismatches only | R | 0 |

---

## 6. Results & exports

| # | Activity | Type | Est |
|---|---|---|---|
| 6.1 | Results grid: sort, per-column filter, column visibility, paging | R | 0 |
| 6.2 | Large-result behaviour: stop rendering past a threshold, offer the complete file instead | R | 0 |
| 6.3 | CSV export — complete result, streamed, not a re-serialisation of the screen | R | 0 |
| 6.4 | Excel export | N | 3 |
| 6.5 | JSON export | N | 1 |
| 6.6 | XML export | N | 1 |
| 6.7 | Saved queries — name, reopen, share | N | 5 |
| 6.8 | Scheduled runs / delivery | N | ? |

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
| 7.1a.7 | SMS gateway sender (**needs a gateway contract + credentials — external dependency, start procurement early**) | N | 3 |
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

**Still open: A9 (tamper-evidence), A14 (sessions), and who may read an archived
log.** See `docs/OPEN_DECISIONS_AUDIT.md`. A9 is the one that must be settled
before 7.3.2 is built.

| # | Activity | Type | Est |
|---|---|---|---|
| 7.3.1 | Capture: user, action, tables and columns touched, timestamp, source IP. **Exports are the entry not to compromise on** — a download is the moment data leaves the system. | N | 4 |
| 7.3.2 | Storage and indexing, sized for the A7 event set; retention setting present but defaulted to never-purge | N | 3 |
| 7.3.3 | Append-only / tamper-evident storage if audit rules require it | N | ? |
| 7.3.4 | Log viewer with filters, granted to officers as an RBAC permission | N | 5 |
| 7.3.5 | Export the log, and audit that export too | N | 2 |
| 7.3.6 | Store the query shape with placeholders; strip bound values before writing (A6) | N | 2 |
| 7.3.7 | Access control and retention on the log. Lighter than it would have been: with values stripped it is not a personal-data store, but it still shows who looked at what | N | 2 |
| 7.3.8 | Scope the log viewer to the requesting officer's subtree (A10) | N | 3 |

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
| 9.1 | Query timeouts, row caps, fan-out ceiling — all configurable | R | 0 |
| 9.2 | SQL emission validated against the engine's own analyzer, not just a parser | R | 0 |
| 9.3 | Performance testing at realistic volumes (not 200k synthetic rows) | N | 5 |
| 9.4 | Containerised deployment, environment configuration | R | 0 |
| 9.5 | Backup and restore of the operational database | N | 2 |

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
| A7 | What counts as "accessed"? Every query, every export, every table listing, every login attempt? | Decides log volume by an order of magnitude. |
| A8 | Retention, archival and volume expectations. | Every query by every officer, indefinitely, is a large table. |
| A9 | Is tamper-evidence required (append-only, checksummed), or is a normal table acceptable? | Government audit rules often require the former; it is much harder. |
| A10 | **DECIDED: yes, the audit log is scoped.** A district officer with log access sees entries for users within their own subtree, not other districts'. | Without this the log leaks exactly what the scoping prevents. Open sub-question: an entry written by a *state-level* user who queried one district's data — does it appear to that district's log viewer? Simplest rule is to scope by the acting user's node, not by the data they touched. |

### D. Follow-on from A2

| # | Question | Why it matters |
|---|---|---|
| A16 | **ANSWERED as decision 0.7: yes, a second orthogonal dimension.** Confirmed 2026-09-25; the model built in AA-06 is N dimensions, each its own tree. | Built. |

### E. Consequence worth designing around

| # | Note |
|---|---|
| A17 | **Bind each table at the highest level it actually carries.** A district officer querying a table that has a `district_code` column gets `district_code = ?` — one value, cheap. The same officer querying a table that only has `village_code` needs every village in that district, which is an `IN` list running into thousands and a slow query. Where a table carries several levels, the binding should prefer the coarsest column that covers the user's scope. Worth a cap and a clear refusal when expansion would be unreasonable. |

### C. Identity and accounts

| # | Question | Why it matters |
|---|---|---|
| A11 | **ANSWERED: fully standalone local accounts.** No existing directory, no SSO at this stage, access limited to a small number of users. | Build password management, reset and lockout in-house. Keep the `AuthMode` seam so SSO can be added later without touching `SecurityConfig`. |
| A12 | **ANSWERED: yes — OTP, required, with a per-user admin toggle.** See §7.1a. Channel still open (0.5c). | Adds an enrollment flow, a verification step at login, a recovery path, and possibly an external gateway. |
| A13 | **ANSWERED: the SuperAdmin password is supplied when the app is configured**, not defaulted in code. See §7.1b. | Bootstrap runs once, on an empty user table. A shipped default password is the single worst thing this product could do, so there must not be one. |
| A14 | Concurrent sessions, idle timeout, forced logout. | Usually mandated in government deployments. |
| A15 | **ANSWERED: soft delete — deactivation only. A deactivated user's audit log is archived, never removed.** See §7.1c. | A user row is never physically deleted, so audit entries keep a real author forever. |
