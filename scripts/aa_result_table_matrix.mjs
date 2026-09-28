#!/usr/bin/env node
/**
 * Browser matrix for shared result-table controls (Extract / Report / Dashboard).
 */
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";

const BASE = process.env.AA_BASE_URL || "http://localhost:3000";
const OUT = process.env.AA_RESULT_TABLE_MATRIX_OUT || "/tmp/aa-result-table-acceptance";

fs.mkdirSync(OUT, { recursive: true });

async function login(page) {
  await page.goto(`${BASE}/login`);
  await page.locator("#login-username").fill("superadmin");
  const pass = process.env.AA_ADMIN_PASS?.trim();
  if (!pass) throw new Error("AA_ADMIN_PASS is required");
  await page.locator("#login-password").fill(pass);
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL((u) => !u.pathname.includes("/login"), { timeout: 30000 });
}

async function waitRunDone(page) {
  await page.waitForFunction(
    () => {
      const b = [...document.querySelectorAll("#qb1 button, #qb2 button")].find((x) =>
        (x.textContent || "").includes("Run Query"),
      );
      return b && !b.textContent?.includes("Running");
    },
    { timeout: 300000 },
  );
}

async function main() {
  const checks = {};
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await login(page);

  // Report grouped (pagination + sort on small set)
  await page.goto(`${BASE}/query-builder`);
  await page.getByRole("button", { name: "Single Source mode" }).click();
  const pick = page.locator("select#extract-source-catalog").locator("xpath=ancestor::div[contains(@class,'pick')][1]");
  await pick.getByLabel("Catalog").selectOption("iceberg");
  await pick.getByLabel("Schema").selectOption("srse");
  await pick.getByLabel("Table").selectOption("beneficiary");
  await page.locator("#qb1 .attr-grid").waitFor();
  await page.getByRole("checkbox", { name: /^district\b/i }).check();
  await page.getByRole("button", { name: "Run Query" }).click();
  await waitRunDone(page);
  await page.getByRole("button", { name: "Go to Report Analysis" }).click();
  await page.locator("#report-group-enabled").check();
  await page.waitForFunction(
    () => {
      const sel = document.querySelector("#report-group-by-col");
      return sel && [...sel.options].some((o) => /district/i.test(o.textContent || o.value));
    },
    { timeout: 60000 },
  );
  await page.locator("#report-group-by-col").selectOption({ index: 1 });
  await page.getByRole("button", { name: "Apply report options" }).click();
  await page.getByRole("button", { name: "Download CSV" }).first().waitFor({ timeout: 180000 });

  const pageSize = page.locator("#result-rows-per-page");
  checks.defaultPageSize25 = (await pageSize.inputValue()) === "25";
  for (const size of ["10", "50", "100"]) {
    await pageSize.selectOption(size);
    checks[`pageSize${size}`] = (await pageSize.inputValue()) === size;
  }
  await pageSize.selectOption("25");

  const next = page.getByRole("button", { name: "Next" });
  const prev = page.getByRole("button", { name: "Previous" });
  checks.nextDisabledOnSinglePage = await next.isDisabled();
  checks.prevDisabledOnFirst = await prev.isDisabled();

  const sortBtn = page.locator(".result-sort-btn").first();
  await sortBtn.click();
  checks.ariaSortAsc = (await page.locator("th[aria-sort]").first().getAttribute("aria-sort")) === "ascending";
  await sortBtn.click();
  checks.ariaSortDesc = (await page.locator("th[aria-sort]").first().getAttribute("aria-sort")) === "descending";
  await sortBtn.click();
  checks.ariaSortNone = (await page.locator("th[aria-sort]").first().getAttribute("aria-sort")) === "none";

  const csvButtons = await page.getByRole("button", { name: "Download CSV" }).count();
  const xlsxButtons = await page.getByRole("button", { name: "Download Excel" }).count();
  checks.reportExportCsvExcel = csvButtons >= 1 && xlsxButtons >= 1;
  checks.noDuplicateExportControls = csvButtons === 1 && xlsxButtons === 1;

  // Extract overflow path
  await page.getByRole("button", { name: "Extract Records tab" }).click();
  await page.getByRole("checkbox", { name: /^id\b/i }).check();
  await page.getByRole("button", { name: "Run Query" }).click();
  await waitRunDone(page);
  checks.extractOverflow = await page.getByText(/Too many rows to display/i).isVisible();
  checks.extractOverflowCsvExcel =
    (await page.getByRole("button", { name: "Download CSV" }).count()) >= 1 &&
    (await page.getByRole("button", { name: "Download Excel" }).count()) >= 1;

  // Dashboard with request + fallback
  await page.locator("#app-sidebar").getByRole("link", { name: "Result Dashboard" }).click();
  await page.getByRole("button", { name: "Download Excel" }).first().waitFor();
  checks.dashboardWithRequest = true;

  await page.evaluate(() => {
    const raw = localStorage.getItem("aa-query-results-v1");
    const parsed = JSON.parse(raw);
    delete parsed.state.lastResult.matchExportRequest;
    localStorage.setItem("aa-query-results-v1", JSON.stringify(parsed));
  });
  await page.reload();
  checks.dashboardFallbackExcel = await page.getByRole("button", { name: "Download Excel" }).isVisible();

  const report = { checks, pass: Object.values(checks).every(Boolean) };
  fs.writeFileSync(path.join(OUT, "result-table-matrix-report.json"), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
  await browser.close();
  process.exit(report.pass ? 0 : 1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
