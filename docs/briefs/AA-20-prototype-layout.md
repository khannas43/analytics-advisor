# Brief AA-20 — Rebuild the UI to the prototype layout

**Repo:** `analytics-advisor` · **Covers:** `PRODUCT_PLAN.md` 8.1, 8.2, and the
mechanism for 8.3 · **Follows:** AA-19 (`f035363`)

## The instruction

**Follow `docs/Analytics_Advisor.html` strictly.** The current UI is inherited
SRSE chrome — a top nav bar over four routes — and it is to be replaced, not
extended. The old pages are archived: remove them from the application, and rely
on git history rather than keeping dead routes around.

The file in this repo is byte-identical to the one at
`SRSE/docs/Analytics_Advisor.html`, so either copy is the reference.

## Part 0 — Take the layout, not the logic

The prototype is a **mockup**: `const DB = {...}` with six rows per table, a
`lev()` implementation in the browser, and no server at all. The engine behind
the real product is complete and verified — scope filtering, fuzzy matching,
grouping, exports, audit.

So: **take its markup, structure, classes and copy. Take none of its
behaviour.** Every list, every result, every count comes from the existing APIs.
If you find yourself porting `showPage`'s data handling or the browser-side
Levenshtein, stop.

## Part 1 — The shell

```
<aside class="sidebar" id="app-sidebar">
  brand: icon "D", appTitle, appSub
  nav:   3 buttons — p1 / p2 / p3
  sidebar-footer: theme toggle, language toggle
</aside>
<button class="sidebar-reopen-btn" id="sidebar-reopen">   <!-- collapsed state -->
<main class="main" id="main-content">
```

- **Collapsible**, with the reopen button the prototype provides.
- Three nav items only: **Database Overview**, **No-Code Query Builder**,
  **Result Dashboard** (`navOverview`, `navQueryBuilder`, `navDashboard`).
- Theme and language live in the **sidebar footer**, as in the prototype.

## Part 2 — The three pages

**Page 1 — Database Overview** (`p1Title`, `p1Sub`). AA-15 already built this at
`/database-overview`; restyle it into the prototype's shell. It must keep going
through the scope-filtered registry methods — a table hidden from an officer
stays hidden.

**Page 2 — No-Code Query Builder** (`p2Title`, `p2Sub`). This is where the two
current routes merge. The prototype has two tabs — `tabExtract` "1. Extract
Records" and `tabReport` "2. Report Analysis" — and a mode switch, `modeDual`
"Source & Destination" versus `modeSingle` "Single Source".

Its sections map onto features that already exist:

| Prototype section | Backed by |
|---|---|
| `secChooseTables` | registry cascade |
| `secSelectAttrs` | display columns |
| `secPrimaryKeys` | join criteria |
| `secRules` (`btnAddRule`) | AA-13 rules |
| `secFilter` (`btnAddFilter`) | AA-16 value filter |
| `secFuzzy` (`lblFuzzyRatio`, `lblCaseSensitive`, `lblWithSpace`, `lblFuzzyText`) | AA-16 fuzzy options and typed text |
| `secGroupBy` (`btnAddAgg`) | AA-14 grouping and aggregates |

Nothing in that table needs new backend work. `lblCaseSensitive` and
`lblWithSpace` are exactly AA-16's `caseSensitive` and `ignoreSpaces`.

**Page 3 — Result Dashboard** (`p3Title`, `p3Sub`). Cards, stat tiles, two
charts and a table, with a collapsible `report-controls-panel`.

The flow between them is in the prototype and should be kept: Extract →
`btnGoReport` → Report → `btnViewDashboard` → page 3, and `btnBackExtract` back.

**Results must survive the move to page 3.** Today they render inline. Carrying a
streamed result across a page change is the one genuinely new piece of
engineering here — hold it in client state, and do **not** re-run the query to
populate the dashboard. Re-running would double every audit entry and could
return different rows.

## Part 3 — What the prototype does not have

It has **no authentication surface at all** — no login, no OTP step, no forced
password change, no admin. Those exist, are required, and must not be deleted:

- **`/login`, `/change-password`, the OTP step** stay outside the three-page
  shell. An unauthenticated visitor sees login, not a sidebar.
- **Admin** is not in the prototype's nav. Put it in the **sidebar footer**
  beside theme and language, visible only to users who hold `SRSE_ADMIN`. This
  keeps the three-page IA the prototype specifies while leaving admin reachable.
- Audit viewing stays where AA-12 put it, gated on `AUDIT_READ`.

## Part 4 — Theme and language

- **Theme** (8.2): the prototype has a light/dark toggle (`lblTheme`,
  `themeLight`). Wire it, and persist the choice per browser.
- **Language** (`lblLanguage`): **the prototype already carries both English and
  Hindi for all 58 `data-t` keys** — see the `btnGoReport: "रिपोर्ट विश्लेषण →"`
  block. Lift those strings as the initial translation catalogue and wire the
  switch.

That is the *mechanism* for 8.3, not the whole of it. Strings outside the
prototype's surface — admin screens, error messages, audit viewer — are not
translated, and the native-speaker review in 8.3 still applies. **Do not claim
8.3 done.** Say which keys are covered.

Devanagari needs a font that actually renders it; check the prototype's choice
works in the app's bundle.

## Part 5 — Tests

- `npm run build` and `npm run test` clean.
- The three routes render, and the sidebar collapses and reopens.
- An officer with no visible tables sees the empty state, not a crash
  (`msgNoExtractYet`).
- Page 3 renders from state carried across navigation, with **no second call**
  to `/api/analysis/match` — assert the request count.
- Language switch changes rendered text and survives a reload.
- Backend is untouched: `mvn -f backend/pom.xml test` still **518**.

## Acceptance

- Live against the deployed stack: all three pages reachable from the sidebar,
  a query built on page 2 runs against the real API, and its results appear on
  page 3.
- As `jaipurofficer`, the same query shows **Jaipur only** — the new UI must not
  lose the scope enforcement the old one had.
- Report which old routes were removed and which prototype keys are wired.

## Do not

- Do not port the prototype's hardcoded data or browser-side matching.
- Do not re-run the query to populate the dashboard.
- Do not remove login, change-password or the OTP step.
- Do not claim the Hindi item complete.
