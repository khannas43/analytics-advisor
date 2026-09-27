import type { PredicateSpecWire, TableRef } from "@/lib/analysisApi";

const NULL_SENTINEL = "__NULL__";

/** Canonical IN predicate identity (column + sorted values + table path). */
export function inPredicateHydrationKey(spec: PredicateSpecWire | null | undefined): string {
  const root = spec?.root;
  if (root?.type !== "PREDICATE" || root.operator !== "IN" || !Array.isArray(root.value)) {
    return "";
  }
  const t = root.column?.table;
  const tablePart = t ? `${t.catalog}.${t.schema}.${t.table}` : "";
  const col = root.column?.column ?? "";
  const vals = root.value.map((v) => (v === null ? NULL_SENTINEL : String(v))).sort();
  return `IN|${tablePart}|${col}|${vals.join("\u0001")}`;
}

export function inPredicateHydrationKeyFor(table: TableRef, column: string, values: Iterable<string>): string {
  const vals = [...values].sort();
  return `IN|${table.catalog}.${table.schema}.${table.table}|${column}|${vals.join("\u0001")}`;
}

export function valuePickerSpecSignaturesEqual(
  a: PredicateSpecWire | null | undefined,
  b: PredicateSpecWire | null | undefined,
): boolean {
  return inPredicateHydrationKey(a) === inPredicateHydrationKey(b);
}
