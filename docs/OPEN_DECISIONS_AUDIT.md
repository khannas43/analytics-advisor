# Audit log — questions to answer

> **Q1 and Q2 answered 2026-09-25.** Log everything except metadata browsing;
> keep everything. Recorded in `PRODUCT_PLAN.md` §7.3.
>
> **Still open: Q3 (tamper-evidence), Q4 (sessions), Q5 (archived log
> readership), Q6 (does a failed audit write fail the action).** Q3 is the one to raise with DoIT&C, and the one that must be
> settled before 7.3.2 is built.

`PRODUCT_PLAN.md` §7.3 is the last blocked section. Five questions, each with
what the answer commits you to and a recommendation.

Already settled, for context: the log stores the query **shape** with
placeholders and never the bound values (A6), so no Aadhaar numbers or names
land in it; and log access is **scoped to the reading officer's own subtree**
(A10).

**Q1 and Q2 are the ones that shape the build.** Q3 is a compliance question
someone outside the team may have to answer. Q4 and Q5 are small.

---

## Q1 — What counts as "accessed"? *(decides volume by an order of magnitude)*

Everything else in §7.3 scales from this answer — storage, indexing, retention,
and whether the log viewer needs paging that works at a hundred million rows.

| Candidate event | Per officer per day, roughly | Worth logging? |
|---|---|---|
| **Login, logout, failed login, lockout** | tens | Yes — cheap, and the first thing any investigation asks for |
| **A query that returns data** (match, multi-match, comparison summary) | tens to hundreds | Yes — this is the thing the log exists for |
| **An export** (CSV download) | a few | Yes, emphatically — this is data leaving the building |
| **A query that was planned but not run** (SQL preview, refused fan-out) | hundreds | Probably — a refused query shows intent, and preview is one click from execution |
| **Browsing metadata** (cascade calls: catalogs, schemas, tables, columns) | hundreds to thousands | **Recommend no** — see below |
| **Admin actions** (create user, grant scope, change binding, reset password) | a handful | Yes — these change who can see what |

**My recommendation:** log the first four plus admin actions; do **not** log
metadata browsing.

The cascade fires on every dropdown interaction, so it would dominate the table
by an order of magnitude while recording that someone opened a list of table
names — which the registry already decides they are allowed to see. It buries
the entries that matter. If you want browse coverage, a daily "officer X browsed
the registry" rollup gives the same assurance for a thousandth of the rows.

**The one I would not compromise on is exports.** A CSV download is the moment
data leaves the system, and it is the entry an investigation actually needs.

---

## Q2 — Retention and volume *(decides the storage design)*

Follows directly from Q1. At roughly 200 logged events per officer per day and
50 officers, that is ~2.5 million rows a year — small. Include metadata browsing
and it is 50–100 million, which is a different engineering problem.

**The question:** how long must entries be kept, and what happens at the end?

| Answer | What it commits you to |
|---|---|
| **Keep everything indefinitely** | Simplest. Fine at the volume my Q1 recommendation implies; not fine if browsing is logged. |
| **Retain N years, then delete** | Needs a documented, tested purge job. Someone must own the number. |
| **Retain N years hot, then archive** | Also needs somewhere to archive *to*, and a way to read it back when asked. |

**My recommendation:** keep everything, with a retention *setting* built in from
the start even if it is set to "never purge". Retrofitting deletion into a table
that is append-only by design is much harder than switching it on later.

**Worth knowing:** government record-retention rules often mandate a minimum
(commonly 5–7 years) rather than a maximum. If such a rule applies here, it sets
the floor and Q3 probably answers itself.

---

## Q3 — Is tamper-evidence required? *(compliance, not engineering preference)*

Can an administrator with database access alter or delete a log entry without it
being detectable?

