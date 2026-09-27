"use client";

import { useEffect, useState } from "react";
import {
  browseColumns,
  getTableScopeConfig,
  replaceTableScopeConfig,
  type TableRegistration,
  type TableScopeConfig,
} from "@/lib/adminApi";
import { listDimensions, listLevels, type DimensionView, type LevelView } from "@/lib/scopeAdminApi";
import { fetchAuthSession } from "@/lib/authToken";

export function TableScopeBindingEditor({
  registration,
  onSaved,
}: Readonly<{ registration: TableRegistration; onSaved?: () => void }>) {
  const [open, setOpen] = useState(false);
  const [config, setConfig] = useState<TableScopeConfig | null>(null);
  const [dimensions, setDimensions] = useState<DimensionView[]>([]);
  const [levelsByDim, setLevelsByDim] = useState<Record<number, LevelView[]>>({});
  const [columns, setColumns] = useState<string[]>([]);
  const [levelId, setLevelId] = useState("");
  const [columnName, setColumnName] = useState("");
  const [exemptIds, setExemptIds] = useState<number[]>([]);
  const [shared, setShared] = useState(false);
  const [superAdmin, setSuperAdmin] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    let cancelled = false;
    (async () => {
      try {
        const [cfg, dims, cols, session] = await Promise.all([
          getTableScopeConfig(registration.id),
          listDimensions(),
          browseColumns(registration.catalog, registration.schema, registration.table).then((c) =>
            c.map((x) => x.name),
          ),
          fetchAuthSession(),
        ]);
        if (cancelled) return;
        setConfig(cfg);
        setDimensions(dims);
        setColumns(cols);
        setExemptIds(cfg.exemptDimensionIds);
        setShared(cfg.sharedReference);
        setSuperAdmin(session?.superAdmin ?? false);
        const levelMap: Record<number, LevelView[]> = {};
        for (const d of dims) {
          levelMap[d.id] = await listLevels(d.id);
        }
        if (cancelled) return;
        setLevelsByDim(levelMap);
      } catch (e: unknown) {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open, registration.id, registration.catalog, registration.schema, registration.table]);

  async function onSave() {
    if (!config) return;
    setError(null);
    try {
      await replaceTableScopeConfig(registration.id, {
        bindings: config.bindings.map((b) => ({
          scopeLevelId: b.scopeLevelId,
          columnName: b.columnName,
        })),
        exemptDimensionIds: exemptIds,
        sharedReference: shared,
      });
      onSaved?.();
      setOpen(false);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  async function addBinding() {
    if (!config || !levelId || !columnName) return;
    const updated = await replaceTableScopeConfig(registration.id, {
      bindings: [
        ...config.bindings.map((b) => ({ scopeLevelId: b.scopeLevelId, columnName: b.columnName })),
        { scopeLevelId: Number(levelId), columnName },
      ],
      exemptDimensionIds: exemptIds,
      sharedReference: shared,
    });
    setConfig(updated);
    setLevelId("");
    setColumnName("");
  }

  return (
    <span>
      <button type="button" className="srse-btn srse-btn-ghost srse-btn-sm" onClick={() => setOpen((o) => !o)}>
        Scope bindings
      </button>
      {open ? (
        <div className="srse-card" style={{ marginTop: "0.5rem", padding: "0.75rem" }}>
          <p className="srse-muted" style={{ fontSize: "0.85rem" }}>
            Without a binding, exemption, or shared-reference flag for each dimension a scoped officer is
            assigned in, this table is <strong>invisible</strong> in Analysis — not visible and unfiltered.
          </p>
          {config ? (
            <ul>
              {config.bindings.map((b) => (
                <li key={b.id}>
                  {b.levelName} (depth {b.levelDepth}) → {b.columnName}
                </li>
              ))}
            </ul>
          ) : null}
          <label>
            Add binding — level
            <select className="srse-input" value={levelId} onChange={(e) => setLevelId(e.target.value)}>
              <option value="">Select…</option>
              {dimensions.flatMap((d) =>
                (levelsByDim[d.id] ?? []).map((l) => (
                  <option key={l.id} value={l.id}>
                    {d.code} / {l.name} (depth {l.depth})
                  </option>
                )),
              )}
            </select>
          </label>
          <label>
            Column
            <select className="srse-input" value={columnName} onChange={(e) => setColumnName(e.target.value)}>
              <option value="">Select…</option>
              {columns.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </label>
          <button type="button" className="srse-btn srse-btn-sm" onClick={addBinding}>
            Add binding
          </button>
          <label>
            Exempt dimensions (not applicable)
            <select
              multiple
              className="srse-input"
              value={exemptIds.map(String)}
              onChange={(e) =>
                setExemptIds(Array.from(e.target.selectedOptions).map((o) => Number(o.value)))
              }
            >
              {dimensions.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.code}
                </option>
              ))}
            </select>
          </label>
          {superAdmin ? (
            <label>
              <input type="checkbox" checked={shared} onChange={(e) => setShared(e.target.checked)} /> Shared
              reference (visible to all officers; scoping disabled)
            </label>
          ) : null}
          {error ? <p className="srse-error-text">{error}</p> : null}
          <button type="button" className="srse-btn srse-btn-primary srse-btn-sm" onClick={onSave}>
            Save scope settings
          </button>
        </div>
      ) : null}
    </span>
  );
}
