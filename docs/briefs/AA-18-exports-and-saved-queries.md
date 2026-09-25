# Brief AA-18 — Excel, JSON and XML exports, and saved queries

**Repo:** `analytics-advisor` · **Covers:** `PRODUCT_PLAN.md` 6.4, 6.5, 6.6, 6.7
**Follows:** AA-17 (`1fea0f1`)

6.8 (scheduled runs) is deliberately out — it needs a scheduler, a delivery
channel and a decision about whose scope a scheduled run executes under, which
is its own conversation.

## Part 1 — Three more export formats (6.4–6.6)

CSV already does the hard part: it **re-runs the query and streams from Presto
to the file**, so the download is the complete result rather than a
re-serialisation of what the browser was holding. AA-17 confirmed it —
200,000 rows, 4.1 MB, two seconds.

The three new formats ride that path. They are not new pipelines.

- **JSON** (6.5) and **XML** (6.6) must **stream**, not build a document in
  memory. Both formats tempt you into assembling the whole thing first; at
  200,000 rows that is a heap problem, and the whole point of the CSV contract is
  that size does not change the answer. Write the opening token, stream the rows,
  write the closing token.
- **Excel** (6.4) cannot stream as freely — the format is a zip container. Use a
  streaming writer (Apache POI's `SXSSFWorkbook` keeps only a row window in
  memory). **Excel has a hard limit of 1,048,576 rows per sheet**, which is below
  the 200,000-row stream stop but well below what a match can return. Decide and
  state what happens past it: refuse with a clear message naming the limit, or
  spill to further sheets. **Refusing is better** — a silently truncated
  spreadsheet is the same failure class as a silently partial total, and the
  officer already has CSV for the large case.
- Every format is **audited as `EXPORT`**, and under Q6 the row is committed
  **before the first byte**, exactly as CSV does. Four formats, one rule.
- Every format carries the **scope predicate**, because they all go through the
  same planner. Do not add a fourth path that builds its own query.

## Part 2 — Saved queries (6.7)

Save a query by name, reopen it, share it.

### The decision this package actually contains

A saved query stores a **request payload**. Since AA-13 and AA-16 that payload
can contain **officer-typed values**: rule operands, value-filter selections, and
typed fuzzy text — which is very often a person's name.

Decision A6 keeps exactly those values **out of the audit log**, deliberately, so
the log never becomes a second copy of the data. Saving them to a table is a
different question and it needs answering rather than assuming:

**Store them.** A saved query that discards its own parameters is not a saved
query — reopening it would give the officer an empty form. The feature is
unusable without the values.

But say so honestly in the schema and the UI, because it makes `saved_query` a
table that holds personal data when the audit log deliberately does not:

- The table is **scope-aware**: a saved query is owned by its creator and
  readable only by users who could run it. Reuse AA-07's containment predicate —
  do not write a third one.
- **Sharing** (the word in 6.7) means sharing *within* that boundary. An officer
  may share with someone whose scope covers the query's tables; they may not
  share a query whose results the recipient could not have obtained themselves.
  Otherwise a saved query becomes a way to launder scope.
- **Re-validate at run time, never trust the stored payload.** Registrations,
  scope bindings, column visibility and the officer's own assignments all change.
  A query saved when a table was visible must be refused when it no longer is —
  so a saved query is re-planned through the normal path, not replayed.
- Deactivated owners keep their saved queries; an admin may reassign them
  (7.1c.4, already in the plan).

### Mechanics

- `saved_query`: id, owner, name, description, created/updated, the payload.
- The payload is **JSON in a text column** — decision 0.1 forbids JSONB, since
  the schema must run on DB2 as well as PostgreSQL. Store it as `varchar`/`clob`
  and parse in the application.
- **Version the payload shape.** Requests have changed four times in this project
  (rules, grouping, fuzzy options, value filters). A saved query from before a
  change must either still load or fail with a message saying it is too old —
  never load with fields silently dropped.
- Creating, updating, deleting and sharing a saved query are **admin-shaped
  actions on the officer's own data** and should be audited as such.

## Part 3 — Tests

- Each format streams: assert the writer never materialises the full result
  (inject a large row source and assert bounded memory, or at minimum assert the
  streaming API is used).
- Each format writes exactly one `EXPORT` audit row, committed before the body.
- Excel past its row limit refuses with the limit in the message.
- A saved query re-planned after its table is unregistered is **refused**.
- A saved query re-planned by an officer with narrower scope returns **their**
  rows, not the author's.
- Sharing to a user who could not run the query is refused.
- An old payload version loads or fails loudly; it never loads partially.
- Typed fuzzy text survives a save/reopen round trip.

## Acceptance

- `mvn -f backend/pom.xml test` green on **JDK 17**. Report the count — **502**.
- `npm run build` / `npm run test` clean.
- Live: export the same match as CSV, JSON, XML and Excel; confirm identical row
  counts across all four, and four `EXPORT` audit rows.
- Live: save a query as `superadmin`, reopen it as `jaipurofficer`, and confirm
  it returns **Jaipur only** — the author's scope must not travel with the query.

## Do not

- Do not buffer a whole export in memory.
- Do not build a second query path for any format.
- Do not replay a saved payload without re-validating it.
- Do not let a shared query return rows the recipient could not obtain directly.
