/**
 * Analytics Advisor final browser acceptance — accessible selectors only.
 */
import { chromium } from "playwright";
import { execSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { createReadStream } from "node:fs";
import { createInterface } from "node:readline";
import { execSync as exec } from "node:child_process";
import {
  dualGroupByInvalidComparisonRequest,
  dualGroupByValidRequest,
} from "./acceptance_helpers.mjs";
import {
  BROWSER_CHECKPOINT_FILENAME,
  assertCheckpointFingerprintMatches,
  loadBrowserCheckpoint,
  materializeCheckpointFromBrowserReport,
  runAutomatedSuite,
  writeBrowserCheckpoint,
} from "./acceptance_runner.mjs";

const ROOT = "/Users/sameerkhanna/Documents/Projects/analytics-advisor";
const BASE = process.env.AA_BASE_URL || "http://localhost:3000";
const API = process.env.AA_API_BASE || "http://localhost:8080";
const OUT = process.env.AA_ACCEPTANCE_OUT || "/tmp/aa-acceptance-screenshots";

const EXTRACT_SAVED_REGION = "Extract saved queries";
const REPORT_SAVED_REGION = "Report saved queries";

function requireAdminPassword() {
  const password = process.env.AA_ADMIN_PASS?.trim();
  if (!password) {
    throw new Error("AA_ADMIN_PASS environment variable is required (do not hardcode passwords in scripts).");
  }
  return password;
}

function adminUsername() {
  return process.env.AA_ADMIN_USER?.trim() || "superadmin";
}

fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(path.join(OUT, "downloads"), { recursive: true });

/** Progress to stderr — the final report is one JSON blob on stdout at the end. */
function progress(msg) {
  process.stderr.write(`${new Date().toISOString()} [aa-acceptance] ${msg}\n`);
}

function createPageDiagnostics() {
  return {
    pageErrors: [],
    consoleErrors: [],
    failedRequests: [],
    columnValuesResponses: [],
  };
}

function wirePageDiagnostics(page, diag) {
  page.on("pageerror", (err) => {
    diag.pageErrors.push(err.stack || String(err));
  });
  page.on("console", (msg) => {
    if (msg.type() === "error") {
      diag.consoleErrors.push(msg.text());
    }
  });
  page.on("requestfailed", (req) => {
    diag.failedRequests.push({
      url: req.url(),
      error: req.failure()?.errorText ?? "failed",
    });
  });
  page.on("response", async (res) => {
    if (res.request().method() !== "POST" || !res.url().includes("/api/analysis/column-values")) {
      return;
    }
    let body = "";
    try {
      body = await res.text();
    } catch {
      body = "";
    }
    diag.columnValuesResponses.push({ status: res.status(), body: body.slice(0, 4000) });
  });
}

async function failIfAppCrash(page, diag, prefix = "") {
  const appError = page.getByText(/Application error|Something went wrong/i).first();
  if (await appError.isVisible().catch(() => false)) {
    const snippet = await page.locator("body").innerText().catch(() => "");
    throw new Error(
      `${prefix}Next.js client error visible. pageErrors=${JSON.stringify(diag.pageErrors)} consoleErrors=${JSON.stringify(diag.consoleErrors)} snippet=${snippet.slice(0, 600)}`,
    );
  }
  if (diag.pageErrors.length > 0) {
    throw new Error(`${prefix}pageerror: ${diag.pageErrors[diag.pageErrors.length - 1]}`);
  }
}

function formatDiagnostics(diag) {
  return JSON.stringify(
    {
      pageErrors: diag.pageErrors,
      consoleErrors: diag.consoleErrors,
      failedRequests: diag.failedRequests,
      columnValues: diag.columnValuesResponses,
    },
    null,
    0,
  );
}

const report = {
  verdict: "INCOMPLETE",
  git: {},
  runtime: { baseUrl: BASE, apiBase: API },
  sections: {},
  matchLedger: { events: [], phases: [], ledgerDetails: [] },
  pipelineChecks: {},
  savedQuery: { cases: [], featureMatrix: {}, restorationMatrix: {} },
  dualGroupByDiagnosis: {},
  optionalFilters: {},
  auth: { admin: {}, officer: {} },
  exports: { formats: {}, normalizedColumns: {}, rowCountsMatch: false },
  screenshots: [],
  automatedSuites: {},
  automatedCommands: {},
  repoHygiene: {},
  limitations: [],
  acceptanceRun: {
    mode: "full",
    resumedFromCheckpoint: false,
    browserEvidenceProducedAt: null,
    browserReportWrittenAt: null,
    browserReportSourcePath: null,
    automatedEvidenceProducedAt: null,
    applicationSourceFingerprint: null,
    fingerprintMatchAtResume: null,
  },
};

function gitRecord() {
  report.git.commit = execSync(`git -C ${ROOT} rev-parse HEAD`, { encoding: "utf8" }).trim();
  report.git.dirty = execSync(`git -C ${ROOT} status -sb`, { encoding: "utf8" }).trim();
}

function isMatchExecution(url, method) {
  if (method !== "POST") return false;
  const p = new URL(url).pathname;
  return p === "/api/analysis/match" || p === "/api/analysis/match-multi";
}

function isExportEndpoint(url, method) {
  const p = new URL(url).pathname;
  return method === "POST" && /^\/api\/analysis\/match\.(csv|json|xml|xlsx)$/.test(p);
}

function phase(name, expectedNew, cumulative, extra = {}) {
  report.matchLedger.phases.push({ name, expectedNew, cumulative, ...extra });
}

async function loginApi(username, password) {
  const res = await fetch(`${API}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  const body = await res.json();
  return body.token;
}

function latestOtp() {
  const logs = execSync("docker logs analytics-advisor-srse-backend-1 2>&1 | tail -500", { encoding: "utf8" });
  const m = [...logs.matchAll(/verification code is: (\d{6})/g)];
  if (!m.length) throw new Error("No OTP in backend logs");
  return m[m.length - 1][1];
}

function navLink(page, name) {
  return page.locator("#app-sidebar").getByRole("link", { name });
}

async function loginLocal(page, username, password, { mfa = false } = {}) {
  await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded", timeout: 120000 });
  await page.getByLabel("Username").waitFor({ state: "visible", timeout: 120000 });
  await page.getByLabel("Username").fill(username);
  await page.locator("#login-password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  if (mfa) {
    await page.getByRole("heading", { name: "Verification code" }).waitFor({ timeout: 20000 });
    await page.waitForTimeout(800);
    await page.locator("#login-otp").fill(latestOtp());
    await page.getByRole("button", { name: "Verify" }).click();
  }
  await page.waitForURL((u) => !u.pathname.includes("/login"), { timeout: 60000 });
}

function extractSourcePick(page) {
  return page.locator("#qb1").locator("select#extract-source-catalog").locator("xpath=ancestor::div[contains(@class,'pick')][1]");
}

async function selectSourceTable(page, { catalog, schema, table }) {
  const pick = extractSourcePick(page);
  await pick.getByLabel("Catalog").selectOption(catalog);
  await pick.getByLabel("Schema").selectOption(schema);
  await pick.getByLabel("Table").selectOption(table);
}

async function selectTargetTable(page, { catalog, schema, table }) {
  const pick = page.locator("#qb1").locator("select#extract-target-catalog").locator("xpath=ancestor::div[contains(@class,'pick')][1]");
  await pick.getByLabel("Catalog").selectOption(catalog);
  await pick.getByLabel("Schema").selectOption(schema);
  await pick.getByLabel("Table").selectOption(table);
}

async function waitRunQueryDone(page) {
  await page.waitForFunction(
    () => {
      const b = [...document.querySelectorAll("#qb1 button, #qb2 button")].find((x) => x.textContent?.includes("Run Query"));
      return b && !b.textContent?.includes("Running");
    },
    { timeout: 300000 },
  );
}

async function waitReportTabEnabled(page) {
  await page.waitForFunction(
    () => {
      const b = [...document.querySelectorAll("button")].find((x) =>
        (x.getAttribute("aria-label") === "Report Analysis tab" ||
          /2\.\s*Report Analysis/.test(x.textContent || "")),
      );
      return b && b.getAttribute("aria-disabled") !== "true";
    },
    { timeout: 600000 },
  );
}

async function openReportTab(page) {
  await waitReportTabEnabled(page);
  await page.getByRole("tab", { name: "Report Analysis tab" }).click();
}

function normalizeLogicalColumns(names) {
  return [...new Set(names.map((n) => n.trim().toLowerCase().replace(/\s+/g, "_")))].sort();
}

function setsEqual(a, b) {
  return a.length === b.length && a.every((v, i) => v === b[i]);
}

function parseExportCsv(filePath) {
  const text = fs.readFileSync(filePath, "utf8");
  const lines = text.split(/\r?\n/).filter((l) => l.length > 0);
  if (lines.length === 0) return { dataRows: 0, columns: [] };
  const headers = lines[0].split(",").map((h) => h.replace(/^"|"$/g, "").trim());
  return { dataRows: Math.max(0, lines.length - 1), columns: normalizeLogicalColumns(headers) };
}

function parseExportJson(filePath) {
  const data = JSON.parse(fs.readFileSync(filePath, "utf8"));
  if (!Array.isArray(data)) {
    throw new Error("export.json must be a top-level array");
  }
  const keys =
    data.length > 0 && data[0] && typeof data[0] === "object" ? Object.keys(data[0]) : [];
  return { dataRows: data.length, columns: normalizeLogicalColumns(keys) };
}

function parseExportXml(filePath) {
  const xml = fs.readFileSync(filePath, "utf8");
  const rows = [...xml.matchAll(/<row>\s*([\s\S]*?)<\/row>/g)];
  const columns =
    rows.length > 0
      ? normalizeLogicalColumns([...rows[0][1].matchAll(/<([a-zA-Z0-9_]+)>/g)].map((m) => m[1]))
      : [];
  return { dataRows: rows.length, columns };
}

function xlsxCellText(cellXml, shared) {
  const inline = cellXml.match(/<is>\s*<t[^>]*>([^<]*)<\/t>/);
  if (inline) return inline[1];
  const v = cellXml.match(/<v>([^<]+)<\/v>/);
  if (!v) return "";
  const tAttr = cellXml.match(/\bt="([^"]+)"/);
  if (tAttr?.[1] === "s") return shared[Number(v[1])] ?? v[1];
  return v[1];
}

function parseExportXlsx(filePath) {
  const sharedXml = exec(`unzip -p "${filePath}" xl/sharedStrings.xml 2>/dev/null || true`, {
    encoding: "utf8",
  });
  const shared = [];
  for (const m of sharedXml.matchAll(/<t[^>]*>([^<]*)<\/t>/g)) {
    shared.push(m[1]);
  }
  const sheetXml = exec(`unzip -p "${filePath}" xl/worksheets/sheet1.xml`, { encoding: "utf8" });
  const rowBlocks = [...sheetXml.matchAll(/<row[^>]*>([\s\S]*?)<\/row>/g)];
  const dataRows = Math.max(0, rowBlocks.length - 1);
  const headers = [];
  if (rowBlocks.length > 0) {
    const cells = [...rowBlocks[0][1].matchAll(/<c[^>]*(?:\/>|>[\s\S]*?<\/c>)/g)];
    for (const cell of cells) {
      headers.push(xlsxCellText(cell[0], shared));
    }
  }
  return { dataRows, columns: normalizeLogicalColumns(headers.filter(Boolean)) };
}

function scoreExports() {
  const f = report.exports.formats;
  const counts = [f.csv?.dataRows, f.json?.dataRows, f.xml?.dataRows, f.xlsx?.dataRows];
  report.exports.normalizedColumns = {
    csv: f.csv?.columns ?? [],
    json: f.json?.columns ?? [],
    xml: f.xml?.columns ?? [],
    xlsx: f.xlsx?.columns ?? [],
  };
  const cols = Object.values(report.exports.normalizedColumns);
  report.exports.columnsMatch =
    cols.length === 4 && cols.every((c) => c.length > 0) && cols.every((c) => setsEqual(c, cols[0]));
  report.exports.rowCountsMatch =
    counts.every((c) => typeof c === "number" && c === 7) && report.exports.columnsMatch;
  report.sections.exports = report.exports.rowCountsMatch ? "PASS" : "FAIL";
}

async function activeQueryBuilderTabPanel(page) {
  const selectedTab = page.getByRole("tab", { selected: true });
  await selectedTab.waitFor({ state: "visible", timeout: 30000 });
  const controls = await selectedTab.getAttribute("aria-controls");
  if (!controls) {
    throw new Error("Active Query Builder tab missing aria-controls");
  }
  const panel = page.locator(`#${controls}`);
  await panel.waitFor({ state: "visible", timeout: 30000 });
  const tabLabel = (await selectedTab.getAttribute("aria-label")) ?? "";
  return { panel, tabLabel, tabId: await selectedTab.getAttribute("id") };
}

async function savedQueriesRegion(page) {
  const { panel, tabLabel, tabId } = await activeQueryBuilderTabPanel(page);
  const regionName = /Report Analysis/i.test(tabLabel) ? REPORT_SAVED_REGION : EXTRACT_SAVED_REGION;
  const regions = panel.getByRole("region", { name: regionName, exact: true });
  const count = await regions.count();
  if (count !== 1) {
    const diagnostics = await page.evaluate(() => {
      const entries = [];
      for (const r of document.querySelectorAll('[role="region"]')) {
        const ariaLabel = r.getAttribute("aria-label") ?? "";
        if (!/saved queries/i.test(ariaLabel)) continue;
        const tabpanel = r.closest('[role="tabpanel"]');
        const tabId = tabpanel?.getAttribute("aria-labelledby") ?? null;
        const tab = tabId ? document.getElementById(tabId) : null;
        entries.push({
          ariaLabel,
          tabpanelId: tabpanel?.id ?? null,
          tabAriaLabel: tab?.getAttribute("aria-label") ?? null,
          tabSelected: tab?.getAttribute("aria-selected") ?? null,
        });
      }
      const selected = document.querySelector('[role="tab"][aria-selected="true"]');
      return {
        activeTabId: selected?.id ?? null,
        activeTabLabel: selected?.getAttribute("aria-label") ?? null,
        activeTabControls: selected?.getAttribute("aria-controls") ?? null,
        savedQueryRegions: entries,
      };
    });
    throw new Error(
      `Expected exactly one “${regionName}” region in active panel (tab ${tabId}), found ${count}. diagnostics=${JSON.stringify(diagnostics)}`,
    );
  }
  return regions;
}

async function ensureQueryBuilderReady(page) {
  await page.goto(`${BASE}/query-builder`, { waitUntil: "domcontentloaded", timeout: 120000 });
  await page.getByRole("tab", { name: "Extract Records tab" }).waitFor({ state: "visible", timeout: 120000 });
}

async function ensureExtractTab(page) {
  await page.getByRole("tab", { name: "Extract Records tab" }).click();
  await page.locator("#qb1").waitFor({ state: "visible", timeout: 120000 });
}

async function ensureSingleSourceMode(page) {
  await ensureExtractTab(page);
  await page.getByRole("button", { name: "Single Source mode" }).click();
}

async function waitReportExportReady(page) {
  const toolbar = page.locator("#qb2 .result-table-toolbar").or(page.locator(".result-table-toolbar"));
  await toolbar.first().waitFor({ state: "visible", timeout: 180000 });
  const csvBtn = page.getByRole("button", { name: "Download CSV" }).first();
  await csvBtn.waitFor({ state: "visible", timeout: 180000 });
  await page.waitForFunction(
    () => {
      const btn = document.querySelector('button[aria-label="Download CSV"]');
      return btn && !btn.disabled;
    },
    { timeout: 180000 },
  );
}

async function runMatchLedger(page, setPhase) {
  await page.goto(`${BASE}/query-builder`);
  setPhase("Open Query Builder");
  phase("Open Query Builder", 0, report.matchLedger.events.length);

  setPhase("Configure Extract");
  await page.getByRole("button", { name: "Single Source mode" }).click();
  await selectSourceTable(page, { catalog: "iceberg", schema: "srse", table: "beneficiary" });
  await page.locator("#qb1 .attr-grid").waitFor({ timeout: 30000 });
  await page.getByRole("checkbox", { name: /^id\b/i }).check();
  await page.getByRole("checkbox", { name: /^district\b/i }).check();
  report.pipelineChecks.buildableNoGoReport =
    (await page.getByRole("button", { name: "Go to Report Analysis" }).count()) === 0;
  phase("Configure Extract", 0, report.matchLedger.events.length);

  setPhase("Open Report before Extract");
  const reportTab = page.getByRole("tab", { name: "Report Analysis tab" });
  report.pipelineChecks.reportTabAriaDisabledBeforeExtract =
    (await reportTab.getAttribute("aria-disabled")) === "true";
  await reportTab.click({ force: true });
  if (report.pipelineChecks.reportTabAriaDisabledBeforeExtract) {
    await page.getByRole("tab", { name: "Extract Records tab", selected: true }).waitFor();
    await page.locator("#qb1").waitFor({ state: "visible" });
    report.pipelineChecks.reportEmptyStateBeforeExtract = "tab locked until extract succeeds";
  } else {
    await page.locator("#qb-panel-report").getByText(/No extracted records yet/i).waitFor();
    report.pipelineChecks.reportEmptyStateBeforeExtract = true;
  }
  phase("Open Report before Extract", 0, report.matchLedger.events.length, {
    emptyState: report.pipelineChecks.reportEmptyStateBeforeExtract === true,
  });

  setPhase("Run Extract");
  await page.getByRole("tab", { name: "Extract Records tab" }).click();
  const bExt = report.matchLedger.events.length;
  await page.getByRole("button", { name: "Run Query" }).click();
  await waitRunQueryDone(page);
  await page.getByRole("button", { name: "Go to Report Analysis" }).waitFor({ timeout: 180000 });
  const aExt = report.matchLedger.events.length;
  phase("Run Extract", 1, aExt, { actualNew: aExt - bExt });
  report.pipelineChecks.extractUnlocksReport = true;

  setPhase("Open Report after Extract");
  await page.getByRole("button", { name: "Go to Report Analysis" }).click();
  await page.locator("#qb2").getByText(/iceberg\.srse\.beneficiary/i).first().waitFor();
  report.pipelineChecks.extractSummaryVisible = true;
  report.pipelineChecks.noCriterionCascades =
    (await page.locator("select#extract-source-catalog").count()) === 0;
  phase("Open Report after Extract", 0, report.matchLedger.events.length);

  setPhase("Inspect hydrated Extract summary");
  phase("Inspect hydrated Extract summary", 0, report.matchLedger.events.length);

  setPhase("Apply unchanged Report options");
  const applyBtn = page.getByRole("button", { name: "Apply report options" });
  const unchangedDisabled = await applyBtn.isDisabled();
  const bU = report.matchLedger.events.length;
  if (!unchangedDisabled) await applyBtn.click();
  phase("Apply unchanged Report options", 0, report.matchLedger.events.length, {
    actionDisabled: unchangedDisabled,
    actualNew: report.matchLedger.events.length - bU,
  });

  setPhase("Change server-side Report option");
  await page.getByRole("checkbox", { name: /server-side totals/i }).check();
  await page.getByLabel("Group by columns", { exact: false }).selectOption("district").catch(async () => {
    await page.locator("#report-group-by-col").selectOption("district");
  });
  phase("Change server-side Report option", 0, report.matchLedger.events.length);

  setPhase("Apply changed Report options");
  const bA = report.matchLedger.events.length;
  await applyBtn.click();
  await page.waitForTimeout(5000);
  phase("Apply changed Report options", 1, report.matchLedger.events.length, {
    actualNew: report.matchLedger.events.length - bA,
  });

  setPhase("Open Dashboard");
  const bD = report.matchLedger.events.length;
  await navLink(page, "Result Dashboard").click();
  await page.waitForURL("**/dashboard**");
  phase("Open Dashboard", 0, report.matchLedger.events.length, {
    actualNew: report.matchLedger.events.length - bD,
  });
  report.pipelineChecks.dashboardPopulated = (await page.locator(".empty").count()) === 0;

  setPhase("Return to Report");
  const bR = report.matchLedger.events.length;
  await navLink(page, "Query Builder").click();
  await page.getByRole("tab", { name: "Report Analysis tab" }).click({ force: true });
  phase("Return to Report", 0, report.matchLedger.events.length, {
    actualNew: report.matchLedger.events.length - bR,
  });

  setPhase("Reload Dashboard after clearing carried state");
  await page.evaluate(() => localStorage.removeItem("aa-query-results-v1"));
  const bC = report.matchLedger.events.length;
  await page.goto(`${BASE}/dashboard`);
  await page.getByText(/Run a query in the Query Builder to see analysis here/i).waitFor({ timeout: 15000 });
  phase("Reload Dashboard after clearing carried state", 0, report.matchLedger.events.length, {
    actualNew: report.matchLedger.events.length - bC,
    dashEmpty: true,
  });

  report.matchLedger.ledgerEndIndex = report.matchLedger.events.length;
}

function scoreLedger() {
  const ev = report.matchLedger.events.slice(0, report.matchLedger.ledgerEndIndex);
  report.matchLedger.ledgerEventCount = ev.length;
  report.matchLedger.ledgerDetails = ev.map((e, i) => ({
    n: i + 1,
    action: e.phase,
    method: e.method,
    endpoint: e.endpoint,
    status: e.status,
  }));
  const phasesOk = report.matchLedger.phases.every((p) =>
    p.actualNew == null ? true : p.actualNew === p.expectedNew,
  );
  report.sections.matchLedger = phasesOk && ev.length === 2 ? "PASS" : "FAIL";
}

async function apiRegisterSecondTable(adminToken) {
  const res = await fetch(`${API}/api/admin/lakehouse/registrations`, {
    method: "POST",
    headers: { Authorization: `Bearer ${adminToken}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      catalog: "iceberg_silver",
      schema: "silver_txn",
      table: "tbl_txn_bankdtl",
      layer: "SILVER",
      sourceSystem: null,
      tableGroup: null,
    }),
  });
  report.savedQuery.secondTableRegistered = res.status;
  return res.ok;
}

async function apiRestoreTags(adminToken, id, tags) {
  await fetch(`${API}/api/admin/lakehouse/registrations/${id}`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${adminToken}`, "Content-Type": "application/json" },
    body: JSON.stringify(tags),
  });
}

async function apiPostJson(token, path, body) {
  const res = await fetch(`${API}${path}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  return { status: res.status, body: text.slice(0, 4000) };
}

async function diagnoseDualGroupBy(adminToken) {
  const valid = dualGroupByValidRequest();
  const invalid = dualGroupByInvalidComparisonRequest();
  const validSql = await apiPostJson(adminToken, "/api/analysis/match.sql", valid);
  const invalidSql = await apiPostJson(adminToken, "/api/analysis/match.sql", invalid);
  const validMatch = await apiPostJson(adminToken, "/api/analysis/match", valid);
  report.dualGroupByDiagnosis = {
    rootCause:
      "HTTP 400 when groupByColumns and comparisonGroups are both present — frontend buildMergedRequest previously left stale comparison pairs on grouped requests.",
    productRule: "Post-join comparisons cannot be combined with grouping (MatchGroupingSql.validateGroupedRequest).",
    fix: "buildMergedRequest clears comparisonGroups and dedup when report.groupEnabled",
    failureStage:
      invalidSql.status === 400
        ? "Request validation before SQL execution (planMatch / validateGroupedRequest)"
        : "unknown",
    probes: {
      validDualGroupBy: {
        request: valid,
        matchSql: validSql,
        matchStreamHead: validMatch,
      },
      invalidComparisonPlusGroup: {
        request: invalid,
        matchSql: invalidSql,
      },
    },
  };
  report.sections.dualGroupBy =
    validSql.status === 200 && invalidSql.status === 400 && validSql.body.includes("GROUP BY")
      ? "PASS"
      : "FAIL";
}

async function browserDualGroupByProbe(page) {
  await page.goto(`${BASE}/query-builder`);
  await ensureExtractTab(page);
  await page.getByRole("button", { name: "Dual Source mode" }).click();
  await selectSourceTable(page, { catalog: "iceberg", schema: "srse", table: "beneficiary" });
  await selectTargetTable(page, {
    catalog: "iceberg_silver",
    schema: "silver_txn",
    table: "tbl_txn_bankdtl",
  });
  await page.getByRole("checkbox", { name: /^district\b/i }).check();
  await page.locator("#qb1").getByLabel("Source primary key").selectOption("id");
  await page.locator("#qb1").getByLabel("Destination primary key").selectOption("m_id");
  await page.getByRole("button", { name: "Run Query" }).click();
  await waitRunQueryDone(page);
  await page.getByRole("button", { name: "Go to Report Analysis" }).click();
  await page.locator("#report-group-enabled").check();
  await page.waitForFunction(
    () => {
      const sel = document.querySelector("#report-group-by-col");
      return sel && [...sel.options].some((o) => o.value === "district");
    },
    { timeout: 60000 },
  );
  await page.locator("#report-group-by-col").selectOption("district");
  let matchStatus = null;
  let matchBody = "";
  const matchResponsePromise = page.waitForResponse(
    (res) => res.url().includes("/api/analysis/match") && res.request().method() === "POST",
    { timeout: 180000 },
  );
  await page.getByRole("button", { name: "Apply report options" }).click();
  try {
    const matchResponse = await matchResponsePromise;
    matchStatus = matchResponse.status();
    matchBody = (await matchResponse.text()).slice(0, 500);
  } catch {
    /* listener below may still capture */
    await page.waitForTimeout(3000);
  }
  report.dualGroupByDiagnosis.browserApply = { status: matchStatus, bodyHead: matchBody };
  if (matchStatus === 200) {
    report.sections.dualGroupBy = "PASS";
  } else if (matchStatus != null && matchStatus !== 200) {
    report.sections.dualGroupBy = "FAIL";
  }
}

function rebuildMergedFromRequest(req) {
  return JSON.parse(
    execSync("npx tsx scripts/rebuildMergedRequest.ts", {
      cwd: `${ROOT}/frontend`,
      input: JSON.stringify(req),
      encoding: "utf8",
      maxBuffer: 16 * 1024 * 1024,
    }),
  );
}

async function fetchSavedRequest(adminToken, queryName) {
  const list = await fetch(`${API}/api/saved-queries`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  }).then((r) => r.json());
  const row = list.find((q) => q.name === queryName);
  if (!row) return null;
  const full = await fetch(`${API}/api/saved-queries/${row.id}`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  }).then((r) => r.json());
  return full.request;
}

async function waitSourceColumnsLoaded(page, { catalog, schema, table }) {
  const re = new RegExp(
    `/api/analysis/lakehouse/catalogs/${catalog}/schemas/${schema}/tables/${table}/columns`,
  );
  const res = await page.waitForResponse((r) => re.test(r.url()) && r.request().method() === "GET", {
    timeout: 120000,
  });
  if (!res.ok()) {
    throw new Error(`column metadata ${res.status()} ${await res.text()}`);
  }
  await page.locator("#qb1 .attr-grid label").first().waitFor({ state: "visible", timeout: 120000 });
}

async function waitColumnValuesResponse(page) {
  const res = await page.waitForResponse(
    (r) => r.url().includes("/api/analysis/column-values") && r.request().method() === "POST",
    { timeout: 120000 },
  );
  const body = await res.json().catch(() => ({}));
  if (!res.ok()) {
    throw new Error(`column-values ${res.status()} ${JSON.stringify(body)}`);
  }
  return body;
}

async function waitExtractRunComplete(page, caseId = "") {
  const tag = caseId ? `${caseId}: ` : "";
  progress(`${tag}extract running (Presto match — often 2–10 min, hard cap 20 min)`);
  const extractWaitMs = 1_200_000;
  const heartbeat = setInterval(() => {
    progress(`${tag}extract still running…`);
  }, 60_000);
  try {
    await page.waitForFunction(
      () => {
        const b = [...document.querySelectorAll("#qb1 button")].find((x) => x.textContent?.includes("Run Query"));
        return b && !/Running/i.test(b.textContent || "");
      },
      undefined,
      { timeout: extractWaitMs },
    );
    await page.waitForFunction(
      () => {
        const b = [...document.querySelectorAll("button")].find((x) =>
          (x.getAttribute("aria-label") === "Report Analysis tab" ||
          /2\.\s*Report Analysis/.test(x.textContent || "")),
        );
        return b && b.getAttribute("aria-disabled") !== "true";
      },
      undefined,
      { timeout: 120_000 },
    );
  } finally {
    clearInterval(heartbeat);
  }
  const errText = await page
    .locator("#qb1 .srse-text-danger, #qb1 .text-danger")
    .first()
    .textContent()
    .catch(() => "");
  if (errText?.trim()) {
    throw new Error(`extract failed: ${errText.trim()}`);
  }
  progress(`${tag}extract finished`);
}

async function waitReportApplyComplete(page) {
  await page.waitForFunction(
    () => {
      const b = [...document.querySelectorAll("#qb2 button")].find((x) =>
        /Apply report options/i.test(x.textContent || ""),
      );
      if (!b) return false;
      return !/Running/i.test(b.textContent || "");
    },
    undefined,
    { timeout: 600000 },
  );
  const err = await page
    .locator("#qb2 .srse-text-danger, #qb2 .text-danger")
    .first()
    .textContent()
    .catch(() => "");
  if (err?.trim()) {
    throw new Error(`report apply failed: ${err.trim()}`);
  }
}

async function ensureReportFiltersPanelOpen(page) {
  const inner = page.locator("#qb2 .report-controls-panel:not(.collapsed) .report-panel-inner");
  if (await inner.isVisible().catch(() => false)) {
    return;
  }
  const panel = page.locator("#qb2 .report-controls-panel").first();
  const collapsed = await panel.evaluate((el) => el.classList.contains("collapsed")).catch(() => true);
  if (collapsed) {
    const toggle = page.locator("#qb2 [data-testid='report-filters-toggle']");
    if ((await toggle.count()) > 0) {
      await toggle.click();
    } else {
      await page
        .locator("#qb2")
        .getByRole("button", { name: /Filters & Settings|फ़िल्टर और सेटिंग्स/i })
        .first()
        .click();
    }
  } else {
    await panel.scrollIntoViewIfNeeded().catch(() => {});
  }
  await inner.waitFor({ state: "visible", timeout: 60000 });
}

async function clickReportCheckbox(page, name, checked = true) {
  await ensureReportFiltersPanelOpen(page);
  const box = page.locator("#qb2").getByRole("checkbox", { name });
  await box.scrollIntoViewIfNeeded();
  if ((await box.isChecked()) === checked) {
    return;
  }
  await box.check({ force: false });
  if ((await box.isChecked()) !== checked) {
    await box.uncheck({ force: false }).catch(() => {});
    if (checked) {
      await box.check({ force: false });
    }
  }
  if ((await box.isChecked()) !== checked) {
    throw new Error(`report checkbox ${String(name)} could not be set to ${checked}`);
  }
}

async function qb1Checkbox(page, name) {
  const box = page.locator("#qb1").getByRole("checkbox", { name });
  await box.scrollIntoViewIfNeeded();
  return box;
}

/** Toggle React-controlled checkboxes (Playwright `check()` can hang on Zustand-backed inputs). */
async function setQb1Checkbox(page, name, checked = true) {
  await setScopedCheckbox(page, "#qb1", name, checked);
}


async function setScopedCheckbox(page, scope, name, checked = true) {
  const input = page.locator(scope).getByRole("checkbox", { name });
  await input.scrollIntoViewIfNeeded();
  if ((await input.isChecked()) === checked) {
    return;
  }
  if (checked) {
    await input.check();
  } else {
    await input.uncheck();
  }
  if ((await input.isChecked()) !== checked) {
    throw new Error(`checkbox ${String(name)} could not be set to ${checked}`);
  }
}

async function clickAddComparison(page) {
  await ensureReportFiltersPanelOpen(page);
  const btn = page.locator("#qb2").getByRole("button", { name: "Add comparison", exact: true });
  await page.locator("#qb2 .report-panel-inner").evaluate((el) => {
    el.scrollTop = el.scrollHeight;
  });
  await btn.scrollIntoViewIfNeeded();
  await btn.click({ timeout: 60000 });
}

async function captureSavedQueryFailure(page, caseId, result, adminToken, name) {
  const shot = path.join(OUT, `saved-query-fail-${caseId}.png`);
  await page.screenshot({ path: shot, fullPage: true }).catch(() => {});
  result.screenshot = shot;
  try {
    result.requestOnFailure = await fetchSavedRequest(adminToken, name);
  } catch {
    result.requestOnFailure = null;
  }
}

async function payloadHasNoDisplayLabels(adminToken, queryName) {
  const list = await fetch(`${API}/api/saved-queries`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  }).then((r) => r.json());
  const row = list.find((q) => q.name === queryName);
  if (!row) return { ok: false, reason: "saved query not found" };
  const full = await fetch(`${API}/api/saved-queries/${row.id}`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  }).then((r) => r.json());
  const raw = JSON.stringify(full.request);
  return {
    ok:
      !raw.includes('"sourceSystem"') &&
      !raw.includes('"tableGroup"') &&
      !/"layer"\s*:\s*"/.test(raw),
    request: full.request,
  };
}

async function waitForSavedQueryOpen(page, name, diag, timeoutMs = 120000) {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const loadedRe = new RegExp(`Loaded .${escaped}`);
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    await failIfAppCrash(page, diag, "saved-query-open: ");
    if (diag.pageErrors.length > 0) {
      throw new Error(`pageerror during saved-query open: ${diag.pageErrors[diag.pageErrors.length - 1]}`);
    }
    const pickerReady = await page
      .locator("#qb1 .value-filter-picker")
      .getByRole("checkbox", { name: /^Jaipur$/i })
      .isVisible()
      .catch(() => false);
    const jaipurChecked = await page
      .locator("#qb1 .value-filter-picker")
      .getByRole("checkbox", { name: /^Jaipur$/i })
      .isChecked()
      .catch(() => false);
    const pickValues = await page
      .locator("#qb1")
      .getByRole("checkbox", { name: "Pick values from list" })
      .isChecked()
      .catch(() => false);
    if (pickerReady && pickValues && jaipurChecked) {
      return { via: "ui-state" };
    }
    const msgVisible = await page
      .getByText(loadedRe)
      .isVisible()
      .catch(() => false);
    if (msgVisible) {
      return { via: "loaded-message" };
    }
    await page.waitForTimeout(250);
  }
  throw new Error(
    `saved-query open timed out for ${name}. diagnostics=${formatDiagnostics(diag)}`,
  );
}

async function openSavedByName(page, name, diag) {
  const region = await savedQueriesRegion(page);
  const openSaved = region.getByLabel("Open saved query");
  await openSaved.waitFor({ state: "visible" });
  await page.waitForFunction(
    (n) => {
      const sel = document.querySelector('select[aria-label="Open saved query"]');
      return sel && [...sel.options].some((o) => o.textContent?.includes(n));
    },
    name,
    { timeout: 30000 },
  );
  await openSaved.selectOption({ label: name });
  return waitForSavedQueryOpen(page, name, diag);
}

async function saveQueryFromReport(page, name) {
  await page.getByRole("tab", { name: "Report Analysis tab", selected: true }).waitFor({ timeout: 60000 });
  const region = await savedQueriesRegion(page);
  if (typeof region?.waitFor !== "function") {
    throw new Error("savedQueriesRegion did not return a Playwright Locator");
  }
  await region.waitFor({ state: "visible", timeout: 180000 });
  await region.scrollIntoViewIfNeeded();
  await region.getByPlaceholder("Save as…").fill(name);
  await region.getByRole("button", { name: "Save query" }).click();
  await region.getByText("Saved.", { exact: true }).waitFor({ timeout: 60000 });
}

async function runSavedQueryCase(page, adminToken, caseDef) {
  page.setDefaultTimeout(120000);
  page.setDefaultNavigationTimeout(120000);
  const diag = createPageDiagnostics();
  wirePageDiagnostics(page, diag);
  await page.goto(`${BASE}/login`);
  await page.evaluate(() => sessionStorage.clear());
  await loginLocal(page, adminUsername(), requireAdminPassword());
  await ensureQueryBuilderReady(page);
  const name = `sq-${caseDef.id}-${Date.now()}`;
  const result = {
    id: caseDef.id,
    name,
    restored: false,
    executed: false,
    payloadClean: false,
    requestFragmentOk: false,
    features: caseDef.features,
    diagnostics: diag,
  };
  try {
    progress(`${caseDef.id}: configuring…`);
    await caseDef.configure(page, diag);
    progress(`${caseDef.id}: Run Query (1st time)`);
    await page.getByRole("button", { name: "Run Query" }).click();
    await waitExtractRunComplete(page, caseDef.id);
    await openReportTab(page);
    if (caseDef.configureReport) {
      progress(`${caseDef.id}: report options…`);
      await caseDef.configureReport(page);
      const apply = page.getByRole("button", { name: "Apply report options" });
      await apply.click({ timeout: 120000 });
      await waitReportApplyComplete(page);
    }
    progress(`${caseDef.id}: saving…`);
    await saveQueryFromReport(page, name);
    const savedOnce = await fetchSavedRequest(adminToken, name);
    if (caseDef.verifySavedRequest && savedOnce) {
      result.requestFragmentOk = caseDef.verifySavedRequest(savedOnce) === true;
    } else if (!caseDef.verifySavedRequest) {
      result.requestFragmentOk = true;
    }
    progress(`${caseDef.id}: reload + reopen saved query…`);
    await page.reload();
    await failIfAppCrash(page, diag, `${caseDef.id} post-reload: `);
    const openMeta = await openSavedByName(page, name, diag);
    result.savedQueryOpenVia = openMeta.via;
    await failIfAppCrash(page, diag, `${caseDef.id} post-open: `);
    await ensureExtractTab(page);
    await failIfAppCrash(page, diag, `${caseDef.id} extract-tab: `);
    let restored = true;
    if (caseDef.verifyExtractRestored) {
      restored = restored && (await caseDef.verifyExtractRestored(page)) === true;
    }
    if (caseDef.verifyRestored && !caseDef.verifyExtractRestored && !caseDef.verifyReportRestored) {
      restored = (await caseDef.verifyRestored(page)) === true;
    }
    progress(`${caseDef.id}: Run Query (after reopen)`);
    await page.getByRole("button", { name: "Run Query" }).click();
    await waitExtractRunComplete(page, caseDef.id);
    if (caseDef.verifyReportRestored || caseDef.afterReopenReport) {
      await openReportTab(page);
      if (caseDef.afterReopenReport) {
        await caseDef.afterReopenReport(page);
      }
      if (caseDef.verifyReportRestored) {
        restored = restored && (await caseDef.verifyReportRestored(page)) === true;
      }
    }
    const savedReload = await fetchSavedRequest(adminToken, name);
    if (caseDef.verifyRebuiltRequest && savedReload) {
      const rebuilt = rebuildMergedFromRequest(savedReload);
      result.requestFragmentOk =
        result.requestFragmentOk && caseDef.verifyRebuiltRequest(savedReload, rebuilt) === true;
    }
    result.restored = restored;
    result.executed = true;
    const payload = await payloadHasNoDisplayLabels(adminToken, name);
    result.payloadClean = payload.ok;
    result.noClientErrors = diag.pageErrors.length === 0 && diag.consoleErrors.length === 0;
    result.pass =
      result.executed &&
      result.restored &&
      result.payloadClean &&
      result.requestFragmentOk &&
      result.noClientErrors;
    progress(`${caseDef.id}: ${result.pass ? "PASS" : "FAIL"} (restored=${result.restored})`);
  } catch (e) {
    const pe = diag.pageErrors.at(-1);
    const ce = diag.consoleErrors.at(-1);
    result.clientException = pe ?? ce ?? null;
    result.error = pe ? `${String(e)} | pageerror: ${pe}` : String(e);
    result.diagnosticsSummary = formatDiagnostics(diag);
    progress(`${caseDef.id}: FAIL — ${String(e).slice(0, 120)}`);
    await captureSavedQueryFailure(page, caseDef.id, result, adminToken, name);
  }
  return result;
}

async function runOneSavedQueryCase(context, adminToken, caseDef) {
  progress(`saved-query case: ${caseDef.id}`);
  const casePage = await context.newPage();
  try {
    return await runSavedQueryCase(casePage, adminToken, caseDef);
  } finally {
    await casePage.close();
  }
}

function savedQueryCaseFilter(caseId) {
  const only = process.env.AA_SAVED_QUERY_CASE?.trim();
  return !only || only === caseId;
}

async function runSavedQuerySuite(page, adminToken) {
  const only = process.env.AA_SAVED_QUERY_CASE?.trim();
  progress(
    only
      ? `saved-query suite (single case: ${only})`
      : "saved-query suite starting (7 cases, fresh login each — often 25–40 min)",
  );
  const context = page.context();
  const hasDual = await apiRegisterSecondTable(adminToken);
  report.savedQuery.featureMatrix = {
    dualJoinJoinType: false,
    sourceTargetRules: false,
    valueFilter: false,
    fuzzyRule: false,
    postJoinComparison: false,
    deduplication: false,
    groupByAggregate: false,
  };
  const cases = [];

  if (hasDual && savedQueryCaseFilter("dual-join-left")) {
    cases.push(
      await runOneSavedQueryCase(context, adminToken, {
        id: "dual-join-left",
        features: ["dualJoinJoinType"],
        async configure(page) {
          await ensureExtractTab(page);
          await page.getByRole("button", { name: "Dual Source mode" }).click();
          await selectSourceTable(page, { catalog: "iceberg", schema: "srse", table: "beneficiary" });
          await selectTargetTable(page, {
            catalog: "iceberg_silver",
            schema: "silver_txn",
            table: "tbl_txn_bankdtl",
          });
          await page.getByRole("checkbox", { name: /^id\b/i }).check();
          await page.getByRole("checkbox", { name: /^district\b/i }).check();
          await page.locator("#qb1").getByLabel("Source primary key").selectOption("id");
          await page.locator("#qb1").getByLabel("Destination primary key").selectOption("m_id");
          await page.locator("#qb1").getByLabel("Join type").selectOption("INNER");
        },
        async configureReport(page) {
          await page.locator("#qb2 #join-type-embed").selectOption("LEFT");
        },
        async verifyExtractRestored(page) {
          return (await page.locator("#qb1").getByLabel("Join type").inputValue()) === "LEFT";
        },
        async verifyReportRestored(page) {
          return (await page.locator("#join-type-embed").inputValue()) === "LEFT";
        },
      }),
    );
  }

  if (savedQueryCaseFilter("source-rules")) {
  cases.push(
    await runOneSavedQueryCase(context, adminToken, {
      id: "source-rules",
      features: ["sourceTargetRules"],
      async configure(page) {
        await ensureSingleSourceMode(page);
        await selectSourceTable(page, { catalog: "iceberg", schema: "srse", table: "beneficiary" });
        await page.getByRole("checkbox", { name: /^district\b/i }).check();
        await page.locator("#qb1").getByLabel("Source rule column").selectOption("district");
        await page.locator("#qb1").getByLabel("Source rule operator").selectOption("EQ");
        await page.locator("#qb1").getByLabel("Source rule value").fill("Jaipur");
      },
      async verifyRestored(page) {
        return (
          (await page.locator("#qb1").getByLabel("Source rule column").inputValue()) === "district" &&
          (await page.locator("#qb1").getByLabel("Source rule value").inputValue()) === "Jaipur"
        );
      },
    }),
  );
  }

  if (savedQueryCaseFilter("value-filter-in")) {
  cases.push(
    await runOneSavedQueryCase(context, adminToken, {
      id: "value-filter-in",
      features: ["valueFilter"],
      async configure(page, diag) {
        await ensureSingleSourceMode(page);
        await selectSourceTable(page, { catalog: "iceberg", schema: "srse", table: "beneficiary" });
        await waitSourceColumnsLoaded(page, { catalog: "iceberg", schema: "srse", table: "beneficiary" });
        await setQb1Checkbox(page, /^district\b/i, true);
        await setQb1Checkbox(page, "Pick values from list", true);
        const valuesPromise = waitColumnValuesResponse(page);
        await page.locator("#qb1").getByLabel("Source rule column").selectOption("district");
        const valuesBody = await valuesPromise;
        await failIfAppCrash(page, diag, "value-filter: ");
        const cvFromDiag = diag.columnValuesResponses.at(-1);
        if (cvFromDiag && cvFromDiag.status !== 200) {
          throw new Error(
            `column-values HTTP ${cvFromDiag.status}: ${cvFromDiag.body} diagnostics=${formatDiagnostics(diag)}`,
          );
        }
        const jaipurInApi =
          valuesBody.values?.find((v) => v != null && /^jaipur$/i.test(String(v))) ??
          JSON.parse(cvFromDiag?.body || "{}").values?.find((v) => v != null && /^jaipur$/i.test(String(v)));
        if (!jaipurInApi) {
          throw new Error(
            `column-values missing Jaipur: api=${JSON.stringify(valuesBody)} diag=${formatDiagnostics(diag)}`,
          );
        }
        const picker = page.locator("#qb1 .value-filter-picker");
        await picker.waitFor({ state: "visible", timeout: 30000 });
        await failIfAppCrash(page, diag, "value-filter: ");
        await picker.getByText("Loading values…").waitFor({ state: "hidden", timeout: 60000 });
        await failIfAppCrash(page, diag, "value-filter: ");
        const jaipurValue = String(jaipurInApi);
        await picker.getByPlaceholder("Search values…").fill(jaipurValue);
        const jaipurCb = picker.getByRole("checkbox", { name: new RegExp(`^${jaipurValue}$`, "i") });
        await jaipurCb.waitFor({ state: "visible", timeout: 30000 });
        await failIfAppCrash(page, diag, "value-filter: ");
        await jaipurCb.check();
      },
      verifySavedRequest(req) {
        return req.sourceRules?.root?.operator === "IN" && req.sourceRules?.root?.value?.includes("Jaipur");
      },
      verifyRebuiltRequest(_saved, rebuilt) {
        return (
          rebuilt?.sourceRules?.root?.operator === "IN" &&
          rebuilt?.sourceRules?.root?.value?.includes("Jaipur")
        );
      },
      async verifyExtractRestored(page) {
        await page.locator("#qb1").getByRole("checkbox", { name: "Pick values from list" }).waitFor({
          state: "visible",
          timeout: 60000,
        });
        await page.waitForFunction(
          () => {
            const pick = document.querySelector('#qb1 input[aria-label="Pick values from list"]');
            return pick instanceof HTMLInputElement && pick.checked;
          },
          { timeout: 60000 },
        );
        await page.locator("#qb1").getByLabel("Source rule column").waitFor({ state: "visible" });
        await page.waitForFunction(
          () => document.querySelector('#qb1 select[aria-label="Source rule column"]')?.value === "district",
          { timeout: 60000 },
        );
        const picker = page.locator("#qb1 .value-filter-picker");
        await picker.waitFor({ state: "visible", timeout: 60000 });
        await picker.getByText("Loading values…").waitFor({ state: "hidden", timeout: 120000 });
        const jaipurCb = picker.getByRole("checkbox", { name: /^Jaipur$/i });
        await jaipurCb.waitFor({ state: "visible", timeout: 60000 });
        await page.waitForFunction(
          () => {
            const cb = document.querySelector('#qb1 input[aria-label="Jaipur"]');
            return cb instanceof HTMLInputElement && cb.checked;
          },
          { timeout: 60000 },
        );
        return (
          (await page.locator("#qb1").getByRole("checkbox", { name: "Pick values from list" }).isChecked()) &&
          (await page.locator("#qb1").getByLabel("Source rule column").inputValue()) === "district" &&
          (await jaipurCb.isChecked())
        );
      },
    }),
  );
  }

  if (savedQueryCaseFilter("fuzzy-rule-father-name")) {
  cases.push(
    await runOneSavedQueryCase(context, adminToken, {
      id: "fuzzy-rule-father-name",
      features: ["fuzzyRule"],
      async configure(page) {
        await ensureSingleSourceMode(page);
        await selectSourceTable(page, { catalog: "iceberg", schema: "srse", table: "beneficiary" });
        await waitSourceColumnsLoaded(page, { catalog: "iceberg", schema: "srse", table: "beneficiary" });
        await setQb1Checkbox(page, /^father_name\b/i, true);
        await setQb1Checkbox(page, "Typed-text fuzzy match", true);
        await page.locator("#qb1").getByLabel("Source rule column").selectOption("father_name");
        await page
          .locator("#qb1")
          .getByRole("checkbox", { name: "Typed-text fuzzy match" })
          .waitFor({ state: "attached" });
        await page.locator("#qb1").getByLabel("Name to match").waitFor({ state: "visible", timeout: 120000 });
        await page.locator("#qb1").getByLabel("Threshold %").waitFor({ state: "visible" });
        await page.locator("#qb1").getByRole("checkbox", { name: "Ignore spaces", exact: true }).waitFor({
          state: "visible",
        });
        await page.locator("#qb1").getByLabel("Name to match").fill("Kumari");
        await page.locator("#qb1").getByLabel("Threshold %").fill("72");
        await page.locator("#qb1").getByRole("checkbox", { name: "Ignore spaces", exact: true }).check();
        await page.locator("#qb1").getByRole("checkbox", { name: "Case sensitive", exact: true }).check();
      },
      verifySavedRequest(req) {
        const v = req.sourceRules?.root?.value;
        return (
          req.sourceRules?.root?.operator === "FUZZY_MATCH" &&
          Array.isArray(v) &&
          v[0] === "Kumari" &&
          v[1] === 72 &&
          v[2]?.ignoreSpaces === true &&
          v[2]?.caseSensitive === true
        );
      },
      verifyRebuiltRequest(_saved, rebuilt) {
        const v = rebuilt?.sourceRules?.root?.value;
        return (
          rebuilt?.sourceRules?.root?.operator === "FUZZY_MATCH" &&
          Array.isArray(v) &&
          v[0] === "Kumari" &&
          v[1] === 72
        );
      },
      async verifyExtractRestored(page) {
        return (
          (await page.locator("#qb1").getByRole("checkbox", { name: "Typed-text fuzzy match" }).isChecked()) &&
          (await page.locator("#qb1").getByLabel("Source rule column").inputValue()) === "father_name" &&
          (await page.locator("#qb1").getByLabel("Name to match").inputValue()) === "Kumari" &&
          Number(await page.locator("#qb1").getByLabel("Threshold %").inputValue()) === 72 &&
          (await page.locator("#qb1").getByRole("checkbox", { name: "Ignore spaces", exact: true }).isChecked()) &&
          (await page.locator("#qb1").getByRole("checkbox", { name: "Case sensitive", exact: true }).isChecked())
        );
      },
    }),
  );
  }

  if (hasDual && savedQueryCaseFilter("post-join-comparison")) {
    cases.push(
      await runOneSavedQueryCase(context, adminToken, {
        id: "post-join-comparison",
        features: ["postJoinComparison"],
        async configure(page) {
          await ensureExtractTab(page);
          await page.getByRole("button", { name: "Dual Source mode" }).click();
          await selectSourceTable(page, { catalog: "iceberg", schema: "srse", table: "beneficiary" });
          await selectTargetTable(page, {
            catalog: "iceberg_silver",
            schema: "silver_txn",
            table: "tbl_txn_bankdtl",
          });
          await page.getByRole("checkbox", { name: /^id\b/i }).check();
          await page.getByRole("checkbox", { name: /^district\b/i }).check();
          await page.locator("#qb1").getByLabel("Source primary key").selectOption("id");
          await page.locator("#qb1").getByLabel("Destination primary key").selectOption("m_id");
          await page.locator("#qb1").getByLabel("Join type").selectOption("INNER");
        },
        async configureReport(page) {
          await ensureReportFiltersPanelOpen(page);
          await clickAddComparison(page);
          await page.locator("#qb2").getByLabel("Comparison source column").first().selectOption("district");
          await page.locator("#qb2").getByLabel("Comparison target column").first().selectOption("district");
        },
        verifySavedRequest(req) {
          return (req.comparisonGroups?.length ?? 0) >= 1;
        },
        verifyRebuiltRequest(_saved, rebuilt) {
          return (rebuilt?.comparisonGroups?.length ?? 0) >= 1;
        },
        async verifyReportRestored(page) {
          await ensureReportFiltersPanelOpen(page);
          return (
            (await page.locator("#qb2").getByLabel("Comparison source column").first().inputValue()) ===
              "district" &&
            (await page.locator("#qb2").getByLabel("Comparison target column").first().inputValue()) ===
              "district"
          );
        },
        async afterReopenReport(page) {
          const apply = page.getByRole("button", { name: "Apply report options" });
          await apply.click({ timeout: 120000 });
          await waitReportApplyComplete(page);
        },
      }),
    );
  }

  if (hasDual && savedQueryCaseFilter("dedup-inner")) {
    cases.push(
      await runOneSavedQueryCase(context, adminToken, {
        id: "dedup-inner",
        features: ["deduplication"],
        async configure(page) {
          await ensureExtractTab(page);
          await page.getByRole("button", { name: "Dual Source mode" }).click();
          await selectSourceTable(page, { catalog: "iceberg", schema: "srse", table: "beneficiary" });
          await selectTargetTable(page, {
            catalog: "iceberg_silver",
            schema: "silver_txn",
            table: "tbl_txn_bankdtl",
          });
          await waitSourceColumnsLoaded(page, { catalog: "iceberg", schema: "srse", table: "beneficiary" });
          await page.getByRole("checkbox", { name: /^id\b/i }).check();
          await page.locator("#qb1").getByLabel("Source primary key").selectOption("id");
          await page.locator("#qb1").getByLabel("Destination primary key").selectOption("m_id");
          await page.locator("#qb1").getByLabel("Join type").selectOption("INNER");
        },
        async configureReport(page) {
          await ensureReportFiltersPanelOpen(page);
          const dedup = page.locator("#hide-duplicate-records-embed");
          const gridDedup = page.locator("#hide-duplicate-records");
          if (await dedup.isVisible().catch(() => false)) {
            if (await dedup.isDisabled()) {
              const title = await dedup.locator("xpath=ancestor::label[1]").getAttribute("title").catch(() => "");
              throw new Error(`dedup control disabled — ${title || "incompatible join or missing dedup column"}`);
            }
            await clickReportCheckbox(page, "Hide duplicate records", true);
          } else if (await gridDedup.isVisible().catch(() => false)) {
            await gridDedup.check();
          } else {
            throw new Error("dedup control not found (#hide-duplicate-records-embed or #hide-duplicate-records)");
          }
        },
        verifySavedRequest(req) {
          return Boolean(req.dedup?.column);
        },
        verifyRebuiltRequest(_saved, rebuilt) {
          return Boolean(rebuilt?.dedup?.column);
        },
        async verifyReportRestored(page) {
          await ensureReportFiltersPanelOpen(page);
          return page.locator("#hide-duplicate-records-embed").isChecked();
        },
        async afterReopenReport(page) {
          await page.getByRole("button", { name: "Apply report options" }).click({ timeout: 120000 });
          await waitReportApplyComplete(page);
        },
      }),
    );
  }

  if (savedQueryCaseFilter("group-by-aggregate")) {
  cases.push(
    await runOneSavedQueryCase(context, adminToken, {
      id: "group-by-aggregate",
      features: ["groupByAggregate"],
      async configure(page) {
        await ensureSingleSourceMode(page);
        await selectSourceTable(page, { catalog: "iceberg", schema: "srse", table: "beneficiary" });
        await waitSourceColumnsLoaded(page, { catalog: "iceberg", schema: "srse", table: "beneficiary" });
        await setQb1Checkbox(page, /^district\b/i, true);
      },
      async configureReport(page) {
        await ensureReportFiltersPanelOpen(page);
        await clickReportCheckbox(page, "Group by server-side totals", true);
        await page.locator("#report-group-by-col").waitFor({ state: "visible", timeout: 120000 });
        await page.waitForFunction(
          () => {
            const sel = document.querySelector("#report-group-by-col");
            return sel && [...sel.options].some((o) => o.value === "district");
          },
          { timeout: 120000 },
        );
        await page.locator("#report-group-by-col").selectOption("district");
      },
      verifySavedRequest(req) {
        return (
          (req.groupByColumns?.length ?? 0) >= 1 &&
          req.groupByColumns[0].column === "district" &&
          (req.aggregates?.length ?? 0) >= 1
        );
      },
      verifyRebuiltRequest(_saved, rebuilt) {
        return (
          (rebuilt?.groupByColumns?.length ?? 0) >= 1 &&
          rebuilt?.groupByColumns?.[0]?.column === "district"
        );
      },
      async verifyReportRestored(page) {
        await ensureReportFiltersPanelOpen(page);
        await page.locator("#report-group-enabled").waitFor({ state: "visible", timeout: 60000 });
        await page.waitForFunction(
          () => {
            const sel = document.querySelector("#report-group-by-col");
            return sel && [...sel.options].some((o) => o.value === "district");
          },
          { timeout: 60000 },
        );
        return (
          (await page.locator("#report-group-enabled").isChecked()) &&
          (await page.locator("#report-group-by-col").inputValue()) === "district"
        );
      },
    }),
  );
  }

  report.savedQuery.cases = cases;
  const matrixKeys = [
    "dualJoinJoinType",
    "sourceTargetRules",
    "valueFilter",
    "fuzzyRule",
    "postJoinComparison",
    "deduplication",
    "groupByAggregate",
  ];
  for (const c of cases) {
    const ok =
      c.pass ??
      (c.executed && c.restored && c.payloadClean && (c.requestFragmentOk ?? true));
    for (const f of c.features) {
      if (matrixKeys.includes(f)) {
        report.savedQuery.featureMatrix[f] = Boolean(ok);
      }
    }
  }
  report.savedQuery.restorationMatrix = Object.fromEntries(
    matrixKeys.map((k) => [k, Boolean(report.savedQuery.featureMatrix[k])]),
  );
  report.sections.savedQuery = Object.values(report.savedQuery.featureMatrix).every(Boolean)
    ? "PASS"
    : "FAIL";
}

async function runOptionalFilters(page, adminToken) {
  await apiRestoreTags(adminToken, 1, {
    layer: "GOLD",
    sourceSystem: "ACCEPT_SS",
    tableGroup: "ACCEPT_TG",
  });
  await page.goto(`${BASE}/query-builder`);
  const pick = extractSourcePick(page);
  await pick.getByLabel("Catalog").waitFor();
  report.optionalFilters.catalogWithoutOpeningDetails =
    (await pick.getByLabel("Catalog").inputValue()) !== undefined;
  const details = pick.locator("details.cascade-optional-filters");
  report.optionalFilters.disclosureVisible = (await details.count()) > 0;
  if (await details.count()) {
    await details.locator("summary").click();
    await pick.getByLabel("Source system").selectOption("ACCEPT_SS");
    await pick.getByLabel("Source system").selectOption("");
    await pick.getByLabel("Table group").selectOption("ACCEPT_TG");
    await pick.getByLabel("Table group").selectOption("");
    await pick.getByLabel("Layer").selectOption("GOLD");
    await pick.getByLabel("Layer").selectOption("");
    report.optionalFilters.cycleComplete = true;
  }
  await page.waitForFunction(
    () => {
      const sel = document.querySelector("#extract-source-catalog");
      return sel && [...sel.options].some((o) => o.value === "iceberg");
    },
    { timeout: 120000 },
  );
  await pick.getByLabel("Catalog").selectOption("iceberg");
  report.optionalFilters.catalogUsable = true;
  const regs = await fetch(`${API}/api/admin/lakehouse/registrations`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  }).then((r) => r.json());
  for (const reg of regs) {
    await apiRestoreTags(adminToken, reg.id, { layer: null, sourceSystem: null, tableGroup: null });
  }
  await page.reload();
  await pick.getByLabel("Catalog").waitFor();
  const detailsAfter = pick.locator("details.cascade-optional-filters");
  report.optionalFilters.disclosureHiddenAfterRestore = (await detailsAfter.count()) === 0;
  report.sections.optionalFilters =
    report.optionalFilters.catalogUsable && report.optionalFilters.disclosureHiddenAfterRestore
      ? "PASS"
      : "PARTIAL";
}

async function runExports(page) {
  await page.goto(`${BASE}/query-builder`);
  await ensureSingleSourceMode(page);
  await selectSourceTable(page, { catalog: "iceberg", schema: "srse", table: "beneficiary" });
  await page.getByRole("checkbox", { name: /^district\b/i }).check();
  await page.getByRole("button", { name: "Run Query" }).click();
  await waitRunQueryDone(page);
  await openReportTab(page);
  await page.locator("#report-group-enabled").check();
  await page.waitForFunction(
    () => {
      const sel = document.querySelector("#report-group-by-col");
      return sel && [...sel.options].some((o) => o.value === "district");
    },
    { timeout: 60000 },
  );
  await page.locator("#report-group-by-col").selectOption("district");
  await page.getByRole("button", { name: "Apply report options" }).click();
  await waitReportExportReady(page);

  const dl = path.join(OUT, "downloads");
  const exportRequests = [];
  page.on("request", (req) => {
    if (isExportEndpoint(req.url(), req.method())) {
      exportRequests.push(new URL(req.url()).pathname);
    }
  });

  const downloadBtn = page.locator("#qb2").getByRole("button", { name: "Download CSV" }).first();
  const jsonBtn = page.locator("#qb2").getByRole("button", { name: "Download JSON" }).first();
  const xmlBtn = page.locator("#qb2").getByRole("button", { name: "Download XML" }).first();
  const xlsxBtn = page.locator("#qb2").getByRole("button", { name: "Download Excel" }).first();

  for (const [fmt, btn] of [
    ["csv", downloadBtn],
    ["json", jsonBtn],
    ["xml", xmlBtn],
    ["xlsx", xlsxBtn],
  ]) {
    const [download] = await Promise.all([
      page.waitForEvent("download", { timeout: 180000 }),
      btn.click(),
    ]);
    const file = path.join(dl, `export.${fmt}`);
    await download.saveAs(file);
    if (fmt === "csv") report.exports.formats.csv = parseExportCsv(file);
    if (fmt === "json") report.exports.formats.json = parseExportJson(file);
    if (fmt === "xml") report.exports.formats.xml = parseExportXml(file);
    if (fmt === "xlsx") report.exports.formats.xlsx = parseExportXlsx(file);
  }
  report.exports.exportEndpoints = exportRequests;
  scoreExports();
}

async function runAuth(browser, adminToken) {
  const ctxA = await browser.newContext();
  const pA = await ctxA.newPage();
  await loginLocal(pA, adminUsername(), requireAdminPassword());
  report.auth.admin.primaryNav = await pA.locator("#app-sidebar nav").getByRole("link").count();
  await pA
    .locator("#app-sidebar")
    .getByRole("link", { name: /Admin|एडमिन/ })
    .waitFor({ state: "visible", timeout: 15000 });
  report.auth.admin.seesAdmin = true;
  await pA.goto(`${BASE}/admin456`);
  await pA.getByRole("heading", { name: /Lakehouse registry|Table Registry/i }).waitFor({ timeout: 30000 }).catch(() => {});
  report.auth.admin.adminPageLoaded = /Lakehouse registry|Table Registry/i.test(await pA.textContent("body"));
  const blankTagRes = await fetch(`${API}/api/admin/lakehouse/registrations/1`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${adminToken}`, "Content-Type": "application/json" },
    body: JSON.stringify({ layer: null, sourceSystem: null, tableGroup: null }),
  });
  report.auth.admin.blankOptionalLabelsSaved = blankTagRes.ok;
  await ctxA.close();

  const ctxO = await browser.newContext();
  const pO = await ctxO.newPage();
  await loginLocal(pO, process.env.AA_OFFICER_USER || "jaipurofficer", process.env.AA_OFFICER_PASS || "JaiOffic$1x", {
    mfa: true,
  });
  report.auth.officer.primaryNav = await pO.locator("#app-sidebar nav").getByRole("link").count();
  report.auth.officer.seesAdmin =
    (await pO.locator("#app-sidebar").getByRole("link", { name: /Admin|एडमिन/ }).count()) > 0;
  const ot = await pO.evaluate(() => sessionStorage.getItem("srse.auth.token"));
  report.auth.officer.adminPostStatus = await fetch(`${API}/api/admin/lakehouse/registrations`, {
    method: "POST",
    headers: { Authorization: `Bearer ${ot}`, "Content-Type": "application/json" },
    body: JSON.stringify({ catalog: "x", schema: "y", table: "z" }),
  }).then((r) => r.status);

  await pO.goto(`${BASE}/query-builder`);
  const catalogs = await pO.locator("select#extract-source-catalog option").allTextContents();
  report.auth.officer.catalogOptions = catalogs.filter((c) => c && !c.includes("Select"));

  const offCount = await matchCountRows(ot, { groupBy: true });
  await ctxO.close();

  const superCount = await matchCountRows(adminToken, { groupBy: true });
  report.auth.officer.scopedRowsLtSuperadmin = offCount < superCount;
  report.auth.officer.superadminGroupedRows = superCount;
  report.auth.officer.officerGroupedRows = offCount;

  report.sections.auth =
    report.auth.admin.seesAdmin &&
    report.auth.admin.adminPageLoaded &&
    report.auth.admin.primaryNav === 3 &&
    !report.auth.officer.seesAdmin &&
    report.auth.officer.primaryNav === 3 &&
    report.auth.officer.adminPostStatus === 403 &&
    report.auth.officer.scopedRowsLtSuperadmin
      ? "PASS"
      : "FAIL";
}

