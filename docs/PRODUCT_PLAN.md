# Analytics Advisor — build plan

Working plan. Numbered so items can be added, split or struck. Estimates are
developer-days unless stated; **R** = reused from SRSE, **N** = new build,
**R+** = reused but needs extending.

Target UX: `docs/Analytics_Advisor.html` (a mockup — hardcoded data, browser-only
logic, six rows per table). Treat it as a picture of the destination, not a spec.

---

## 0. Decisions needed before building

These change the shape of the work. Nothing below them is safe to start until
they are settled.

| # | Decision | Why it matters |
|---|---|---|
| 0.1 | **Operational database: stay on DB2, or move to PostgreSQL?** | DB2 was a Rajasthan constraint, not a product choice. Everything in §2.1 depends on it. |
| 0.2 | **Query engine: PrestoDB only, or Trino too?** | Different JDBC drivers and dialect differences. SRSE is deliberately PrestoDB 0.297. |
| 0.3 | **How does "Source System" map to physical storage?** | See §3.1. The prototype's hierarchy is not the physical one. |
| 0.4 | **Single tenant or multi-tenant?** | Decides whether every table below needs an owner/tenant column. Cheap now, expensive later. |
| 0.5 | **Who are the users?** | Prototype has no login at all. Decides §7 entirely. |
| 0.6 | **Row limits.** Prototype shows 6 rows; SRSE caps display at 10,000 and streams to CSV beyond. | Decides §6. |

---

## 1. Foundation (carried from the fork)

| # | Activity | Type | Est |
|---|---|---|---|
| 1.1 | Restore RBAC + controller tests removed at fork (6 slices). **Highest priority — live behaviour is currently untested.** | N | 2 |
| 1.2 | Restore admin config backup, trimmed to connections + registrations + column metadata | N | 2 |
| 1.3 | Decide and act on the inherited age filter (drop, or back with a real field concept) | N | 1 |
| 1.4 | Rewrite `CLAUDE.md` for this product | N | 1 |
| 1.5 | Strip SRSE screens from the frontend (Rule Engine page, scheme panels, field-mapping editor) | N | 2 |
| 1.6 | Rename package root once both repos stop trading fixes | N | 1 |

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

The prototype has none of this. It is now the largest workstream in the product,
and §7.2 is the riskiest thing in the whole plan.

### 7.1 Users and the organisation hierarchy

Built in-house — no Keycloak. That means we own password storage, reset, lockout,
sessions and everything else an identity product would have given us.

| # | Activity | Type | Est |
|---|---|---|---|
| 7.1.1 | Org hierarchy: levels (State → District → Taluka → …) and nodes with parents | N | 4 |
| 7.1.2 | Users, roles, and assignment to one or more hierarchy nodes | N | 5 |
| 7.1.3 | Local authentication: password hashing, login, logout, session expiry | N | 4 |
| 7.1.4 | Password lifecycle: admin-set initial, self-service reset, expiry, failed-attempt lockout | N | 5 |
| 7.1.5 | SuperAdmin / Admin screens to create users, assign roles and scopes, deactivate | N | 6 |
| 7.1.6 | Should an Admin be scoped themselves (a district admin managing only their own district)? | N | 3 |

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
| 7.2.1 | Scope-binding model: per registered table, which column holds each hierarchy level | N | 4 |
| 7.2.2 | Admin UI to define scope bindings, with an explicit "no scope column" answer | N | 3 |
| 7.2.3 | Inject the scope predicate into **every** emitted query | N | 8 |
| 7.2.4 | Cover every path that touches data — match, multi-target, comparison summary, CSV and other exports, SQL preview, suggest-keys probe, fan-out guard, column value lists | N | 6 |
| 7.2.5 | Join policy: a scoped table joined to an unscoped one | N | 3 |
| 7.2.6 | Group By and aggregates filter before aggregating | N | 2 |
| 7.2.7 | Show the injected predicate in the SQL preview — a preview that hides it is misleading | N | 1 |
| 7.2.8 | Adjust fan-out estimates for the scope filter, or it will refuse queries that would have run | N | 2 |
| 7.2.9 | Negative tests: prove a scoped user cannot reach another district through *any* endpoint | N | 5 |

