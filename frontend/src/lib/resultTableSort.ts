/** Stable locale- and type-aware comparisons for TanStack Table sorting. */

export function compareCellValues(a: unknown, b: unknown): number {
  if (a === b) return 0;
  const aNull = a === null || a === undefined || a === "";
  const bNull = b === null || b === undefined || b === "";
  if (aNull && bNull) return 0;
  if (aNull) return 1;
  if (bNull) return -1;

  const aNum = typeof a === "number" ? a : Number(a);
  const bNum = typeof b === "number" ? b : Number(b);
  const aIsNum = typeof a === "number" || (typeof a === "string" && a.trim() !== "" && !Number.isNaN(aNum));
  const bIsNum = typeof b === "number" || (typeof b === "string" && b.trim() !== "" && !Number.isNaN(bNum));
  if (aIsNum && bIsNum) {
    if (aNum === bNum) return 0;
    return aNum < bNum ? -1 : 1;
  }

  return String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: "base" });
}
