# Open decisions — questions to answer

Seven decisions are blocking. This file states each as a question, says what
each answer commits you to, and what it costs to decide later rather than now.

Answers get recorded in `docs/PRODUCT_PLAN.md` and `CLAUDE.md`.

**Order matters.** Q1, Q4, Q5 and Q7 block the largest piece of remaining work
(§7 user management, scoping and audit — roughly a third of the build). Q3
blocks §3. Q2 and Q6 can safely wait.

---

## Q1 — Where does the product's own data live? *(blocks §2.1, §7)*

Not the data being analysed — that stays in the lakehouse. This is the
product's own store: registered tables, column metadata, and everything §7 adds
(users, roles, scope assignments, audit log, saved queries).

**The question:** is this deployment required to use DB2, or is the database our
choice?

| Answer | What it commits you to |
|---|---|
| **Stay on DB2** | Works today, driver already wired, matches the Rajasthan estate. Costs: licensing, a heavy local container (no arm64 build — it runs under emulation on Apple Silicon and takes minutes to boot), and weaker JSON handling than the audit log and saved-query payloads want. |
| **Move to PostgreSQL** | Free, light, standard, better JSON, trivially containerised. Costs: a migration of the existing schema and a change to the operational datasource config. The analytical plane is untouched either way. |

**Why now:** §7 adds five or more tables, and the audit log is the
highest-volume table in the product. Moving before those exist is a small
schema migration. Moving after means migrating live audit history.

**My recommendation:** PostgreSQL, *unless* the deployment target mandates DB2.
DB2 was a constraint inherited from SRSE's environment, not a choice made for
this product. The JPA layer makes the switch cheap now and expensive later.

**What I need from you:** does the client's environment require DB2, or can we
pick?

---

## Q2 — PrestoDB only, or Trino as well? *(blocks nothing today)*

**The question:** will this product ever point at a query engine that is not
PrestoDB 0.297?

PrestoDB and Trino forked in 2020 and are now different drivers with different
dialects — in exactly the functions the match engine leans on
(`levenshtein_distance`, `array_join`, `TRY_CAST`, `CROSS JOIN UNNEST`).

| Answer | What it commits you to |
|---|---|
| **PrestoDB only** | Nothing changes. All emitted SQL is already tested against 0.297, including against its own parser. |
| **Trino too** | A dialect abstraction over the SQL emitter, and a second set of the manual container runs. Bounded work — the emitter is already centralised — but real. |

**Why it can wait:** nothing is cheaper to decide later. The emitter is one
place, so adding a dialect seam when a second engine actually appears costs
about the same as building it speculatively now.

**My recommendation:** PrestoDB only. Revisit when a non-watsonx.data deployment
is actually on the table.

---

## Q3 — What is a "Source System"? *(blocks §3)*

The prototype shows officers picking a **Source System**, then a table. The real
addressing is `catalog.schema.table`. These are not the same thing, and the
prototype does not say which it means.

| Answer | What it commits you to |
|---|---|
| **A display grouping** — a label on registered tables, nothing more | Cheapest. An admin tags each registered table with a source system and officers filter by it. Exactly how `layer` already works. |
| **A catalog** — one source system = one Presto catalog | Free, since the address already carries it. But it forces the physical layout to match how officers think, which it usually does not. |
| **A real entity that owns its own connection** | Lets one deployment reach several unrelated clusters. Much larger: connection management stops being one pair of planes and becomes a set. |

**The question behind the question:** does this product need to query **more than
one lakehouse cluster**, or is it always one connection reaching many catalogs?
Presto federates across catalogs on a single connection, which covers most of
what "multiple source systems" usually means.

**My recommendation:** a display grouping, unless you know of a second cluster.
It is reversible; the third option is not.

---

## Q4 — One organisation, or several kept apart? *(blocks §7, and the schema)*

**The question:** will a single deployment hold data for organisations that must
not see each other at all — or is it one organisation (a state government) with
internal divisions?

