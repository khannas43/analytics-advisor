#!/usr/bin/env node
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";

const BASE = process.env.AA_BASE_URL || "http://localhost:3000";
const OUT =
  process.env.AA_RESULT_TABLE_VISUAL_OUT ||
  path.join("/Users/sameerkhanna/Documents/Projects/analytics-advisor/docs/screenshots-result-table-controls");

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

async function openReportGrid(page) {
  await page.goto(`${BASE}/query-builder`);
  await page.getByRole("button", { name: "Extract Records tab" }).click();
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
  await page.locator("#report-group-enabled").check();
  await page.waitForFunction(
    () => document.querySelector("#report-group-by-col")?.options?.length > 1,
    { timeout: 60000 },
  );
  await page.locator("#report-group-by-col").selectOption({ index: 1 });
  await page.getByRole("button", { name: "Apply report options" }).click();
  await page.locator(".result-table-toolbar").first().waitFor({ state: "visible", timeout: 180000 });
}

async function setTheme(page, mode) {
  const lightBtn = page.getByRole("button", { name: /^Light$|^लाइट$/ });
  const darkBtn = page.getByRole("button", { name: /^Dark$|^डार्क$/ });
  if (mode === "light" && (await lightBtn.isVisible().catch(() => false))) {
    await lightBtn.click();
  }
  if (mode === "dark" && (await darkBtn.isVisible().catch(() => false))) {
    await darkBtn.click();
  }
}

async function capture(page, name, width) {
  await page.setViewportSize({ width, height: width <= 400 ? 844 : 900 });
  await page.locator(".result-table-toolbar").first().scrollIntoViewIfNeeded();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
  const file = path.join(OUT, `${name}.png`);
  await page.screenshot({ path: file, fullPage: false });
  return { file, overflowOk: overflow };
}

async function main() {
  const report = { shots: [], pass: false };
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await login(page);
  await openReportGrid(page);

  for (const [width, tag] of [
    [1280, "1280"],
    [390, "390"],
  ]) {
    for (const theme of ["light", "dark"]) {
      await setTheme(page, theme);
      const { file, overflowOk } = await capture(page, `${tag}-${theme}`, width);
      report.shots.push({ file, width, theme, overflowOk });
    }
  }

  report.pass = report.shots.length === 4 && report.shots.every((s) => s.overflowOk);
  fs.writeFileSync(path.join(OUT, "visual-matrix-report.json"), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
  await browser.close();
  process.exit(report.pass ? 0 : 1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
