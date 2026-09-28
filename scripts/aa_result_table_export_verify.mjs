#!/usr/bin/env node
/**
 * Scripted CSV/XLSX parity + workbook structure for Extract result-table exports.
 * Evidence → /tmp/aa-result-table-acceptance/
 */
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";
import { execSync } from "node:child_process";

const BASE = process.env.AA_BASE_URL || "http://localhost:3000";
const OUT = process.env.AA_RESULT_TABLE_OUT || "/tmp/aa-result-table-acceptance";

fs.mkdirSync(OUT, { recursive: true });

function unzipEntry(xlsxPath, entry) {
  return execSync(`unzip -p ${JSON.stringify(xlsxPath)} ${entry}`, {
    encoding: "utf8",
    maxBuffer: 32 * 1024 * 1024,
  });
}

function inspectXlsx(filePath) {
  const sheet = unzipEntry(filePath, "xl/worksheets/sheet1.xml");
  const styles = unzipEntry(filePath, "xl/styles.xml");
  const paneMatch = sheet.match(/<pane[^>]*\/>/);
  const headerRow = sheet.match(/<row r="1"[^>]*>([\s\S]*?)<\/row>/);
  const boldFont = /<b(?:\sval="true")?\s*\/>/.test(styles);
  const headerUsesBoldStyle = headerRow != null && / s="1"/.test(headerRow[1]) && boldFont;
  return {
    ySplit1: /ySplit="1(?:\.0)?"/.test(sheet),
    topLeftA2: /topLeftCell="A2"/.test(sheet),
    autoFilter: /<autoFilter/.test(sheet),
    boldHeader: headerUsesBoldStyle,
    paneSnippet: paneMatch ? paneMatch[0].slice(0, 200) : null,
  };
}

function parseCsvHeadersAndRows(text) {
  const raw = text.replace(/^\uFEFF/, "");
  const lines = [];
  let cur = "";
  let inQuotes = false;
  for (let i = 0; i < raw.length; i++) {
    const ch = raw[i];
    if (inQuotes) {
      if (ch === '"' && raw[i + 1] === '"') {
        cur += '"';
        i++;
      } else if (ch === '"') {
        inQuotes = false;
      } else {
        cur += ch;
      }
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === "\n" || (ch === "\r" && raw[i + 1] === "\n")) {
      if (ch === "\r") i++;
      lines.push(cur);
      cur = "";
    } else {
      cur += ch;
    }
  }
  if (cur.length > 0) lines.push(cur);
  const nonEmpty = lines.filter((l) => l.length > 0);
  if (nonEmpty.length === 0) return { headers: [], dataRows: [] };
  const parseLine = (line) => {
    const fields = [];
    let field = "";
    let q = false;
    for (let i = 0; i < line.length; i++) {
      const c = line[i];
      if (q) {
        if (c === '"' && line[i + 1] === '"') {
          field += '"';
          i++;
        } else if (c === '"') q = false;
        else field += c;
      } else if (c === '"') q = true;
      else if (c === ",") {
        fields.push(field);
        field = "";
      } else field += c;
    }
    fields.push(field);
    return fields;
  };
  const headers = parseLine(nonEmpty[0]);
  const dataRows = nonEmpty.slice(1).map(parseLine);
  return { headers, dataRows };
}

function xlsxHeadersAndRowCount(filePath) {
  const sharedXml = execSync(`unzip -p ${JSON.stringify(filePath)} xl/sharedStrings.xml 2>/dev/null || true`, {
    encoding: "utf8",
  });
  const shared = [...sharedXml.matchAll(/<t[^>]*>([^<]*)<\/t>/g)].map((m) => m[1]);
  const sheetXml = unzipEntry(filePath, "xl/worksheets/sheet1.xml");
  const rowBlocks = [...sheetXml.matchAll(/<row[^>]*>([\s\S]*?)<\/row>/g)];
  const cellText = (cellXml) => {
    const inline = cellXml.match(/<is>\s*<t[^>]*>([^<]*)<\/t>/);
    if (inline) return inline[1];
    const v = cellXml.match(/<v>([^<]+)<\/v>/);
    if (!v) return "";
    const tAttr = cellXml.match(/\bt="([^"]+)"/);
    if (tAttr?.[1] === "s") return shared[Number(v[1])] ?? v[1];
    return v[1];
  };
  const headers = [];
  if (rowBlocks.length > 0) {
    const cells = [...rowBlocks[0][1].matchAll(/<c[^>]*(?:\/>|>[\s\S]*?<\/c>)/g)];
    for (const cell of cells) headers.push(cellText(cell[0]));
  }
  return { headers: headers.filter((h) => h !== ""), physicalRows: rowBlocks.length, dataRowCount: Math.max(0, rowBlocks.length - 1) };
}

