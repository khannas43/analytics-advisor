# Brief AA-02 — Strip the SRSE screens from the frontend

**Repo:** `analytics-advisor`  ·  **Closes:** `FORK.md` TODO 6  ·  **Follows:** AA-01 (`afd8482`)

Backend endpoints `/api/decision/**`, `/api/schemes/**`, `/api/metadata/fields/**`,
`/api/metadata/mappings/**` and `/api/admin/config/**` no longer exist. The frontend
still calls all of them. Remove the screens that do, keep everything Analysis and
lakehouse-admin needs.

**Run every command from `frontend/`.** Build/lint/test: `npm run build`, `npm run lint`, `npm run test`.

## Ground rules

- Delete code, do not comment it out.
- Do **not** touch `src/app/analysis/page.tsx`, `AnalysisResultsGrid`, `MultiTargetJoinCanvas`,
  `ComparisonPairsEditor`, `LakehouseCascade`, `analysisApi.ts`, `analysisCriterionModel.ts`,
  `multiTargetMatchBuild.ts`, `multiTargetProgress.ts`, `joinCanvasModel.ts` or either `.test.ts`.
  They are the product.
- `ChartsSection.tsx` and `VennDiagram.tsx` are **shared and stay**. `ChartsSection` is imported by
  both the dying `ResultsPanel` and the surviving `AnalysisResultsGrid`, and it imports nothing from
  `decisionApi`. Leave both files unmodified.
- `MultiSelectDropdown.tsx` stays — used by the admin page and `AnalysisResultsGrid`.
- If removing something breaks a file you were told to keep, stop and report rather than editing
  the kept file to compensate.

## Part 1 — Delete whole files

Every one of these is reachable only from the Rule Engine or the field catalogue.

```
git rm src/app/rules/page.tsx \
       src/components/RuleGroupEditor.tsx \
       src/components/ResultsPanel.tsx \
       src/store/ruleBuilderStore.ts \
       src/lib/adminFieldValidation.ts
```

`ResultsPanel` is imported only by `src/app/rules/page.tsx`; `ruleBuilderStore` only by
`rules/page` and `RuleGroupEditor`; `adminFieldValidation` only by the admin field-catalogue
section removed in Part 3. Remove `src/store/` entirely if it ends up empty.

## Part 2 — Split `src/lib/decisionApi.ts` (690 lines)

It is the only file holding dead endpoints, but it also holds live ones. **Do not delete it
wholesale.** Create `src/lib/adminApi.ts` carrying over only the live surface, then
`git rm src/lib/decisionApi.ts`.

**Carry over** (and the types they return):

| Endpoint | Purpose |
|---|---|
| `GET /api/admin/connections` | Connection status cards |
| `PUT /api/admin/connections/${plane}` | Connection editing |
| `GET /api/admin/lakehouse/browse/catalogs` (+ schemas/tables/columns cascade) | Admin discovery |
| `GET /api/admin/lakehouse/layers` | Layer tags |
| `GET|POST|PUT|DELETE /api/admin/lakehouse/registrations[/${id}]` | Registry |

**Drop**: every `/api/decision/*`, `/api/metadata/fields*`, `/api/metadata/mappings*`,
`/api/schemes*`, and `/api/admin/config/export|import`. Drop the types used only by those
(rule AST, scenario, scheme, field-catalogue, mapping, breakdown).

Keep the `authToken` import and whatever shared fetch/error helper the file defines — the admin
page depends on the same error shape.

Repoint the two remaining importers: `src/lib/environmentLabels.ts` and `src/app/admin456/page.tsx`.

Name it `adminApi.ts`, not `decisionApi.ts` — "decision" is SRSE's concept and the product no
longer has it.

## Part 3 — Surgery on `src/app/admin456/page.tsx` (2,322 lines)

The file is already decomposed into named top-level functions, so this is removal by name, not
by line range. Verify each one's call sites before deleting.

**Delete these functions and everything only they use:**

```
isPlaceholderMapping        isUnconfiguredMapping
FieldHelpButton             AddFieldForm              FieldCatalogRowEditor
buildDobAgeExpression       parseDobAgeExpression
ColumnPickerForMapping      MappingRowEditor          MappingsPanel
SchemeOfficialCriteriaPanel ConfigBackupPanel
```

Also delete the module-level constants used only by them (`TIER_FIELD_HELP`,
`ALLOWED_VALUES_FIELD_HELP`, and any tier / data-type / mapping option lists).

**Keep:** `StatusBadge`, `ConfirmDeleteButton`, `qualified`, `errorMessage`,
`registerButtonLabel`, `ConnectionCard`, `ConnectionsPanel`, `AnalysisGuardrailsPanel`,
`CompareAsSelect`, `COMPARE_AS_OPTIONS`, `ColumnMetadataRowEditor`,
`RegisterColumnMetadataForm`, `ColumnMetadataPanel`, `RegistrationRow`, `LakehouseRegistryPanel`.

**In `AdminPage`**, remove `<ConfigBackupPanel …/>`, `<SchemeOfficialCriteriaPanel />` and
`<MappingsPanel …/>` from the render. Leave the shared `registrations` / `columnMetadata` state
and the `refresh` callback alone — the comment above them explains why the two lists must refresh
together, and that reasoning still holds for the panels that remain.

Rewrite the page description, which currently says "register the lakehouse tables SRSE may use,
and manage which physical column each abstract field resolves to, per environment." The
per-environment field mapping is gone.

`ConfigBackupPanel` returns with `FORK.md` TODO 1, when the backend backup is reinstated carrying
only connections, registrations and column metadata. Do not try to keep it working now — its
endpoints are gone.

## Part 4 — Navigation and the root route

`src/components/AppHeader.tsx` hardcodes SRSE branding and links to `/rules`, which will 404
after Part 1. It is the only file outside `app/rules/` that references that route.

- Drop the `Rule Engine` tab; `Analysis` remains. Add an `Admin` tab pointing at `/admin456`
  **only if** you can confirm the page already guards itself for non-admins — otherwise leave
  the admin route unlinked as it is today and say so in your report.
- Change the brand mark from `SRSE` / "Scheme Rule Simulation Engine" to
  `Analytics Advisor`, and point the brand `Link` at `/analysis`.

**There is no `src/app/page.tsx`** — `/` currently has no route and the brand link papered over
it. Add one that redirects to `/analysis`:

```tsx
import { redirect } from "next/navigation";
export default function Home() { redirect("/analysis"); }
```

Leave the `srse-*` CSS class names in `globals.css` alone. Renaming them is a separate job and
touching them here would collide with every file in this brief.

## Acceptance

- `npm run build` clean — this is the real check, since it catches every dangling import.
- `npm run lint` clean.
- `npm run test` green (the two vitest files must still pass untouched).
- `grep -rn "/api/decision\|/api/schemes\|/api/metadata/fields\|/api/metadata/mappings\|/api/admin/config" src` returns **nothing**.
- `grep -rln "decisionApi\|ruleBuilderStore" src` returns **nothing**.
- Report the line count removed and the final `wc -l` across `src`.
- Report anything you kept that the brief said to delete, with the call site that forced it.

## Manual check before you report done

With the backend running, load `/analysis` and `/admin456` and confirm: the Analysis tab still
runs a match end to end, and the admin page still shows connection status, lakehouse registrations
and column settings. A page that renders but whose panels error on load is not done.