| Answer | What it commits you to |
|---|---|
| **An ordinary table** | Nothing extra. An admin with database access can edit history, and nobody would know. |
| **Append-only at the application layer** | No UPDATE or DELETE path in code. Cheap, and stops accidents and casual misuse — but not someone with direct database access. |
| **Tamper-evident (hash chain)** | Each row carries a hash of itself and its predecessor, so any alteration breaks the chain and a verification job finds it. Perhaps 3–5 days, plus the job and a procedure for what to do when it fails. |
| **Write-once external store** | Strongest and the largest change: the log stops living in the operational database. |

**My recommendation:** application-layer append-only now — no update or delete
path, ever — and a hash chain **only if a rule requires it**. The chain is not
expensive, but it is pointless without someone actually running and acting on
the verification.

**This is the one to ask outside the team.** DoIT&C or the department's audit
function will have a position, and it is much cheaper to build in now than to
retrofit onto a populated table. It is listed in the plan with an estimate of
`?` for exactly this reason.

---

## Q4 — Sessions: concurrent logins, idle timeout, forced logout *(A14, small)*

Not strictly audit, but it is the last open §7 question and it affects what the
log records.

- May one account be logged in from two places at once, or does a new login end
  the old session?
- Idle timeout, and absolute session lifetime?
- Must an admin be able to force a user's sessions to end immediately?

**My recommendation:** allow concurrent sessions, 30-minute idle timeout,
8-hour absolute lifetime, and admin forced-logout — which AA-06 already
supports, since deactivation invalidates live sessions through the session
version. Government deployments frequently mandate an idle timeout, so the value
is worth confirming rather than assuming.

---

## Q6 — If the audit write fails, does the action fail? *(new, raised by AA-11)*

An audit row is written on the same request as the action it records. If that
write fails — the operational database is briefly unavailable, a constraint
trips — there are two choices and no third.

| Answer | What it commits you to |
|---|---|
| **Refuse the action** | The log is complete by construction: nothing happens that is not recorded. But the audit table becomes a hard dependency of the whole product, so an operational-database hiccup stops officers querying the lakehouse — which otherwise does not need that database at all. |
| **Proceed and report the failure loudly** | The product keeps working when the log cannot be written. But there is then a window in which actions happened and were not recorded, which is exactly the window an investigation cares about. |

**My recommendation: split it.** Refuse for the events whose whole point is
accountability — admin actions, role and scope grants, password resets, exports.
Proceed-and-alarm for query execution, where refusing converts a logging problem
into an outage on the product's main path.

That is not a fudge: an export that is not recorded is the case the log exists
for, while a match that is not recorded is a gap in a record of intent. They are
not equally bad and they need not be treated the same.

**Worth answering before 7.3.1 lands**, because it decides whether the audit
write sits inside the request transaction or beside it.

---

## Q5 — Who may read an archived log? *(follows 7.1c)*

Settled already: a deactivated user's audit entries are **archived, not
deleted**. Not settled: who can then read them.

A10 scopes log access to the reader's own subtree, but a deactivated user may
have sat somewhere the current reader cannot see — so scoping alone does not
answer it.

**My recommendation:** SuperAdmin only. Otherwise an archive hands a scoped
reader visibility they never had while the user was active, which quietly
inverts A10.

---

## Summary — what I need

1. **Q1** — which events. *(recommend: logins, queries, exports, refusals, admin actions; not metadata browsing)*
2. **Q2** — retention. *(recommend: keep everything, build the setting anyway)*
3. **Q3** — tamper-evidence. *(recommend: append-only now, hash chain only if mandated — **ask DoIT&C**)*
4. **Q4** — sessions. *(recommend: concurrent allowed, 30 min idle, 8 h absolute, admin force-logout)*
5. **Q5** — archived log readership. *(recommend: SuperAdmin only)*
6. **Q6** — does a failed audit write fail the action. *(recommend: refuse for admin actions and exports, proceed-and-alarm for queries)*

Q3 is the only one likely to need someone outside the team, and it is the most
expensive to change later.
