import { describe, expect, it, vi } from "vitest";
import { exportBufferedAnalyticalResult } from "@/lib/resultTableExport";

describe("dashboard export fallback without matchExportRequest", () => {
  it("exports both CSV and XLSX from buffered rows", async () => {
    const clicks: { name: string; type: string }[] = [];
    vi.stubGlobal(
      "URL",
      Object.assign(URL, {
        createObjectURL: () => "blob:mock",
        revokeObjectURL: () => {},
      }),
    );
    const appendChild = vi.fn();
    const remove = vi.fn();
    vi.stubGlobal("document", {
      body: { appendChild },
      createElement: () => ({
        click: () => clicks.push({ name: "click", type: "a" }),
        remove,
        href: "",
        download: "",
      }),
    });

    const rows = [{ district: "Jaipur" }, { district: "Udaipur" }];
    await exportBufferedAnalyticalResult("csv", ["district"], rows, (id) => id, "dash-test");
    await exportBufferedAnalyticalResult("xlsx", ["district"], rows, (id) => id, "dash-test");
    expect(clicks.length).toBe(2);
    vi.unstubAllGlobals();
  });
});
