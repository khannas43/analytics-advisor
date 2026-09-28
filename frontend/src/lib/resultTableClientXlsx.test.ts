import { describe, expect, it } from "vitest";
import ExcelJS from "exceljs";
import { buildClientXlsxBlob } from "@/lib/resultTableClientXlsx";

describe("buildClientXlsxBlob dashboard fallback", () => {
  it("produces a workbook with frozen header, autofilter and bold row 1", async () => {
    const blob = await buildClientXlsxBlob(
      ["name", "district"],
      [{ name: "जयपुर", district: "Jaipur, RJ" }],
      (id) => id,
    );
    const buf = Buffer.from(await blob.arrayBuffer());
    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(buf);
    const sheet = workbook.getWorksheet("results");
    expect(sheet).toBeTruthy();
    expect(sheet!.getRow(1).getCell(1).value).toBe("name");
    expect(sheet!.getRow(1).font?.bold).toBe(true);
    expect(sheet!.autoFilter).toBeTruthy();
    const view = sheet!.views?.[0] as { ySplit?: number; activeCell?: string; state?: string };
    expect(view?.ySplit).toBe(1);
    expect(view?.activeCell).toBe("A2");
    expect(sheet!.rowCount).toBe(2);
  });
});
