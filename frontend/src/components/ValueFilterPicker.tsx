"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  fetchColumnDistinctValues,
  type PredicateSpecWire,
  type TableRef,
} from "@/lib/analysisApi";
import {
  inPredicateHydrationKey,
  inPredicateHydrationKeyFor,
} from "@/lib/valuePickerSemantics";

const NULL_SENTINEL = "__NULL__";

type Props = Readonly<{
  table: TableRef;
  column: string;
  /** When reopening a saved query, re-select IN values from the persisted predicate. */
  hydratedSpec?: PredicateSpecWire | null;
  onChange: (spec: PredicateSpecWire | null) => void;
}>;

function selectedFromInSpec(spec: PredicateSpecWire | null | undefined): Set<string> {
  const root = spec?.root;
  if (root?.type !== "PREDICATE" || root.operator !== "IN" || !Array.isArray(root.value)) {
    return new Set();
  }
  return new Set(root.value.map((v) => (v === null ? NULL_SENTINEL : String(v))));
}

function setsEqual(a: Set<string>, b: Set<string>): boolean {
  if (a.size !== b.size) return false;
  for (const v of a) {
    if (!b.has(v)) return false;
  }
  return true;
}

/** @deprecated use {@link inPredicateHydrationKey} from valuePickerSemantics */
export { inPredicateHydrationKey } from "@/lib/valuePickerSemantics";

export function predicateWireSignature(p: PredicateSpecWire | null): string {
  if (!p) return "__null__";
  const canonical = inPredicateHydrationKey(p);
  if (canonical) return canonical;
  return JSON.stringify(p);
}

export default function ValueFilterPicker({ table, column, hydratedSpec, onChange }: Props) {
  const pickerKey = `${table.catalog}.${table.schema}.${table.table}|${column}`;
  const [fetchState, setFetchState] = useState<{
    key: string;
    values: (string | null)[];
    truncated: boolean;
    error: string | null;
  }>({ key: "", values: [], truncated: false, error: null });
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<Set<string>>(() => selectedFromInSpec(hydratedSpec));

  const onChangeRef = useRef(onChange);
  const emittedSigRef = useRef<string>("");
  const hydratedSpecRef = useRef(hydratedSpec);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    hydratedSpecRef.current = hydratedSpec;
  }, [hydratedSpec]);

  const loading = Boolean(column) && fetchState.key !== pickerKey;
  const values = fetchState.key === pickerKey ? fetchState.values : [];
  const truncated = fetchState.key === pickerKey ? fetchState.truncated : false;
  const error = fetchState.key === pickerKey ? fetchState.error : null;

  const hydratedKey = inPredicateHydrationKey(hydratedSpec);

  useEffect(() => {
    const next = selectedFromInSpec(hydratedSpecRef.current);
    setSelected((prev) => (setsEqual(prev, next) ? prev : next));
    setSearch("");
  }, [hydratedKey, pickerKey]);

  useEffect(() => {
    if (!column) {
      return undefined;
    }
    let cancelled = false;
    const tableRef: TableRef = {
      catalog: table.catalog,
      schema: table.schema,
      table: table.table,
    };
    fetchColumnDistinctValues(tableRef, column)
      .then((res) => {
        if (!cancelled) {
          setFetchState({
            key: pickerKey,
            values: res.values,
            truncated: res.truncated,
            error: null,
          });
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setFetchState({
            key: pickerKey,
            values: [],
            truncated: false,
            error: err instanceof Error ? err.message : String(err),
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [pickerKey, column, table.catalog, table.schema, table.table]);

  const predicate = useMemo((): PredicateSpecWire | null => {
    if (!column || selected.size === 0) {
      return null;
    }
    const picked: (string | null)[] = [...selected].map((s) => (s === NULL_SENTINEL ? null : s));
    const node = {
      type: "PREDICATE" as const,
      column: {
        table: { catalog: table.catalog, schema: table.schema, table: table.table },
        column,
      },
      operator: "IN" as const,
      value: picked,
    };
    return { root: node };
  }, [selected, column, table.catalog, table.schema, table.table]);

  const selfSig = useMemo(() => {
    if (!column || selected.size === 0) {
      return "";
    }
    return inPredicateHydrationKeyFor(table, column, selected);
  }, [selected, column, table.catalog, table.schema, table.table]);

  useEffect(() => {
    const parentSig = hydratedKey;

    if (!column) {
      if (parentSig !== "") {
        return;
      }
      if (emittedSigRef.current !== "__null__") {
        emittedSigRef.current = "__null__";
        onChangeRef.current(null);
      }
      return;
    }

    if (selfSig === parentSig && parentSig !== "") {
      emittedSigRef.current = selfSig;
      return;
    }

    if (selfSig === "" && parentSig !== "") {
      return;
    }

    if (selfSig === emittedSigRef.current) {
      return;
    }

    emittedSigRef.current = selfSig || "__null__";
    onChangeRef.current(predicate);
  }, [selfSig, hydratedKey, column, predicate]);

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
    <div className="srse-card value-filter-picker" style={{ padding: "0.75rem", marginTop: "0.5rem" }}>
      {!column ? (
        <p className="srse-text-muted" style={{ margin: 0 }}>
          Pick a column first.
        </p>
      ) : (
        <>
          <input
            type="search"
            placeholder="Search values…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="srse-input"
            style={{ marginBottom: "0.5rem", width: "100%" }}
          />
          {loading && <p className="srse-text-muted">Loading values…</p>}
          {error && <p className="srse-text-danger">{error}</p>}
          {!loading && !error && (
            <ul style={{ maxHeight: 160, overflow: "auto", listStyle: "none", padding: 0, margin: 0 }}>
              {values
                .filter((v) => {
                  const label = v === null ? "(null)" : String(v);
                  return !search || label.toLowerCase().includes(search.toLowerCase());
                })
                .map((v) => {
                  const key = v === null ? NULL_SENTINEL : String(v);
                  const label = v === null ? "(null)" : String(v);
                  return (
                    <li key={key}>
                      <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: "0.85rem" }}>
                        <input
                          type="checkbox"
                          aria-label={label}
                          checked={selected.has(key)}
                          onChange={() => toggle(key)}
                        />
                        {label}
                      </label>
                    </li>
                  );
                })}
            </ul>
          )}
          {truncated && (
            <p className="srse-text-muted" style={{ fontSize: "0.75rem", marginTop: "0.35rem" }}>
              List truncated — refine with search or add more filters.
            </p>
          )}
        </>
      )}
    </div>
  );
}
