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

## 7. Platform — absent from the prototype entirely

The prototype has no login, no users, no permissions, no audit. A real product
needs all of it.

| # | Activity | Type | Est |
|---|---|---|---|
| 7.1 | Authentication (decision 0.5) | R+ | 5 |
| 7.2 | Roles: who administers vs who queries | R | 0 |
| 7.3 | Per-method authorisation on every endpoint | R | 0 |
| 7.4 | Audit log — who ran what, when, against which tables | N | 4 |
| 7.5 | Multi-tenancy, if 0.4 says yes | N | ? |
| 7.6 | Session handling, timeouts, logout | N | 2 |

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

**≈12 weeks with two developers**, excluding items marked `?` and any
multi-tenancy. Add contingency: this codebase has repeatedly shown defects that
only appear against a real engine, never in unit tests.
