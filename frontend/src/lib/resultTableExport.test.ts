import { describe, expect, it } from "vitest";
import { buildCsvBlob, buildCsvContent, escapeCsvField } from "@/lib/resultTableExport";

describe("resultTableExport", () => {
  it("CSV row 1 is always the header", () => {
    const text = buildCsvContent(["a", "b"], [{ a: 1, b: 2 }], (id) => id.toUpperCase());
    const firstLine = text.split("\r\n")[0];
    expect(firstLine).toBe("A,B");
  });

  it("escapes quotes, commas, newlines and preserves Hindi text", () => {
    expect(escapeCsvField('He said "hi"')).toBe('"He said ""hi"""');
    expect(escapeCsvField("Jaipur, Rajasthan")).toBe('"Jaipur, Rajasthan"');
    expect(escapeCsvField("line1\nline2")).toBe('"line1\nline2"');
    expect(escapeCsvField("जयपुर")).toBe("जयपुर");
    const csv = buildCsvContent(["n"], [{ n: "जयपुर" }], (id) => id);
    expect(csv).toContain("जयपुर");
  });

  it("buildCsvBlob includes UTF-8 BOM", async () => {
    const blob = buildCsvBlob(["c"], [{ c: "x" }], (id) => id);
    const buf = await blob.arrayBuffer();
    const bytes = new Uint8Array(buf);
    expect(bytes[0]).toBe(0xef);
    expect(bytes[1]).toBe(0xbb);
    expect(bytes[2]).toBe(0xbf);
  });

  it("client CSV row count matches data rows plus header", () => {
    const rows = [{ a: 1 }, { a: 2 }, { a: 3 }];
    const csv = buildCsvContent(["a"], rows, (id) => id);
    expect(csv.split("\r\n").length).toBe(rows.length + 1);
  });
});