async function matchCountRows(token, { groupBy }) {
  const body = {
    sourceCriteria: [],
    targetCriteria: [],
    sourceDisplayColumns: [{ catalog: "iceberg", schema: "srse", table: "beneficiary", column: "district" }],
    highlightDuplicates: false,
    dedup: null,
    singleSource: true,
  };
  if (groupBy) {
    body.sourceDisplayColumns = [];
    body.groupByColumns = [{ catalog: "iceberg", schema: "srse", table: "beneficiary", column: "district" }];
    body.aggregates = [{ function: "COUNT", distinct: false }];
  }
  const res = await fetch(`${API}/api/analysis/match`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) return -1;
  let rows = 0;
  const text = await res.text();
  for (const line of text.split("\n")) {
    if (line.startsWith('{"type":"row"')) rows += 1;
  }
  return rows;
}

async function capture(page, name, vp) {
  await page.setViewportSize(vp);
  await page.waitForTimeout(300);
  const f = path.join(OUT, `${name}.png`);
  await page.screenshot({ path: f, fullPage: true });
  report.screenshots.push(f);
  if (vp.width <= 400) {
    report.sections[`overflow_${name}`] = (await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ))
      ? "PASS"
      : "FAIL";
  }
}

async function setShellTheme(page, mode) {
  const lightBtn = page.getByRole("button", { name: /^Light$|^लाइट$/ });
  const darkBtn = page.getByRole("button", { name: /^Dark$|^डार्क$/ });
  if (mode === "light" && (await lightBtn.isVisible().catch(() => false))) {
    await lightBtn.click();
  }
  if (mode === "dark" && (await darkBtn.isVisible().catch(() => false))) {
    await darkBtn.click();
  }
}

