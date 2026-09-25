# Brief AA-08 — Scope bindings and deny-by-default

**Repo:** `analytics-advisor` · **Covers:** `PRODUCT_PLAN.md` 7.2.1, 7.2.2, 7.2.2a
**Follows:** AA-07 (`b08c4a3`)

## Scope

The metadata that says *which column of which table carries which hierarchy
level*, the admin UI to maintain it, and the rule that a table nobody has bound
is invisible to a scoped user.

**No query is filtered in this brief.** Injecting the predicate is 7.2.3 (the
next one). Build the model and the visibility rule; leave the match engine
alone.

Read `PRODUCT_PLAN.md` §0 and §7.2 first, and AA-06/AA-07 for the model this
sits on.

## Part 0 — Decisions to build to

**D1 — A binding says: "this column holds the `code` of a scope node at level L."**
So `beneficiary.district_code` holds values matching `scope_node.code` where that
node is at the District level of the Geography dimension. The predicate 7.2.3
emits will then be an `IN` list of codes.

**D2 — A table may bind several levels in one dimension**, and should
(decision A17). If `beneficiary` carries both `district_code` and
`village_code`, bind both. 7.2.3 picks the **coarsest** binding that covers the
user's scope, so a district officer gets `district_code = ?` with one value
instead of thousands of village codes.

**D3 — SuperAdmin bypasses data scoping entirely.** A SuperAdmin can grant
themselves any scope in two clicks, so filtering them is theatre that costs
query complexity. Everyone else is filtered by their assignments, Admins
included — an Admin's scope governs their *data* exactly as it governs the users
they manage.

**D4 — A user with no assignments at all sees no scoped data.** Consistent with
§7.1 rule 3. They can still see shared reference tables.

**D5 — The visibility rule, which is the heart of this brief.** For a scoped
user, a registered table is visible only if, for **every dimension that user is
assigned in**, one of these holds:

- the table has a **binding** for a level in that dimension, or
- the table is explicitly marked **not applicable** to that dimension, or
- the table is flagged **shared reference data** (A4), which exempts it from all
  dimensions at once.

Otherwise it is invisible. No binding and no flag means invisible — never
"visible and unfiltered".

The per-dimension exemption exists because most tables will not carry a
department column, and without it a department-scoped officer would see almost
nothing. But it must be an **explicit admin act**, not a default, or the
department axis silently stops being enforced wherever a column happens to be
missing — which is precisely the silent widening decision A4 exists to prevent.

## Part 1 — Schema

Vendor-neutral Liquibase, as AA-05 established. No JSONB, no dialect DDL.

```
table_scope_binding        id, registered_table_id, scope_level_id, column_name
                           unique (registered_table_id, scope_level_id)

table_dimension_exemption  registered_table_id, scope_dimension_id
                           primary key on both

registered_table           + shared_reference boolean not null default false
```

- **Validate the column exists** when a binding is saved, through
  `LakehouseRegistryService.validateColumn` — the same two gates everything else
  uses. A binding naming a dropped column compiles into a broken query later.
- Deleting a scope level or dimension with bindings must be **refused, naming
  what blocks it**, as AA-07 does for nodes.
- Unregistering a table should remove its bindings and exemptions; that is a
  deliberate admin act on the table itself.

## Part 2 — Resolution service

A service that answers, for a given user and table:

1. **Is it visible?** (D5)
2. **Which binding applies per dimension?** — the coarsest level whose depth is
   at or above every node the user holds in that dimension (D2/A17).

7.2.3 consumes this; nothing else does yet. **Write it as a pure, testable
function of (assignments, bindings, exemptions, shared flag)** rather than
something that reaches into a request context, so it can be tested exhaustively
without a web layer. Both defects found in AA-06 and AA-07 were in exactly this
kind of predicate, and both were invisible to the tests that existed.

Test at minimum: a table bound in one dimension but not the user's second;
a table exempt from that second dimension; a shared reference table; a user with
no assignments; a user holding two nodes at different depths in one dimension
(the coarsest must still cover both); and a table with no bindings at all.

## Part 3 — Deny-by-default across the officer surface (7.2.2a)

This is the part that must not be half-done. **Every** officer-facing read in
`LakehouseRegistryService` has to apply D5:

| Method | What must change |
|---|---|
| `listTables(catalog, schema[, layer])` | Drop invisible tables |
| `listSchemas(catalog[, layer])` | Drop schemas left with no visible table |
| `listCatalogs([layer])` | Drop catalogs left with no visible table |
| `listLayers()` | Drop layers left with no visible table |
| `listColumns(catalog, schema, table)` | Refuse for an invisible table |
| `validateRegistered(...)` (both overloads) | Refuse for an invisible table |
| `validateColumn` / `validateColumns` | Refuse for an invisible table |
| `hasColumns` | Must not report on an invisible table |

The cascade rungs above tables matter as much as the tables. A catalog left
visible but empty tells an officer that a catalog exists and that something in
it is hidden from them, which is a small leak and an obvious support call.

`LakehouseBrowseService` is **admin-only** and stays unfiltered — it is
discovery against the live cluster, not officer-facing. Do not touch it.

The validate gates are the important half: hiding a table from a listing while
`validateColumn` still accepts it means an officer who guesses or replays a name
reaches it anyway.

## Part 4 — Admin UI

Extend the existing admin page rather than adding a screen elsewhere.

- Per registered table: its bindings (level → column), its dimension
  exemptions, and the shared-reference flag.
- The column picker offers the table's **live** columns, as the rest of the
  admin page does.
- **Show the consequence.** A table with no binding and no exemption for a
  dimension is invisible to everyone scoped in it — say so on screen, because
  otherwise "my table disappeared" is the first support ticket.
- Only a **SuperAdmin** may set `shared_reference`. It disables scoping for that
  table entirely, which is not a district admin's call.

## Part 5 — Tests

- Every case listed in Part 2.
- Every row of Part 3's table, as a test that a scoped user cannot see or
  validate an invisible table through that method.
- A scoped user and a SuperAdmin against the same registry, asserting the
  SuperAdmin sees everything (D3).
- Liquibase applies to empty PostgreSQL; extend `OperationalStoreLiquibaseIT`.

## Acceptance

- `mvn -f backend/pom.xml test` green on **JDK 17**. Report the count — **394**.
- `npm run build` and `npm run test` clean.
- Live, against PostgreSQL in `local` mode, using the fixtures already in the
  dev database (`jaipuradmin`, `sanganeruser`, the Geography hierarchy): with
  `iceberg.srse.beneficiary` registered but **unbound**, a scoped officer must
  see it in **none** of the cascade calls and must be refused by
  `validateColumn`. Bind `district_code` and it appears. Flag it shared
  reference and it appears for a user with no assignments at all.
- Report which Part 3 methods you covered, and any you could not.

## Do not

- Do not filter query results yet — 7.2.3.
- Do not touch `LakehouseBrowseService`, `analysis/`, or `compiler/`.
- Do not make "no binding" mean "visible and unfiltered", under any
  circumstance, however convenient it makes the demo.
