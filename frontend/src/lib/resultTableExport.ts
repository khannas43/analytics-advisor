/** UTF-8 CSV export for in-browser analytical result buffers (server stream handles full lakehouse exports). */

export function escapeCsvField(value: unknown): string {
  if (value === null || value === undefined) return "";
  const text = typeof value === "object" ? JSON.stringify(value) : String(value);
  if (
    text.includes('"') ||
    text.includes(",") ||
    text.includes("\n") ||
    text.includes("\r")
  ) {
    return `"${text.replaceAll('"', '""')}"`;
  }
  return text;
}

export function buildCsvContent(
  columnIds: readonly string[],
  rows: readonly Record<string, unknown>[],
  headerLabel: (id: string) => string,
): string {
  const lines: string[] = [
    columnIds.map((id) => escapeCsvField(headerLabel(id))).join(","),
    ...rows.map((row) => columnIds.map((id) => escapeCsvField(row[id])).join(",")),
  ];
  return lines.join("\r\n");
}

/** Excel-compatible UTF-8 CSV with BOM; header is always row 1 (CSV has no freeze panes). */
export function buildCsvBlob(
  columnIds: readonly string[],
  rows: readonly Record<string, unknown>[],
  headerLabel: (id: string) => string,
): Blob {
  const body = buildCsvContent(columnIds, rows, headerLabel);
  return new Blob(["\uFEFF", body], { type: "text/csv;charset=utf-8;" });
}

export function triggerBlobDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export function defaultExportFilename(prefix: string, ext: string): string {
  return `${prefix}-${new Date().toISOString().slice(0, 19).replaceAll(/[:T]/g, "-")}.${ext}`;
}

/** Client-side export of all buffered columns/rows (no server match request). */
export async function exportBufferedAnalyticalResult(
  format: "csv" | "xlsx",
  columnIds: readonly string[],
  rows: readonly Record<string, unknown>[],
  headerLabel: (id: string) => string,
  filenamePrefix = "analysis-result",
): Promise<void> {
  const { buildClientXlsxBlob } = await import("@/lib/resultTableClientXlsx");
  if (format === "csv") {
    triggerBlobDownload(buildCsvBlob(columnIds, rows, headerLabel), defaultExportFilename(filenamePrefix, "csv"));
    return;
  }
  const blob = await buildClientXlsxBlob(columnIds, rows, headerLabel);
  triggerBlobDownload(blob, defaultExportFilename(filenamePrefix, "xlsx"));
}
