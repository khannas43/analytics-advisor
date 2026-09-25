"use client";

import { useCallback, useEffect, useState } from "react";
import {
  listOverviewColumns,
  listOverviewSourceSystems,
  listOverviewTableGroups,
  listOverviewTables,
  type OverviewTableSummary,
  type RegisteredColumn,
  type TableRef,
} from "@/lib/analysisApi";

function tagLabel(tag: string | null): string {
  if (!tag) return "—";
  return tag === "UNTAGGED" ? "Untagged (legacy)" : tag;
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

function TableColumnsPanel({ tableRef }: Readonly<{ tableRef: TableRef }>) {
  const [columns, setColumns] = useState<RegisteredColumn[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    listOverviewColumns(tableRef)
      .then((cols) => {
        if (!cancelled) setColumns(cols);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(errorMessage(err));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [tableRef.catalog, tableRef.schema, tableRef.table]);

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
      <div className="srse-text-muted" style={{ fontSize: "0.78rem", marginTop: "0.35rem", display: "flex", gap: "0.75rem", flexWrap: "wrap" }}>
        <span>Layer: {tagLabel(row.layer)}</span>
        <span>Source: {tagLabel(row.sourceSystem)}</span>
        <span>Group: {tagLabel(row.tableGroup)}</span>
        {row.sharedReference && <span className="srse-badge">Shared reference</span>}
      </div>
      {open && <TableColumnsPanel tableRef={tableRef} />}
    </div>
  );
}

export default function DatabaseOverviewPage() {
  const [sourceSystems, setSourceSystems] = useState<string[]>([]);
  const [tableGroups, setTableGroups] = useState<string[]>([]);
  const [selectedSource, setSelectedSource] = useState("");
  const [selectedGroup, setSelectedGroup] = useState("");
  const [tables, setTables] = useState<OverviewTableSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadSourceSystems = useCallback(() => {
    listOverviewSourceSystems()
      .then(setSourceSystems)
      .catch((err: unknown) => setError(errorMessage(err)));
  }, []);

  useEffect(() => {
    loadSourceSystems();
  }, [loadSourceSystems]);

  useEffect(() => {
    listOverviewTableGroups(selectedSource || undefined)
      .then(setTableGroups)
      .catch((err: unknown) => setError(errorMessage(err)));
  }, [selectedSource]);

  useEffect(() => {
    setLoading(true);
    setError(null);
    listOverviewTables(selectedSource || undefined, selectedGroup || undefined)
      .then(setTables)
      .catch((err: unknown) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, [selectedSource, selectedGroup]);

  function onSourceChange(value: string) {
    setSelectedSource(value);
    setSelectedGroup("");
  }

  return (
    <div className="overview-embed">
        {error && <p className="srse-text-danger">{error}</p>}

        <section className="srse-card" style={{ marginBottom: "1rem" }}>
          <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap", alignItems: "flex-end" }}>
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
                <option value="">— all —</option>
                {sourceSystems.map((s) => (
                  <option key={s} value={s}>
                    {tagLabel(s)}
                  </option>
                ))}
              </select>
            </div>
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
                <option value="">— all —</option>
                {tableGroups.map((g) => (
                  <option key={g} value={g}>
                    {tagLabel(g)}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </section>

        {loading && <p className="srse-text-muted">Loading tables…</p>}

        {!loading && tables.length === 0 && (
          <p className="srse-text-muted">No registered tables match these filters in your scope.</p>
        )}

        {!loading &&
          tables.map((row) => (
            <OverviewTableRow key={row.qualifiedName} row={row} />
          ))}
    </div>
  );
}
