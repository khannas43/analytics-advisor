"use client";

import { useCallback, useEffect, useState } from "react";
import {
  fetchColumnDistinctValues,
  type PredicateSpecWire,
  type TableRef,
} from "@/lib/analysisApi";

type Props = Readonly<{
  table: TableRef;
  column: string;
  onChange: (spec: PredicateSpecWire | null) => void;
}>;

const NULL_SENTINEL = "__NULL__";

export default function ValueFilterPicker({ table, column, onChange }: Props) {
  const [values, setValues] = useState<(string | null)[]>([]);
  const [truncated, setTruncated] = useState(false);
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (searchTerm?: string) => {
      if (!column) {
        setValues([]);
        onChange(null);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const res = await fetchColumnDistinctValues(table, column, searchTerm);
        setValues(res.values);
        setTruncated(res.truncated);
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : String(err));
      } finally {
        setLoading(false);
      }
    },
    [table, column, onChange],
  );

  useEffect(() => {
    setSelected(new Set());
    void load();
  }, [load]);

  useEffect(() => {
    if (!column || selected.size === 0) {
      onChange(null);
      return;
    }
    const picked: (string | null)[] = [...selected].map((s) => (s === NULL_SENTINEL ? null : s));
    const node = {
      type: "PREDICATE" as const,
      column: { table: { catalog: table.catalog, schema: table.schema, table: table.table }, column },
      operator: "IN" as const,
      value: picked,
    };
    onChange({ root: node });
  }, [selected, column, table, onChange]);

  function toggle(value: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(value)) {
        next.delete(value);
      } else {
        next.add(value);
      }
      return next;
    });
  }

  return (
    <div className="srse-card" style={{ padding: "0.75rem", marginTop: "0.5rem" }}>
      <div className="srse-text-muted" style={{ fontSize: "0.78rem", marginBottom: "0.5rem" }}>
        Value filter — emits the same <code>IN</code> rule as the rule builder (scoped, audited value list).
      </div>
      {error && <p className="srse-text-danger">{error}</p>}
      {truncated && (
        <div style={{ marginBottom: "0.5rem" }}>
          <p className="srse-text-muted" style={{ fontSize: "0.75rem" }}>
            Showing the first values only — list is truncated. Type to search with a bound LIKE.
          </p>
          <div style={{ display: "flex", gap: "0.4rem" }}>
            <input
              className="srse-input"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search…"
            />
            <button type="button" className="srse-btn srse-btn-sm" onClick={() => load(search)}>
              Search
            </button>
          </div>
        </div>
      )}
      {loading && <p className="srse-text-muted">Loading values…</p>}
      {!loading && values.length === 0 && <p className="srse-text-muted">No values in your scope.</p>}
      <div style={{ display: "flex", flexDirection: "column", gap: "0.25rem", maxHeight: 200, overflow: "auto" }}>
        {!loading && column && (
          <label style={{ fontSize: "0.82rem" }}>
            <input type="checkbox" checked={selected.has(NULL_SENTINEL)} onChange={() => toggle(NULL_SENTINEL)} />{" "}
            <span className="srse-text-muted">(null)</span>
          </label>
        )}
        {values.map((v, i) => {
          const key = v ?? NULL_SENTINEL;
          const label = v ?? "(null)";
          return (
            <label key={`${key}-${i}`} style={{ fontSize: "0.82rem" }}>
              <input type="checkbox" checked={selected.has(key)} onChange={() => toggle(key)} /> {label}
            </label>
          );
        })}
      </div>
    </div>
  );
}