**Note on 7.2.9.** This is the test suite that matters most. Every other feature
fails visibly; this one fails silently and looks like working software.

### 7.3 Audit log

| # | Activity | Type | Est |
|---|---|---|---|
| 7.3.1 | Capture: user, action, tables and columns touched, timestamp, source IP | N | 4 |
| 7.3.2 | Storage, indexing and retention — this grows with every query run | N | 3 |
| 7.3.3 | Append-only / tamper-evident storage if audit rules require it | N | ? |
| 7.3.4 | Log viewer with filters, granted to officers as an RBAC permission | N | 5 |
| 7.3.5 | Export the log, and audit that export too | N | 2 |
| 7.3.6 | Decide what is stored of the query itself — see open question A6 | N | 1 |

---

## 8. UX shell

| # | Activity | Type | Est |
|---|---|---|---|
| 8.1 | Three-page IA + collapsible sidebar | N | 3 |
| 8.2 | Dark / light theme | N | 3 |
| 8.3 | **English / Hindi** — every string, Devanagari font, native-speaker review | N | 10 |
| 8.4 | Empty, loading and error states throughout | R+ | 3 |
| 8.5 | Responsive behaviour | N | 3 |

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
| A1 | How deep is the hierarchy, and is it fixed (State / District / Taluka / Village) or configurable per deployment? | Fixed is much simpler; configurable means the scope predicate is built dynamically. |
| A2 | Can one user hold several scopes at the same level — three districts, say? | Decides whether the predicate is `= ?` or `IN (?, ?, …)`, and how the UI reads. |
| A3 | Does a higher officer automatically see everything beneath them? | Presumably yes, but it decides whether scope is stored as a node or as an expanded set of leaves. |
| A4 | **What happens to a registered table that has no district/taluka column at all?** Denied to scoped users, or treated as shared reference data? | This is the silent-leak question. If the default is "no scope column means no filter", every reference table becomes a hole. I would default to **deny**, with an explicit per-table "this is shared reference data" flag an admin must set. |
| A5 | Joining a scoped table to an unscoped one — allowed, and if so does the scope of one side constrain the result? | Otherwise a user joins their district table to an unscoped one and reads everything. |

### B. Audit

| # | Question | Why it matters |
|---|---|---|
| A6 | Do we store the SQL and its parameter values? | Parameters can contain personal data — an Aadhaar number typed as a filter would land in the audit log, which then needs the same protection as the data itself. Storing the shape without the values is safer. |
| A7 | What counts as "accessed"? Every query, every export, every table listing, every login attempt? | Decides log volume by an order of magnitude. |
| A8 | Retention, archival and volume expectations. | Every query by every officer, indefinitely, is a large table. |
| A9 | Is tamper-evidence required (append-only, checksummed), or is a normal table acceptable? | Government audit rules often require the former; it is much harder. |
| A10 | Is the audit log itself scoped? Can a district officer with log access see other districts' entries? | Otherwise the log leaks what the scoping prevents. |

### C. Identity and accounts

| # | Question | Why it matters |
|---|---|---|
| A11 | Fully standalone local accounts, or must it integrate with an existing directory (LDAP / AD / departmental SSO) later? | "No Keycloak" rules out one product, not the integration requirement. Worth knowing now. |
| A12 | Is MFA or OTP required? Aadhaar OTP was a requirement on the predecessor product. | Adds a delivery channel and a whole flow. |
| A13 | Who creates the first SuperAdmin, and how? | Bootstrapping is a real step, not a detail. |
| A14 | Concurrent sessions, idle timeout, forced logout. | Usually mandated in government deployments. |
| A15 | Deactivate versus delete a user — what happens to their audit history and saved queries? | Deleting a user who appears in audit records breaks the record. |
