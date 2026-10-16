# DEMO-02 scripts

Idempotent Admin API setup and the 13-scenario runner. No extra Python
packages. Contract: `docs/briefs/DEMO-00-contract.md`.

| Script | What it does |
|---|---|
| `./scripts/demo/setup.sh` | Data source, registrations, column metadata, scope tree, users |
| `./scripts/demo/setup.sh --reset` | Removes only objects this script created |
| `./scripts/demo/run-scenarios.sh` | Runs scenarios except **2 and 3** |
| `./scripts/demo/run-scenarios.sh --include-fuzzy` | Full suite including 2/3 (fuzzy name ≥85% + exact mobile). 2b skipped. |
| `./scripts/demo/run-scenarios.sh --only 1 4` | Subset |

Copy `demo.env.example` to `demo.env` or the repo-root `.env`. Timeouts:
`TIMEOUTS.md`. Presenter path: `docs/demo/DEMO_SCRIPT.md`.
