# Forked from SRSE

This repository began as a copy of **SRSE** (Scheme Rule Simulation Engine), the
welfare-scheme simulator built for the Government of Rajasthan. Full history is
preserved — every commit, and the reasoning in `CLAUDE.md`, came with it.

Fork point: SRSE tag `srse-pre-fork` (commit `ad4efb4`).
SRSE continues independently at https://github.com/khannas43/SRSE and is
unaffected by anything here.

## Why fork rather than start fresh

About 85% of SRSE's backend is a general-purpose lakehouse analysis engine with
no welfare-scheme assumptions in it: the record-match engine, the lakehouse
registry and browsing, the SQL compiler, type coercion, the fan-out guard,
RBAC, connection management. Rebuilding that would have taken months, and much
of its value is in defects already found and fixed against a real Presto —
several of which no unit test could have caught.

## What was removed

| Package | Why |
|---|---|
| `decision/` | Rule preview, cohort drill-down, scenario endpoints — beneficiary-specific |
| `scenario/` | Saved rulesets and scenario comparison |
| `scheme/` | Welfare schemes and their official-criteria templates |
| `execution/ExecutionService`, `BreakdownRow` | Beneficiary counting and the fixed district/gender/age-band breakdown |
| `metadata/FieldCatalog*`, `FieldColumnMapping*`, `MetadataFieldResolver`, `FieldTier`, `FieldDataType` | The flat field catalogue and its per-environment bindings |
| seed YAMLs | Field catalogue and scheme template seeds |

Nothing is lost: all of it is in this repository's history (`git show
srse-pre-fork:<path>`) and still live in SRSE.

## What was kept

`analysis/` · `lakehouse/` · `compiler/` · `config/` · `security/` ·
`web/` (connections) · `metadata/AnalysisColumnMetadata*` ·
`execution/GuardrailProperties` — roughly 7,200 lines.

## Known TODOs from the fork

1. **Restore the admin config backup.** `AdminConfigService`/`Controller`/`Bundle`
   were removed rather than trimmed: they bundled field mappings and schemes
   alongside registrations and column metadata, and a half-edited import path
   silently loses admin config on restore. Reinstate a version carrying only
   connections, lakehouse registrations and analysis column metadata.
2. ~~**Restore the RBAC and controller tests.**~~ Done (AA-01): six `@WebMvcTest`
   slices restored; the fork had dropped the global `@RestControllerAdvice` —
   `ApiExceptionHandler` in `config/` replaces the deleted
   `DecisionExceptionHandler`.
3. **Decide the age filter.** The Analysis match still has SRSE's age filter,
   which resolved `age_years` through the field catalogue. `StubFieldResolver`
   now fails every key loudly so the app starts. Either drop the age filter or
   back it with whatever field concept this product adopts.
4. **Rewrite `CLAUDE.md`.** It still describes SRSE. Its locked decisions about
   lakehouse addressing, injection safety, fuzzy blocking and the guardrails
   still apply; those about the flat catalogue, the DMN seam and scheme
   templates do not.
5. **Rename the package root** (`gov.rajasthan.smart.srse`) once the two repos
   stop sharing fixes. Deferred deliberately — renaming now makes it harder to
   copy fixes across while both are moving.
6. **Frontend still carries SRSE screens** — the Rule Engine page, scheme
   panels and the field-mapping editor call endpoints that no longer exist.

## Target

`docs/Analytics_Advisor.html` is the UX prototype this product is being built
toward — a no-code query platform. It is a mockup: all data is hardcoded and all
logic runs in the browser.
