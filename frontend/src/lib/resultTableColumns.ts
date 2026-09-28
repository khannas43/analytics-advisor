/** Column visibility helpers for analytical result tables. */

/** Status columns that must stay visible so outer-join / comparison rows stay interpretable. */
export function isLockedResultColumn(columnId: string): boolean {
  return columnId === "match_status" || columnId.endsWith("_match_status");
}

export type ColumnVisibilityState = {
  /** Empty = show all columns (default). Non-empty = hidden column ids. */
  hiddenColumnIds: string[];
};

export function visibleColumnIds(
  allColumns: readonly string[],
  hiddenColumnIds: readonly string[],
): string[] {
  if (hiddenColumnIds.length === 0) {
    return [...allColumns];
  }
  const hidden = new Set(hiddenColumnIds);
  return allColumns.filter((c) => !hidden.has(c) || isLockedResultColumn(c));
}

export function resetColumnVisibilityForSchema(
  previousHidden: readonly string[],
  newColumns: readonly string[],
): string[] {
  const colSet = new Set(newColumns);
  return previousHidden.filter((id) => colSet.has(id) && !isLockedResultColumn(id));
}

export function toggleColumnHidden(
  hidden: readonly string[],
  columnId: string,
  hide: boolean,
  allColumns: readonly string[],
): string[] {
  if (isLockedResultColumn(columnId)) {
    return [...hidden];
  }
  const next = new Set(hidden);
  if (hide) {
    next.add(columnId);
  } else {
    next.delete(columnId);
  }
  const visible = visibleColumnIds(allColumns, [...next]);
  if (visible.length === 0) {
    return [...hidden];
  }
  return [...next];
}
