# Brief AA-12 — Audit log viewer, access control and export

**Repo:** `analytics-advisor` · **Covers:** `PRODUCT_PLAN.md` 7.3.2, 7.3.4, 7.3.5, 7.3.7, 7.3.8
**Follows:** AA-11 (`6bc6694`)

## Scope

Make the audit log readable — by the right people, showing only the entries they
are entitled to, at a volume that stays fast.

This completes §7.3 and, with it, §7 apart from the deferred SMS sender and the
idle timeout (7.1d.3).

## Part 0 — Who may read it

7.3.4 calls this "an RBAC permission", and the role model already has the shape
for it. Add **`AUDIT_READER`** to `AppRole`, mapped in `RoleAuthorityMapper` to a
new **`AUDIT_READ`** authority, and gate `/api/admin/audit/**` on it in
`SecurityConfig`.

- `SUPER_ADMIN` gets `AUDIT_READ` implicitly.
- `ADMIN` does **not**. Administering users and reading who looked at what are
  different jobs, and an audit log the administrators can quietly read is a
  weaker control than one they must be granted.
- Granting `AUDIT_READER` follows AA-07's existing rules without exception —
  subtree only, and **nobody grants it to themselves** (rule 4 already forbids
  editing your own roles, which is exactly the protection needed here).
- Granting or revoking it is itself an audited admin action.

## Part 1 — What a reader may see (7.3.8, A10)

An audit reader sees entries whose **actor** is inside their own scope subtree.

**Reuse AA-07's containment predicate. Do not write a second one.** The rule —
an actor qualifies only if *every* assignment they hold is inside the reader's
subtree — is the one that had the vacuous-truth defect fixed in AA-07
(`b08c4a3`): a user with **no** assignments satisfied "every assignment is
inside my subtree" trivially, which made the unassigned SuperAdmin visible and
manageable to any district admin. Extract the shared predicate rather than
copying it, or that bug comes back here and this time it leaks the SuperAdmin's
activity to every district.

Three cases need deciding explicitly, because the obvious implementation gets
them wrong:

| Entry | Who sees it | Why |
|---|---|---|
| Actor is inside the reader's subtree | The reader | The ordinary case |
| **Actor cannot be resolved** — `LOGIN_FAILED` for a username that does not exist, or an unauthenticated request | **SuperAdmin only** | These are the entries an intrusion actually produces. Showing them to scoped readers leaks attempted usernames; hiding them from everyone loses the most interesting rows in the table. |
| **Archived** — actor is a deactivated user (Q5) | **SuperAdmin only** | A deactivated user may have sat outside the reader's subtree, and an archive must not grant visibility nobody had while they were active |

`SUPER_ADMIN` sees everything, consistent with D3.

## Part 2 — Storage and indexing (7.3.2)

At the settled A7 event set this is roughly 2.5 million rows a year, so the
concern is query shape, not size.

- Indexes for the access patterns the viewer actually uses: `occurred_at DESC`,
  `(actor_user_id, occurred_at)`, `(action_type, occurred_at)`. The table has
  only a primary key today.
- **Paging is server-side and bounded.** Never "fetch all, filter in the
  browser" — that is the mistake the Analysis grid already had to be rescued
  from at scale.
- Retention: the setting exists and defaults to never-purge (A8). **Do not build
  a purge job in this brief**; just make sure the setting is read and reported so
  nobody assumes deletion is happening.
- Vendor-neutral Liquibase, as always.

## Part 3 — The viewer (7.3.4)

`GET /api/admin/audit` with server-side paging and filters on date range, actor,
action type and outcome.

The UI follows the existing admin page's conventions. It needs to answer the
questions someone actually arrives with:

- *What did this officer do last Tuesday?* — filter by actor and date.
- *Who exported anything this month?* — filter by action type.
- *What was refused, and to whom?* — filter by outcome.

Show `query_shape` as-is. It carries placeholders and no values by construction
(A6, verified in AA-11), so it is safe to display — but **do not add anything
that re-inflates it with parameters**, which is the same trap
`renderQueryForDisplay` represents on the write side.

## Part 4 — Export (7.3.5)

`GET /api/admin/audit.csv`, streamed, honouring the same filters and the same
scope rules as the viewer.

**The export is itself audited**, and under Q6 an export whose audit row cannot
be written is refused. So: write and commit the `EXPORT` row **before the first
byte**, exactly as AA-11 does for the analysis CSV. One row per export — this
does not recurse, but say so in a comment, because it reads as though it might.

The exported file must carry only what the reader was entitled to see. Run the
export through the same scoped query as the viewer rather than a parallel one;
two query paths is how the filtered and unfiltered versions drift apart.

## Part 5 — Tests

- A scoped reader sees an in-subtree actor's entries and **not** an
  out-of-subtree actor's.
- **An actor with no assignments is not visible to a scoped reader** — the
  AA-07 regression, now in this surface.
- Unresolvable-actor entries: SuperAdmin yes, scoped reader no.
- Deactivated actor's entries: SuperAdmin yes, scoped reader no.
- `ADMIN` without `AUDIT_READER` gets 403 on every audit endpoint.
- An audit reader cannot grant themselves `AUDIT_READER`.
- The CSV contains exactly the rows the viewer would show for that reader —
  assert it, do not assume the shared path.
- Granting `AUDIT_READER` writes an audit row.

## Acceptance

- `mvn -f backend/pom.xml test` green on **JDK 17**. Report the count — **449**.
- `npm run build` / `npm run test` clean.
- Live against PostgreSQL, using the existing fixtures: grant `AUDIT_READER` to
  `jaipuradmin`, have `jaipurofficer` and `superadmin` each run a match, then
  confirm `jaipuradmin` sees the officer's entries and **not** the
  SuperAdmin's. Then export and confirm the file matches.
- Report which of Part 1's three cases you covered.

## Do not

- Do not write a second containment predicate — share AA-07's.
- Do not give `ADMIN` audit read implicitly.
- Do not re-inflate `query_shape` with bound values anywhere in the read path.
- Do not build a purge job.