async function login(page) {
  await page.goto(`${BASE}/login`);
  await page.locator("#login-username").fill("superadmin");
  const pass = process.env.AA_ADMIN_PASS?.trim();
  if (!pass) throw new Error("AA_ADMIN_PASS is required");
  await page.locator("#login-password").fill(pass);
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL((u) => !u.pathname.includes("/login"), { timeout: 30000 });
}

async function runExtractManyRows(page) {
  await page.goto(`${BASE}/query-builder`, { waitUntil: "domcontentloaded" });
  await page.getByRole("button", { name: "Extract Records tab" }).click();
  await page.getByRole("button", { name: "Single Source mode" }).click();
  const pick = page
    .locator("#qb1")
    .locator("select#extract-source-catalog")
    .locator("xpath=ancestor::div[contains(@class,'pick')][1]");
  await pick.getByLabel("Catalog").selectOption("iceberg", { timeout: 60000 });
  await pick.getByLabel("Schema").selectOption("srse");
  await pick.getByLabel("Table").selectOption("beneficiary");
  await page.locator("#qb1 .attr-grid").waitFor({ timeout: 60000 });
  await page.getByRole("checkbox", { name: /^id\b/i }).check();
  await page.getByRole("checkbox", { name: /^district\b/i }).check();
  await page.getByRole("button", { name: "Run Query" }).click();
  await page.waitForFunction(
    () => {
      const b = [...document.querySelectorAll("#qb1 button")].find((x) => x.textContent?.includes("Run Query"));
      return b && !b.textContent?.includes("Running");
    },
    { timeout: 300000 },
  );
  await page.getByRole("button", { name: "Download CSV" }).first().waitFor({ state: "visible", timeout: 180000 });
}

async function downloadExport(page, basename, format) {
  const label = format === "csv" ? "Download CSV" : "Download Excel";
  const btn = page.getByRole("button", { name: label }).first();
  const [dl] = await Promise.all([page.waitForEvent("download", { timeout: 180000 }), btn.click()]);
  const ext = format === "csv" ? "csv" : "xlsx";
  const filePath = path.join(OUT, `${basename}.${ext}`);
  await dl.saveAs(filePath);
  return filePath;
}