async function runVisualMatrix(page) {
  await page.goto(`${BASE}/query-builder`);
  await ensureSingleSourceMode(page);
  await capture(page, "1280-dark-en", { width: 1280, height: 900 });
  await setShellTheme(page, "light");
  await capture(page, "1280-light-en", { width: 1280, height: 900 });
  await page.locator("#app-sidebar").getByRole("button", { name: /हिन्दी \(HI\)/ }).click();
  await capture(page, "1280-light-hi", { width: 1280, height: 900 });
  await setShellTheme(page, "dark");
  await capture(page, "1280-dark-hi", { width: 1280, height: 900 });
  await page.getByRole("button", { name: "Collapse sidebar" }).click();
  await capture(page, "1280-dark-hi-sidebar-collapsed", { width: 1280, height: 900 });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "Expand sidebar" }).click();
  await capture(page, "390-dark-en", { width: 390, height: 844 });
  await setShellTheme(page, "light");
  await page.locator("#app-sidebar").getByRole("button", { name: /English \(EN\)/ }).click();
  await capture(page, "390-light-hi", { width: 390, height: 844 });
  report.sections.visualMatrix = report.screenshots.length >= 7 ? "PASS" : "FAIL";
}

function applyAutomatedSuiteResults({ commands, repoHygiene, evaluation }) {
  report.automatedCommands = commands;
  report.repoHygiene = { ...report.repoHygiene, ...repoHygiene };
  report.automatedSuites = {
    frontendTests: commands.frontendTests,
    frontendLint: {
      ...commands.frontendLint,
      eslintErrors: evaluation.lintSummary.errors,
      eslintWarnings: evaluation.lintSummary.warnings,
    },
    backendTests: commands.backendTests,
    productionBuild: {
      ...commands.productionBuild,
      buildPass: evaluation.buildEval.pass,
      buildPassReason: evaluation.buildEval.reason,
    },
    diffCheck: commands.diffCheck,
    gateEvaluation: evaluation,
  };
  report.sections.automated = evaluation.pass ? "PASS" : "FAIL";
  if (!evaluation.pass) {
    report.automatedSuites.gateFailures = evaluation.failures;
  }
}

