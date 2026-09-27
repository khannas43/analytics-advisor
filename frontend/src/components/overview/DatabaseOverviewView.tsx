"use client";

import { useEffect, useMemo, useState } from "react";
import {
  listOverviewColumns,
  listOverviewSourceSystems,
  listOverviewTableGroups,
  listOverviewTables,
  type OverviewTableSummary,
  type RegisteredColumn,
  type TableRef,
} from "@/lib/analysisApi";
import {
  shouldShowLabelFilter,
  UNTAGGED_FILTER,
  meaningfulLabelFilterOptions,
} from "@/components/LakehouseCascade";

function tagLabel(tag: string | null): string {
  if (!tag) return "";
  return tag === UNTAGGED_FILTER ? "Untagged (legacy)" : tag;
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

function TableColumnsPanel({ tableRef }: Readonly<{ tableRef: TableRef }>) {
  const tableKey = `${tableRef.catalog}.${tableRef.schema}.${tableRef.table}`;
  const [fetchState, setFetchState] = useState<{
    key: string;
    columns: RegisteredColumn[] | null;
    error: string | null;
  }>({ key: "", columns: null, error: null });
  const loading = fetchState.key !== tableKey;
  const columns = fetchState.key === tableKey ? fetchState.columns : null;
  const error = fetchState.key === tableKey ? fetchState.error : null;

  useEffect(() => {
    let cancelled = false;
    listOverviewColumns(tableRef)
      .then((cols) => {
        if (!cancelled) setFetchState({ key: tableKey, columns: cols, error: null });
      })
      .catch((err: unknown) => {
        if (!cancelled) setFetchState({ key: tableKey, columns: null, error: errorMessage(err) });
      });
    return () => {
      cancelled = true;
    };
  }, [tableKey, tableRef]);

  if (loading) {
    return <p className="srse-text-muted" style={{ margin: "0.5rem 0 0 1.5rem" }}>Loading columns…</p>;
  }
  if (error) {
    return <p className="srse-text-danger" style={{ margin: "0.5rem 0 0 1.5rem" }}>{error}</p>;
  }
  if (!columns?.length) {
    return (
      <p className="srse-text-muted" style={{ margin: "0.5rem 0 0 1.5rem" }}>
        No visible columns (all hidden in admin metadata).
      </p>
    );
  }

  return (
    <div style={{ margin: "0.5rem 0 0 1.5rem", overflowX: "auto" }}>
      <table className="srse-table" style={{ fontSize: "0.82rem" }}>
        <thead>
          <tr>
            <th>Column</th>
            <th>Type</th>
            <th>Business name</th>
            <th>Fuzzy</th>
          </tr>
        </thead>
        <tbody>
          {columns.map((c) => (
            <tr key={c.name}>
              <td style={{ fontFamily: "monospace" }}>{c.name}</td>
              <td>{c.dataType}</td>
              <td>{c.businessName ?? "—"}</td>
              <td>{c.fuzzyMatchable ? "Yes" : "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function OverviewTableRow({ row }: Readonly<{ row: OverviewTableSummary }>) {
  const [open, setOpen] = useState(false);
  const tableRef: TableRef = { catalog: row.catalog, schema: row.schema, table: row.table };
  const metaParts: string[] = [];
  if (row.layer) metaParts.push(`Layer: ${tagLabel(row.layer)}`);
  if (row.sourceSystem) metaParts.push(`Source: ${tagLabel(row.sourceSystem)}`);
  if (row.tableGroup) metaParts.push(`Group: ${tagLabel(row.tableGroup)}`);

  return (
    <div className="srse-card" style={{ padding: "0.75rem 1rem", marginBottom: "0.5rem" }}>
      <button
        type="button"
        className="srse-btn srse-btn-ghost srse-btn-sm"
        style={{ marginRight: "0.5rem" }}
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        {open ? "▾" : "▸"}
      </button>
      <span style={{ fontFamily: "monospace", fontSize: "0.88rem" }}>{row.qualifiedName}</span>
      {metaParts.length > 0 && (
        <div className="srse-text-muted" style={{ fontSize: "0.78rem", marginTop: "0.35rem", display: "flex", gap: "0.75rem", flexWrap: "wrap" }}>
          {metaParts.map((p) => (
            <span key={p}>{p}</span>
          ))}
          {row.sharedReference && <span className="srse-badge">Shared reference</span>}
        </div>
      )}
      {open && <TableColumnsPanel tableRef={tableRef} />}
    </div>
  );
}

function groupByCatalogSchema(rows: OverviewTableSummary[]): Map<string, Map<string, OverviewTableSummary[]>> {
  const out = new Map<string, Map<string, OverviewTableSummary[]>>();
  for (const row of rows) {
    let schemas = out.get(row.catalog);
    if (!schemas) {
      schemas = new Map();
      out.set(row.catalog, schemas);
    }
    const list = schemas.get(row.schema) ?? [];
    list.push(row);
    schemas.set(row.schema, list);
  }
  return out;
}

export default function DatabaseOverviewPage() {
  const [sourceSystems, setSourceSystems] = useState<string[]>([]);
  const [tableGroups, setTableGroups] = useState<string[]>([]);
  const [selectedSource, setSelectedSource] = useState("");
  const [selectedGroup, setSelectedGroup] = useState("");
  const filterKey = `${selectedSource}|${selectedGroup}`;
  const [tableFetch, setTableFetch] = useState<{
    key: string;
    rows: OverviewTableSummary[];
    error: string | null;
  }>({ key: "", rows: [], error: null });
  const loading = tableFetch.key !== filterKey;
  const tables = tableFetch.key === filterKey ? tableFetch.rows : [];
  const tableError = tableFetch.key === filterKey ? tableFetch.error : null;
  const [labelLoadError, setLabelLoadError] = useState<string | null>(null);
  const error = tableError ?? labelLoadError;

  const showSourceFilter = shouldShowLabelFilter(sourceSystems);
  const showGroupFilter = shouldShowLabelFilter(tableGroups);

  useEffect(() => {
    let cancelled = false;
    listOverviewSourceSystems()
      .then((systems) => {
        if (!cancelled) setSourceSystems(systems);
      })
      .catch((err: unknown) => {
        if (!cancelled) setLabelLoadError(errorMessage(err));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    listOverviewTableGroups(selectedSource || undefined)
      .then((groups) => {
        if (!cancelled) setTableGroups(groups);
      })
      .catch((err: unknown) => {
        if (!cancelled) setLabelLoadError(errorMessage(err));
      });
    return () => {
      cancelled = true;
    };
  }, [selectedSource]);

  useEffect(() => {
    let cancelled = false;
    listOverviewTables(selectedSource || undefined, selectedGroup || undefined)
      .then((rows) => {
        if (!cancelled) setTableFetch({ key: filterKey, rows, error: null });
      })
      .catch((err: unknown) => {
        if (!cancelled) setTableFetch({ key: filterKey, rows: [], error: errorMessage(err) });
      });
    return () => {
      cancelled = true;
    };
  }, [filterKey, selectedSource, selectedGroup]);

  function onSourceChange(value: string) {
    setSelectedSource(value);
    setSelectedGroup("");
  }

  const grouped = useMemo(() => groupByCatalogSchema(tables), [tables]);

  return (
    <div className="overview-embed">
        {error && <p className="srse-text-danger">{error}</p>}

        {(showSourceFilter || showGroupFilter) && (
        <section className="srse-card" style={{ marginBottom: "1rem" }}>
          <p className="srse-text-muted" style={{ marginTop: 0, fontSize: "0.82rem" }}>
            Optional display-label filters — leave at All to browse every registered table in your scope.
          </p>
          <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap", alignItems: "flex-end" }}>
            {showSourceFilter && (
            <div>
              <label htmlFor="overview-source" className="srse-text-muted" style={{ fontSize: "0.72rem", display: "block" }}>
                Source system
              </label>
              <select
                id="overview-source"
                className="srse-select"
                style={{ minWidth: 180 }}
                value={selectedSource}
                onChange={(e) => onSourceChange(e.target.value)}
              >
                <option value="">All</option>
                {meaningfulLabelFilterOptions(sourceSystems).map((s) => (
                  <option key={s} value={s}>
                    {tagLabel(s)}
                  </option>
                ))}
                {sourceSystems.includes(UNTAGGED_FILTER) && (
                  <option value={UNTAGGED_FILTER}>{tagLabel(UNTAGGED_FILTER)}</option>
                )}
              </select>
            </div>
            )}
            {showGroupFilter && (
            <div>
              <label htmlFor="overview-group" className="srse-text-muted" style={{ fontSize: "0.72rem", display: "block" }}>
                Table group
              </label>
              <select
                id="overview-group"
                className="srse-select"
                style={{ minWidth: 180 }}
                value={selectedGroup}
                onChange={(e) => setSelectedGroup(e.target.value)}
              >
                <option value="">All</option>
                {meaningfulLabelFilterOptions(tableGroups).map((g) => (
                  <option key={g} value={g}>
                    {tagLabel(g)}
                  </option>
                ))}
                {tableGroups.includes(UNTAGGED_FILTER) && (
                  <option value={UNTAGGED_FILTER}>{tagLabel(UNTAGGED_FILTER)}</option>
                )}
              </select>
            </div>
            )}
          </div>
        </section>
        )}

        {loading && <p className="srse-text-muted">Loading tables…</p>}

        {!loading && tables.length === 0 && (
          <p className="srse-text-muted">No registered tables match these filters in your scope.</p>
        )}

        {!loading &&
          [...grouped.entries()].map(([catalog, schemas]) => (
            <section key={catalog} className="srse-card" style={{ marginBottom: "1rem", padding: "0.75rem 1rem" }}>
              <h3 style={{ margin: "0 0 0.75rem", fontSize: "1rem" }}>{catalog}</h3>
              {[...schemas.entries()].map(([schema, schemaRows]) => (
                <div key={`${catalog}.${schema}`} style={{ marginBottom: "1rem" }}>
                  <h4 className="srse-text-muted" style={{ margin: "0 0 0.5rem", fontSize: "0.88rem", fontFamily: "monospace" }}>
                    {schema}
                  </h4>
                  {schemaRows.map((row) => (
                    <OverviewTableRow key={row.qualifiedName} row={row} />
                  ))}
                </div>
              ))}
            </section>
          ))}
    </div>
  );
}
