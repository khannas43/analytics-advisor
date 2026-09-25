# Brief AA-15 — Source-system labels and the Database Overview page

**Repo:** `analytics-advisor` · **Covers:** `PRODUCT_PLAN.md` 3.1.1–3.1.4
**Follows:** AA-14b (`c2c3b6e`)

## Scope

Give registered tables two more display labels — **source system** and **table
group** — and build the **Database Overview**: a browsable picture of what an
officer can actually query, down to columns and their data types.

3.1.5 (column business names, hide, fuzzy flag, comparison mode) already exists
and is untouched.

## Part 0 — Labels are tags, not addresses

Decision 0.3: *Source System is a display label on registered tables, exactly
like `layer`.* That word "exactly" is doing real work, and `CLAUDE.md` already
states the rule for `layer`:

> Layer never enters a request payload, a validation gate, or emitted SQL.

The same holds for both new labels. The address is and stays
`catalog.schema.table`. A label filters what an officer is *shown*; it never
decides what they may *reach*, and it never appears in a query.

This matters because it is tempting to let "source system" become a security
boundary — it looks like one. It is not. Reach is decided by registration, the
scope bindings, and the two gates, all of which already exist. A label that
quietly became an access control would be a control nobody tested.

Follow `layer`'s existing shape: two nullable `varchar` columns on
`registered_table`, a `listSourceSystems()` / `listTableGroups()` returning
distinct values, and the same `UNTAGGED` sentinel treatment for rows that have
none. Vendor-neutral Liquibase.

## Part 1 — The vocabulary (3.1.2)

Derive the vocabulary from the registrations themselves, as `listLayers()`
already does, rather than adding a vocabulary table to keep in sync.

- The admin registration form offers existing values as autocomplete and accepts
  a new one. Free text with suggestions stops most typos without a second table
  drifting out of step with the first.
- Add a **bulk rename**: changing "Jan Aadhaar" to "Jan Aadhaar (Txn)" should
  update every registration carrying it, not leave two labels that look like one.
  This is the thing a derived vocabulary cannot do by itself, and without it the
  list silently accumulates near-duplicates.
- Renaming is an admin action and therefore **audited** — it changes what
  officers see.

If you find you want a managed vocabulary table instead, say so before building
it; it is a defensible choice, just a larger one.

## Part 2 — The Database Overview page (3.1.4)

A read-only page: source system → table group → table → columns with data types.

**It must show only what the officer could actually query.** This is a new read
surface over the registry, and AA-08 Part 3 lists the eight officer-facing
methods that apply deny-by-default. Go through those — do not add a ninth path
that reads `registered_table` directly. A table invisible to an officer in the
Analysis cascade must be invisible here too, and an overview is exactly where
someone would notice a discrepancy.

For each table show its labels, its layer, whether it is shared reference data,
and its columns with live data types and business names.

**Load columns lazily, when a table is expanded.** Column lists come from live
introspection, one call per table. Eagerly loading every table's columns turns a
page visit into a hundred round trips to Presto — the same mistake the fan-out
guard exists to prevent elsewhere, in a smaller costume.

**Do not audit browsing here.** Decision A7 excludes metadata browsing
deliberately, and this page is metadata browsing. `CLAUDE.md` and the cascade
already carry that note; add it here too, because an overview page is precisely
where someone will think "surely we should log this".

## Part 3 — The officer cascade (3.1.3)

The Analysis and Extract cascades currently lead with Layer as a registry
filter. Add source system and table group as further filters of the same kind —
optional, narrowing, and applied to the registry rather than the address.

The submitted request is unchanged: still `catalog.schema.table`. If a label
reaches a request payload, something has gone wrong.

## Part 4 — Tests

- A table invisible to a scoped officer is absent from the overview, and from
  the label lists that lead to it — if every table in a source system is hidden,
  the source system should not appear either. The same reasoning as AA-08's
  empty-catalog rule: an empty group announces that something is being hidden.
- Labels never appear in emitted SQL. Assert it against a planned match.
- `UNTAGGED` behaves as it does for layer — a filter value, never a stored tag.
- Bulk rename updates every affected registration and writes one audit row.
- Browsing the overview writes **no** audit rows.
- Registering with a new label makes it appear in the vocabulary; unregistering
  the last table carrying it makes it disappear.

## Acceptance

- `mvn -f backend/pom.xml test` green on **JDK 17**. Report the count — **478**.
- `npm run build` / `npm run test` clean.
- Live, against the dev fixtures: label `iceberg.srse.beneficiary` with a source
  system, then confirm the overview shows it for `superadmin` and — with the
  table's scope binding removed — **not** for `sanganeruser`, whose taluka scope
  makes it invisible. Report both.

## Do not

- Do not let a label into a request payload, a validation gate, or SQL.
- Do not read `registered_table` directly for the overview — use the
  scope-filtered registry methods.
- Do not eagerly load every table's columns.
- Do not audit metadata browsing.