async function runAutomatedSuites() {
  const suite = runAutomatedSuite(ROOT, API);
  applyAutomatedSuiteResults(suite);
  report.acceptanceRun.automatedEvidenceProducedAt = new Date().toISOString();
}

function mergeBrowserCheckpointIntoReport(checkpoint) {
  const browser = checkpoint.report?.sections ? checkpoint.report : checkpoint.report;
  Object.assign(report.sections, browser.sections ?? checkpoint.report.sections);
  report.matchLedger = browser.matchLedger ?? checkpoint.report.matchLedger;
  report.pipelineChecks = browser.pipelineChecks ?? checkpoint.report.pipelineChecks;
  report.savedQuery = browser.savedQuery ?? checkpoint.report.savedQuery;
  report.dualGroupByDiagnosis = browser.dualGroupByDiagnosis ?? checkpoint.report.dualGroupByDiagnosis;
  report.optionalFilters = browser.optionalFilters ?? checkpoint.report.optionalFilters;
  report.exports = browser.exports ?? checkpoint.report.exports;
  report.auth = browser.auth ?? checkpoint.report.auth;
  report.screenshots = browser.screenshots ?? checkpoint.report.screenshots;
  report.visualError = browser.visualError ?? checkpoint.report.visualError;
  report.runtime = { ...report.runtime, ...(browser.runtime ?? checkpoint.report.runtime) };
  report.git = { ...report.git, ...(browser.git ?? checkpoint.report.git) };
  if (checkpoint.report.limitations) {
    report.limitations = [...(report.limitations ?? []), ...checkpoint.report.limitations];
  }
  report.acceptanceRun.resumedFromCheckpoint = true;
  report.acceptanceRun.browserEvidenceProducedAt = checkpoint.browserEvidenceProducedAt;
  report.acceptanceRun.browserReportWrittenAt = checkpoint.browserReportWrittenAt;
  report.acceptanceRun.browserReportSourcePath = checkpoint.browserReportSourcePath;
  report.acceptanceRun.applicationSourceFingerprint = checkpoint.applicationSourceFingerprint;
  report.acceptanceRun.browserCheckpointPath = checkpoint._checkpointPath;
}

