/** In-browser XLSX for buffered analytical rows (dashboard fallback / offline buffer). */

function cellValue(value: unknown): string | number | boolean | Date | null {
  if (value === null || value === undefined) return null;
  if (typeof value === "number" || typeof value === "boolean") return value;
  if (value instanceof Date) return value;
  if (typeof value === "object") return JSON.stringify(value);
  const asNum = Number(value);
  if (typeof value === "string" && value.trim() !== "" && !Number.isNaN(asNum)) {
    return asNum;
  }
  return String(value);
}

export async function buildClientXlsxBlob(
  columnIds: readonly string[],
  rows: readonly Record<string, unknown>[],
  headerLabel: (id: string) => string,
): Promise<Blob> {
  const ExcelJS = (await import("exceljs")).default;
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet("results");

  const headers = columnIds.map((id) => headerLabel(id));
  sheet.addRow(headers);
  const headerRow = sheet.getRow(1);
  headerRow.font = { bold: true };
  headerRow.commit();

  for (const row of rows) {
    sheet.addRow(columnIds.map((id) => cellValue(row[id])));
  }

  if (columnIds.length > 0) {
    sheet.autoFilter = {
      from: { row: 1, column: 1 },
      to: { row: 1, column: columnIds.length },
    };
  }
  sheet.views = [{ state: "frozen", ySplit: 1, activeCell: "A2", showGridLines: true }];

  const buffer = await workbook.xlsx.writeBuffer();
  return new Blob([buffer], {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
}
