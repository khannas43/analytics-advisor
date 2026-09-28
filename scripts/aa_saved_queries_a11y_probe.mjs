#!/usr/bin/env node
/**
 * Targeted probe: exactly one saved-query region in the active Query Builder tab panel.
 */
import { chromium } from "playwright";

const BASE = process.env.AA_BASE_URL || "http://localhost:3000";

function requireAdminPassword() {
  const password = process.env.AA_ADMIN_PASS?.trim();
  if (!password) throw new Error("AA_ADMIN_PASS is required");
  return password;
}

async function login(page) {
  await page.goto(`${BASE}/login`);
  await page.getByLabel("Username").fill(process.env.AA_ADMIN_USER?.trim() || "superadmin");
  await page.locator("#login-password").fill(requireAdminPassword());
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL((u) => !u.pathname.includes("/login"), { timeout: 60000 });
}

async function countSavedRegionsInActivePanel(page) {
  return page.evaluate(() => {
    const tab = document.querySelector('[role="tab"][aria-selected="true"]');
    const panelId = tab?.getAttribute("aria-controls");
    const panel = panelId ? document.getElementById(panelId) : null;
    if (!panel) return { error: "no active panel", count: -1 };
    const regions = [...panel.querySelectorAll('[role="region"]')].filter((r) =>
      /saved queries/i.test(r.getAttribute("aria-label") ?? ""),
    );
    return {
      tab: tab?.getAttribute("aria-label"),
      panelId,
      labels: regions.map((r) => r.getAttribute("aria-label")),
      count: regions.length,
    };
  });
}

async function main() {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await login(page);
  await page.goto(`${BASE}/query-builder`);
  await page.getByRole("tab", { name: "Extract Records tab" }).waitFor();

  const extract = await countSavedRegionsInActivePanel(page);
  if (extract.count !== 1 || extract.labels?.[0] !== "Extract saved queries") {
    console.error(JSON.stringify({ pass: false, phase: "extract", extract }, null, 2));
    process.exit(1);
  }

  await page.getByRole("button", { name: "Single Source mode" }).click();
  const pick = page.locator("select#extract-source-catalog").locator("xpath=ancestor::div[contains(@class,'pick')][1]");
  await pick.getByLabel("Catalog").selectOption("iceberg");
  await pick.getByLabel("Schema").selectOption("srse");
  await pick.getByLabel("Table").selectOption("beneficiary");
  await page.locator("#qb1 .attr-grid").waitFor();
  await page.getByRole("checkbox", { name: /^district\b/i }).check();
  await page.getByRole("button", { name: "Run Query" }).click();
  await page.waitForFunction(
    () => {
      const b = [...document.querySelectorAll("#qb1 button")].find((x) => x.textContent?.includes("Run Query"));
      return b && !b.textContent?.includes("Running");
    },
    { timeout: 300000 },
  );
  await page.getByRole("button", { name: "Go to Report Analysis" }).click();
  await page.getByRole("tab", { name: "Report Analysis tab", selected: true }).waitFor();

  const report = await countSavedRegionsInActivePanel(page);
  if (report.count !== 1 || report.labels?.[0] !== "Report saved queries") {
    console.error(JSON.stringify({ pass: false, phase: "report", report }, null, 2));
    process.exit(1);
  }

  await page.getByRole("tab", { name: "Extract Records tab" }).click();
  const back = await countSavedRegionsInActivePanel(page);
  const pass =
    back.count === 1 &&
    back.labels?.[0] === "Extract saved queries" &&
    extract.count === 1 &&
    report.count === 1;
  console.log(JSON.stringify({ pass, extract, report, back }, null, 2));
  await browser.close();
  process.exit(pass ? 0 : 1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