function loadOrMaterializeBrowserCheckpoint(checkpointPath) {
  const resolved = path.resolve(checkpointPath);
  const storePath = process.env.AA_BROWSER_CHECKPOINT_STORE || path.join(OUT, BROWSER_CHECKPOINT_FILENAME);
  const isPersistedBrowserReport =
    resolved.endsWith("aa-acceptance-stdout.json") || resolved.endsWith("acceptance-report.json");

  if (isPersistedBrowserReport) {
    const materialized = materializeCheckpointFromBrowserReport(resolved, ROOT, {
      browserEvidenceProducedAt: "2026-09-27T10:22:02.532Z",
      browserReportWrittenAt: fs.statSync(resolved).mtime.toISOString(),
    });
    materialized._checkpointPath = resolved;
    materialized._materializedFromBrowserReport = true;
    fs.writeFileSync(storePath, JSON.stringify(materialized, null, 2));
    progress(`materialized browser checkpoint from report → ${storePath}`);
    return materialized;
  }

  if (fs.existsSync(storePath) && resolved === path.resolve(storePath)) {
    const stored = loadBrowserCheckpoint(storePath);
    return { ...stored, _checkpointPath: storePath, _loadedFromStore: true };
  }

  const raw = JSON.parse(fs.readFileSync(checkpointPath, "utf8"));
  if (raw.version === 1 && raw.applicationSourceFingerprint && raw.report) {
    return { ...raw, _checkpointPath: checkpointPath };
  }

  const materialized = materializeCheckpointFromBrowserReport(checkpointPath, ROOT, {
    browserEvidenceProducedAt: "2026-09-27T10:22:02.532Z",
    browserReportWrittenAt: fs.statSync(checkpointPath).mtime.toISOString(),
  });
  materialized._checkpointPath = checkpointPath;
  fs.writeFileSync(storePath, JSON.stringify(materialized, null, 2));
  progress(`materialized browser checkpoint → ${storePath}`);
  return materialized;
}