| Answer | What it commits you to |
|---|---|
| **Single tenant** | Simpler everywhere. Separation between departments is handled by the scoping model in Q7, not by a tenant boundary. |
| **Multi-tenant** | Every table in §7 carries an owner column, every query filters on it, and admin roles become per-tenant. Cheap to build in now, expensive to retrofit. |

**Watch out:** this overlaps Q7. "Health department must not see Revenue
department's data" sounds like multi-tenancy but is more likely a *scope
dimension* — the same mechanism that stops a Taluka officer seeing another
Taluka. True multi-tenancy is for when two organisations share an installation
and neither should know the other exists.

**My recommendation:** single tenant, with department as a scope dimension
(see Q7). Say so explicitly rather than by omission, because the schema is being
designed now.

---

## Q5 — Who are the users, and how do they get in? *(blocks §7 entirely)*

The prototype has no login at all. This is the largest gap between prototype and
product, and it is five questions, not one.

| # | Question | Why it matters |
|---|---|---|
| **5a** | Fully standalone accounts, or must it integrate with an existing directory (LDAP / AD / departmental SSO)? | Decides whether we build password management, reset and lockout, or delegate all of it. This is the big one. |
| **5b** | Is MFA or OTP required? Aadhaar OTP was a requirement on the predecessor product. | Adds a delivery channel and a whole verification flow. |
| **5c** | Who creates the first SuperAdmin, and how? | Bootstrapping is a real step, not a detail — someone must exist before anyone can be granted anything. |
| **5d** | Concurrent sessions, idle timeout, forced logout? | Usually mandated in government deployments; cheap if known up front. |
| **5e** | Deactivate versus delete a user — and what happens to their audit history and saved queries? | Deleting a user who appears throughout the audit log either orphans it or destroys it. Both are bad; deactivation is usually the answer. |

**My recommendation:** if there is any existing departmental identity provider,
integrate rather than build. In-house user management was the instruction, and
that still holds for *roles and scoping* — but authentication is the part worth
delegating if a directory exists.

**What I need from you:** 5a and 5b at minimum. The rest can follow.

---

## Q6 — Row limits *(blocks nothing)*

The prototype shows six rows per table. The engine inherited from SRSE stops
rendering the grid past **10,000 rows**, stops reading the stream at **200,000**,
and offers the complete result as a streamed CSV that is never a
re-serialisation of what the screen held.

**The question:** are those the right numbers for these users?

**My recommendation:** keep them. They were arrived at against real data at
crore scale, and the CSV path means nothing is actually lost at any size. Revisit
only if officers complain.

---

## Q7 — Is "department" a second dimension, or a level in the geography? *(blocks §7)*

Already asked as A16, still open. You said an officer can hold multiple roles —
"district / taluka / dept". Geography is a tree: District → Taluka →
Village/City. **Department is not a level in that tree.** An officer can
plausibly be "Health Department, Jaipur District", which is a position on two
independent axes.

| Answer | What it commits you to |
|---|---|
| **Orthogonal dimensions** (assumed) | A deployment defines N dimensions, each its own tree. A user holds assignments in each. The predicate is AND across dimensions, OR within one. |
| **One tree** | Every department sits under a geography node, or vice versa. Simpler, but it cannot express a state-level Health officer, and departments would be duplicated under every district. |

**My recommendation:** orthogonal. It is what the design already assumes, and
retrofitting a second axis into a single tree means rewriting every scope
predicate and every assignment row.

**What I need from you:** confirm, or correct.

---

## Summary — what I need, in order

1. **Q1** — does the client require DB2, or can we choose? *(recommend PostgreSQL)*
2. **Q5a / Q5b** — existing directory to integrate with? MFA/OTP required?
3. **Q7** — department orthogonal to geography? *(recommend yes — confirm)*
4. **Q4** — single tenant? *(recommend yes — confirm)*
5. **Q3** — is "Source System" just a label? *(recommend yes, unless a second cluster exists)*
6. **Q2, Q6** — safe to leave as they are *(PrestoDB only; keep the measured limits)*