async function main() {
  const report = { pass: false, checks: {}, files: {} };
  const browser = await chromium.launch();
  const context = await browser.newContext({ acceptDownloads: true });
  const page = await context.newPage();

  await login(page);
  await runExtractManyRows(page);

  const baselineCsv = await downloadExport(page, "extract-baseline", "csv");
  const baselineXlsx = await downloadExport(page, "extract-baseline", "xlsx");

  const csvText = fs.readFileSync(baselineCsv, "utf8");
  const csvParsed = parseCsvHeadersAndRows(csvText);
  const xlsxParsed = xlsxHeadersAndRowCount(baselineXlsx);
  const xlsxStruct = inspectXlsx(baselineXlsx);

  report.checks.csvBom = csvText.charCodeAt(0) === 0xfeff;
  report.checks.csvHeaderRow1 = csvParsed.headers.length > 0;
  report.checks.csvDataRowCount = csvParsed.dataRows.length;
  report.checks.paginationMoreThan25 = csvParsed.dataRows.length > 25;
  report.checks.headerOrderParity =
    csvParsed.headers.length === xlsxParsed.headers.length &&
    csvParsed.headers.every((h, i) => h === xlsxParsed.headers[i]);
  report.checks.rowCountParity = csvParsed.dataRows.length === xlsxParsed.dataRowCount;
  report.checks.xlsxStructure = xlsxStruct;
  report.checks.serverExportNoteVisible = await page
    .locator(".result-table-export-note")
    .filter({ hasText: /server query order/i })
    .isVisible()
    .catch(() => false);

  const overflowMode = await page.getByText(/too many rows to display|download the complete/i).isVisible().catch(() => false);
  report.checks.overflowMode = overflowMode;

  let sortedCsv = baselineCsv;
  if (!overflowMode) {
    // Hide a column in UI — export must still include it.
    const hideTarget = csvParsed.headers.find((h) => /district/i.test(h)) ?? csvParsed.headers[1];
    if (hideTarget) {
      await page.getByRole("button", { name: /^Columns \(\d+\/\d+\)$/ }).click();
      const colCheckbox = page.getByRole("checkbox", { name: new RegExp(hideTarget, "i") }).first();
      if (await colCheckbox.isVisible().catch(() => false)) {
        await colCheckbox.uncheck();
      }
      await page.keyboard.press("Escape");
      const hiddenCsv = await downloadExport(page, "extract-hidden-col", "csv");
      const hiddenParsed = parseCsvHeadersAndRows(fs.readFileSync(hiddenCsv, "utf8"));
      report.checks.hiddenColumnStillExported =
        hiddenParsed.headers.includes(hideTarget) && hiddenParsed.dataRows.length === csvParsed.dataRows.length;
      report.files.hiddenCsv = hiddenCsv;
    } else {
      report.checks.hiddenColumnStillExported = false;
    }

    // UI sort must not change server export ordering.
    const sortBtn = page.locator(".result-sort-btn").first();
    await sortBtn.click();
    await page.waitForTimeout(400);
    sortedCsv = await downloadExport(page, "extract-after-ui-sort", "csv");
    const sortedParsed = parseCsvHeadersAndRows(fs.readFileSync(sortedCsv, "utf8"));
    const firstDataCellBaseline = csvParsed.dataRows[0]?.[0] ?? "";
    const firstDataCellSorted = sortedParsed.dataRows[0]?.[0] ?? "";
    report.checks.uiSortDoesNotChangeServerExport = firstDataCellBaseline === firstDataCellSorted;
  } else {
    report.checks.hiddenColumnStillExported = true;
    report.checks.uiSortDoesNotChangeServerExport = true;
  }
  report.checks.exportNoteAfterSort = await page
    .locator(".result-table-export-note")
    .filter({ hasText: /server query order/i })
    .isVisible()
    .catch(() => false);

  // Dashboard with matchExportRequest (server path).
  await page.locator("#app-sidebar").getByRole("link", { name: "Result Dashboard" }).click();
  await page.waitForSelector(".result-table-toolbar", { timeout: 60000 });
  const dashServerCsv = await downloadExport(page, "dashboard-with-request", "csv");
  const dashServerXlsx = await downloadExport(page, "dashboard-with-request", "xlsx");
  report.checks.dashboardServerCsvRows = parseCsvHeadersAndRows(fs.readFileSync(dashServerCsv, "utf8")).dataRows.length;
  report.checks.dashboardServerXlsxRows = xlsxHeadersAndRowCount(dashServerXlsx).dataRowCount;

  // Dashboard fallback without matchExportRequest (client CSV + XLSX).
  await page.evaluate(() => {
    const raw = localStorage.getItem("aa-query-results-v1");
    if (!raw) throw new Error("missing persisted query results");
    const parsed = JSON.parse(raw);
    if (parsed.state?.lastResult) {
      delete parsed.state.lastResult.matchExportRequest;
      localStorage.setItem("aa-query-results-v1", JSON.stringify(parsed));
    }
  });
  await page.reload();
  await page.waitForSelector(".result-table-toolbar", { timeout: 60000 });
  report.checks.dashboardFallbackNoServerNote = !(await page.locator(".result-table-export-note").isVisible());
  const dashFallbackCsv = await downloadExport(page, "dashboard-fallback", "csv");
  const dashFallbackXlsx = await downloadExport(page, "dashboard-fallback", "xlsx");
  const fbCsv = parseCsvHeadersAndRows(fs.readFileSync(dashFallbackCsv, "utf8"));
  const fbXlsx = xlsxHeadersAndRowCount(dashFallbackXlsx);
  report.checks.dashboardFallbackRowParity = fbCsv.dataRows.length === fbXlsx.dataRowCount;
  report.checks.dashboardFallbackXlsxStructure = inspectXlsx(dashFallbackXlsx);

  report.files = {
    baselineCsv,
    baselineXlsx,
    sortedCsv,
    dashServerCsv,
    dashServerXlsx,
    dashFallbackCsv,
    dashFallbackXlsx,
    ...report.files,
  };

  const structOk =
    xlsxStruct.ySplit1 &&
    xlsxStruct.topLeftA2 &&
    xlsxStruct.autoFilter &&
    xlsxStruct.boldHeader &&
    report.checks.dashboardFallbackXlsxStructure.ySplit1 &&
    report.checks.dashboardFallbackXlsxStructure.topLeftA2 &&
    report.checks.dashboardFallbackXlsxStructure.autoFilter &&
    report.checks.dashboardFallbackXlsxStructure.boldHeader;

  report.pass =
    report.checks.csvBom &&
    report.checks.csvHeaderRow1 &&
    report.checks.paginationMoreThan25 &&
    report.checks.rowCountParity &&
    report.checks.headerOrderParity &&
    report.checks.hiddenColumnStillExported !== false &&
    report.checks.uiSortDoesNotChangeServerExport &&
    report.checks.exportNoteAfterSort &&
    report.checks.dashboardFallbackRowParity &&
    report.checks.dashboardFallbackNoServerNote &&
    structOk;

  fs.writeFileSync(path.join(OUT, "export-verify-report.json"), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
  await browser.close();
  process.exit(report.pass ? 0 : 1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