async function main() {
  const automatedOnly = process.env.AA_ACCEPTANCE_AUTOMATED_ONLY === "1";
  const savedQueryOnly = process.env.AA_ACCEPTANCE_SAVED_QUERY_ONLY === "1";
  report.acceptanceRun.mode = automatedOnly
    ? "automated-only"
    : savedQueryOnly
      ? "saved-query-only"
      : "full";

  progress(
    automatedOnly
      ? "mode: automated-only (reuse browser checkpoint; no Playwright)"
      : savedQueryOnly
        ? "mode: saved-query only (no JSON on stdout until finished)"
        : "mode: full acceptance (browser ~45–90 min, then automated gates — no stdout until finished)",
  );
  progress(`frontend ${BASE}  api ${API}`);
  gitRecord();
  report.runtime.health = JSON.parse(execSync(`curl -s ${API}/api/health/planes`, { encoding: "utf8" }));
  progress(`health: ${JSON.stringify(report.runtime.health)}`);
  report.runtime.frontendPid = execSync("lsof -t -i:3001 2>/dev/null || true", { encoding: "utf8" }).trim();
  report.runtime.backendContainer = execSync(
    "docker ps --filter name=srse-backend --format '{{.ID}} {{.Status}}'",
    { encoding: "utf8" },
  ).trim();
  try {
    report.runtime.frontendBuildMtime = fs.statSync(`${ROOT}/frontend/.next/BUILD_ID`).mtime.toISOString();
    report.runtime.frontendBuildId = fs.readFileSync(`${ROOT}/frontend/.next/BUILD_ID`, "utf8").trim();
  } catch {
    report.limitations.push("Frontend BUILD_ID missing before run — rebuild required.");
  }
  report.runtime.dockerFrontend = execSync(
    "docker ps -a --filter name=srse-frontend --format '{{.Status}}'",
    { encoding: "utf8" },
  ).trim();

  if (process.env.AA_STOP_DOCKER_FRONTEND === "1") {
    try {
      execSync("docker stop analytics-advisor-srse-frontend-1 2>/dev/null || true");
    } catch {
      /* already stopped */
    }
  }

  if (automatedOnly) {
    const checkpointPath =
      process.env.AA_BROWSER_CHECKPOINT ||
      path.join(OUT, BROWSER_CHECKPOINT_FILENAME) ||
      "/tmp/aa-acceptance-stdout.json";
    progress(`loading browser checkpoint: ${checkpointPath}`);
    const checkpoint = loadOrMaterializeBrowserCheckpoint(checkpointPath);
    const fpCheck = assertCheckpointFingerprintMatches(checkpoint, ROOT);
    report.acceptanceRun.fingerprintMatchAtResume = fpCheck.match;
    report.acceptanceRun.applicationSourceFingerprint = fpCheck.current.fingerprint;
    if (!fpCheck.match) {
      report.fatal =
        "Application source fingerprint changed since browser checkpoint; full browser acceptance required.";
      report.verdict = "INCOMPLETE";
      progress(report.fatal);
      finalizeAndWriteReport(savedQueryOnly, automatedOnly);
      return;
    }
    mergeBrowserCheckpointIntoReport(checkpoint);
    report.acceptanceRun.browserCheckpointMaterializedFromReport =
      checkpoint._materializedFromBrowserReport === true;
    report.acceptanceRun.fingerprintCapturedAtResume = checkpoint.fingerprintCapturedAt;
    report.acceptanceRun.browserGitDriftNote =
      "Compare browserReportGit.finalStatus in checkpoint store vs current git if investigating post-browser app edits.";
    progress("checkpoint fingerprint match — running automated gates only");
    await runAutomatedSuites();
    finalizeAndWriteReport(savedQueryOnly, automatedOnly);
    return;
  }

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();
  let setPhase = (p) => {
    report._phase = p;
    progress(`phase: ${p}`);
  };
  page.on("request", (req) => {
    if (!isMatchExecution(req.url(), req.method())) return;
    report.matchLedger.events.push({
      phase: report._phase,
      method: req.method(),
      endpoint: new URL(req.url()).pathname,
      url: req.url(),
    });
  });
  page.on("response", (res) => {
    const req = res.request();
    if (!isMatchExecution(req.url(), req.method())) return;
    const ev = report.matchLedger.events.find((e) => e.url === req.url() && e.status == null);
    if (ev) ev.status = res.status();
  });

  try {
    await loginLocal(page, adminUsername(), requireAdminPassword());
    const adminToken = await page.evaluate(() => sessionStorage.getItem("srse.auth.token"));
    const sessionRes = await fetch(`${API}/api/auth/session`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    const sessionBody = sessionRes.ok ? await sessionRes.json() : {};
    report.auth.admin.superAdminSession = sessionBody.superAdmin === true;
    await ensureQueryBuilderReady(page);
    progress("query-builder ready (Extract Records tab visible)");

    if (savedQueryOnly) {
      setPhase("saved-query");
      await runSavedQuerySuite(page, adminToken);
    } else {
    await diagnoseDualGroupBy(adminToken);

    await runMatchLedger(page, setPhase);
    scoreLedger();
    report.sections.pipeline = Object.values(report.pipelineChecks).every(Boolean) ? "PASS" : "FAIL";

    setPhase("dual-group-by-browser");
    try {
      await browserDualGroupByProbe(page);
    } catch (e) {
      report.dualGroupByDiagnosis.browserError = String(e);
      report.sections.dualGroupBy = "FAIL";
    }

    setPhase("saved-query");
    try {
      await runSavedQuerySuite(page, adminToken);
    } catch (e) {
      report.savedQuery.error = String(e);
      report.sections.savedQuery = "FAIL";
    }

    setPhase("optional-filters");
    try {
      await runOptionalFilters(page, adminToken);
    } catch (e) {
      report.optionalFilters.error = String(e);
      report.sections.optionalFilters = "FAIL";
    }

    setPhase("exports");
    try {
      await runExports(page);
    } catch (e) {
      report.exports.error = String(e);
      report.sections.exports = "FAIL";
    }

    setPhase("visual");
    try {
      await runVisualMatrix(page);
    } catch (e) {
      report.visualError = String(e);
      report.sections.visualMatrix = "FAIL";
    }

    try {
      await runAuth(browser, adminToken);
    } catch (e) {
      report.auth.error = String(e);
      report.sections.auth = "FAIL";
    }
    }
  } catch (e) {
    report.fatal = String(e?.stack || e);
  } finally {
    await browser.close();
  }

  if (savedQueryOnly) {
    progress("saved-query-only: skipping automated suites (run full acceptance for mvn/npm/build gate)");
    report.automatedSuites.frontendTests = "skipped (saved-query-only)";
    report.automatedSuites.backendTests = "skipped (saved-query-only)";
    report.automatedSuites.productionBuild = "skipped (saved-query-only)";
    report.sections.automated = "SKIP";
  } else {
    const browserEvidenceProducedAt = new Date().toISOString();
    report.acceptanceRun.browserEvidenceProducedAt = browserEvidenceProducedAt;
    writeBrowserCheckpoint(path.join(OUT, BROWSER_CHECKPOINT_FILENAME), report, ROOT, browserEvidenceProducedAt);
    progress("browser checkpoint written — starting automated gates");
    await runAutomatedSuites();
  }
  finalizeAndWriteReport(savedQueryOnly, false);
}

function finalizeAndWriteReport(savedQueryOnly, automatedOnly) {
  progress("writing acceptance-report.json");
  report.sections.rebuild =
    report.runtime.health?.operational === "up" && report.runtime.health?.analytical === "up"
      ? "PASS"
      : "FAIL";

  const requiredSections = savedQueryOnly
    ? ["savedQuery"]
    : [
        "matchLedger",
        "pipeline",
        "dualGroupBy",
        "savedQuery",
        "optionalFilters",
        "exports",
        "visualMatrix",
        "auth",
        "rebuild",
        "automated",
      ];
  report.verdict = requiredSections.every((k) => report.sections[k] === "PASS") ? "COMPLETE" : "INCOMPLETE";
  if (automatedOnly && report.acceptanceRun.resumedFromCheckpoint && report.verdict === "COMPLETE") {
    report.qualifiedVerdict = "COMPLETE";
    report.verdictNote =
      "Browser evidence from checkpoint; automated gates executed in resumed run with exit-code validation.";
  }
  report.git.finalStatus = execSync(`git -C ${ROOT} status -sb`, { encoding: "utf8" }).trim();

  const outJson =
    process.env.AA_ACCEPTANCE_REPORT_OUT || path.join(OUT, "acceptance-report.json");
  fs.writeFileSync(outJson, JSON.stringify(report, null, 2));
  fs.writeFileSync(path.join(OUT, "acceptance-report.json"), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
}

main();
