/**
 * Quick layout probe for Report controls panel (1280px + 390px).
 * Usage: node frontend/scripts/reportControlsLayout.mjs
 */
import { chromium } from "playwright";

const BASE = process.env.AA_BASE_URL || "http://localhost:3001";

async function login(page) {
  await page.goto(`${BASE}/login`);
  await page.getByLabel("Username").fill(process.env.AA_ADMIN_USER || "superadmin");
  await page.getByLabel("Password").fill(process.env.AA_ADMIN_PASS || "Supradmin@123");
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.waitForURL((u) => !u.pathname.includes("/login"));
}

async function runExtract(page) {
  await page.goto(`${BASE}/query-builder`);
  await page.getByRole("button", { name: "Single Source mode" }).click();
  const pick = page.locator("#qb1 select#extract-source-catalog").locator("xpath=ancestor::div[contains(@class,'pick')][1]");
  await pick.getByLabel("Catalog").selectOption("iceberg");
  await pick.getByLabel("Schema").selectOption("srse");
  await pick.getByLabel("Table").selectOption("beneficiary");
  await page.getByRole("checkbox", { name: /^district\b/i }).check();
  await page.getByRole("button", { name: "Run Query" }).click();
  await page.waitForFunction(
    () => {
      const b = [...document.querySelectorAll("#qb1 button")].find((x) => x.textContent?.includes("Run Query"));
      return b && !/Running/i.test(b.textContent || "");
    },
    undefined,
    { timeout: 600000 },
  );
  await page.getByRole("button", { name: "Report Analysis tab" }).click();
  await page.locator("#qb2").getByText(/iceberg\.srse\.beneficiary/i).first().waitFor({ timeout: 60000 });
}

async function probeViewport(page, width, height) {
  await page.setViewportSize({ width, height });
  await page.getByRole("button", { name: /Filters & Settings|फ़िल्टर/i }).click();
  const panel = page.locator("#qb2 .report-controls-panel:not(.collapsed)");
  await panel.waitFor({ state: "visible" });

  const layout = await page.evaluate(() => {
    const panelEl = document.querySelector("#qb2 .report-controls-panel:not(.collapsed)");
    const group = document.querySelector("#report-group-enabled");
    const dedup = document.querySelector("#hide-duplicate-records-embed");
    const pick = (el) => {
      if (!el) return null;
      const r = el.getBoundingClientRect();
      const cx = r.left + r.width / 2;
      const cy = r.top + r.height / 2;
      const top = document.elementFromPoint(cx, cy);
      return {
        rect: { x: r.x, y: r.y, w: r.width, h: r.height },
        topTag: top?.tagName,
        topId: top?.id,
        hitsSelf: top === el || el.contains(top),
      };
    };
    return {
      panel: pick(panelEl),
      group: pick(group),
      dedup: pick(dedup),
    };
  });

  for (const key of ["group"]) {
    const row = layout[key];
    if (!row?.hitsSelf) {
      throw new Error(`${width}x${height}: ${key} checkbox hit-test failed: ${JSON.stringify(row)}`);
    }
  }
  if (layout.dedup && !layout.dedup.hitsSelf) {
    throw new Error(`${width}x${height}: dedup checkbox hit-test failed: ${JSON.stringify(layout.dedup)}`);
  }
  if ((layout.panel?.rect?.w ?? 0) < 200) {
    throw new Error(`${width}x${height}: panel width too small: ${JSON.stringify(layout.panel)}`);
  }

  await page.locator("#report-group-enabled").check();
  await page.locator("#report-group-enabled").uncheck();
  console.log(`OK ${width}x${height}`, JSON.stringify(layout.panel?.rect));
}

const browser = await chromium.launch();
const page = await browser.newPage();
try {
  await login(page);
  await runExtract(page);
  await probeViewport(page, 1280, 800);
  await probeViewport(page, 390, 844);
} finally {
  await browser.close();
}
