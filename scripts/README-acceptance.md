# Acceptance scripts

Project-maintained browser acceptance lives in this directory. Playwright is a **devDependency of `scripts/package.json`** (not the frontend app).

```bash
cd scripts && npm install
cd .. && AA_ADMIN_PASS='…' node scripts/aa_final_acceptance.mjs
```

Node resolves `playwright` from `scripts/node_modules` when running `scripts/*.mjs`.

Environment:

- `AA_ADMIN_PASS` — required (never commit passwords).
- `AA_ADMIN_USER` — optional (default `superadmin`).
- `AA_SAVED_QUERY_CASE` — run one saved-query case (e.g. `dual-join-left`).
- `AA_ACCEPTANCE_SAVED_QUERY_ONLY=1` — saved-query suite only.

A copy may also be executed from `/tmp/aa_final_acceptance.mjs`; keep it in sync with `scripts/aa_final_acceptance.mjs` or run the tracked path above.
