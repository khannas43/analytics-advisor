"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { RegistryBrowseFilter } from "@/lib/analysisApi";

/**
 * The Catalog → Schema → Table cascade, shared by every place an admin or
 * officer picks a lakehouse table.
 *
 * Each level is fetched only once the level above it is chosen, and choosing
 * a level clears everything below it — so a stale table name from a previous
 * catalog can never survive into a submitted request. That matters more than
 * usual here: with the lakehouse's Silver and Gold layers both mapped, the
 * same table name exists under more than one catalog, so a leftover value
 * would silently point at the wrong layer rather than failing loudly.
 *
 * When registry filter fetchers are supplied (Analysis registry cascade),
 * optional Source system / Table group / Layer rungs appear first. Those
 * values are component-internal state only — they are never part of
 * {@link CascadeValue}, so they cannot ride into match payloads via spread.
 *
 * Deliberately source-agnostic — the caller supplies the fetchers. The
 * Admin page passes the live-browse endpoints (everything the Presto
 * connection can reach); the Analysis tab passes the registry endpoints (only
 * what an admin registered). Same component, two different reaches.
 */

export type CascadeValue = {
  catalog: string;
  schema: string;
  table: string;
};

export const EMPTY_CASCADE: CascadeValue = { catalog: "", schema: "", table: "" };

export function isCascadeComplete(v: CascadeValue): boolean {
  return Boolean(v.catalog && v.schema && v.table);
}

/** A table option; `layer` (SILVER/GOLD) is rendered as a suffix when present. */
export type TableOption = { name: string; layer?: string | null };

export type CascadeFetchers = {
  listSourceSystems?: () => Promise<string[]>;
  listTableGroups?: (sourceSystem?: string) => Promise<string[]>;
  listLayers?: () => Promise<string[]>;
  listCatalogs: (filter?: RegistryBrowseFilter) => Promise<string[]>;
  listSchemas: (catalog: string, filter?: RegistryBrowseFilter) => Promise<string[]>;
  listTables: (
    catalog: string,
    schema: string,
    filter?: RegistryBrowseFilter,
  ) => Promise<TableOption[]>;
};

type Props = Readonly<{
  value: CascadeValue;
  onChange: (value: CascadeValue) => void;
  fetchers: CascadeFetchers;
  /** Rendered above the row; omit for a bare inline cascade. */
  label?: string;
  idPrefix: string;
  disabled?: boolean;
  /** Compact drops the per-select captions — for dense repeated rows. */
  compact?: boolean;
  onError?: (message: string) => void;
}>;

const selectStyle = { minWidth: 150 } as const;

/** Must stay in lockstep with {@code RegistryDisplayTags.UNTAGGED} on the backend. */
function tagOptionLabel(tag: string): string {
  return tag === "UNTAGGED" ? "Untagged (legacy)" : tag;
}

export default function LakehouseCascade({
  value,
  onChange,
  fetchers,
  label,
  idPrefix,
  disabled = false,
  compact = false,
  onError,
}: Props) {
  const [sourceSystems, setSourceSystems] = useState<string[]>([]);
  const [tableGroups, setTableGroups] = useState<string[]>([]);
  const [layers, setLayers] = useState<string[]>([]);
  const [selectedSourceSystem, setSelectedSourceSystem] = useState("");
  const [selectedTableGroup, setSelectedTableGroup] = useState("");
  const [selectedLayer, setSelectedLayer] = useState("");
  const [catalogs, setCatalogs] = useState<string[]>([]);
  const [schemas, setSchemas] = useState<string[]>([]);
  const [tables, setTables] = useState<TableOption[]>([]);
  const [loading, setLoading] = useState(false);

  const layerRequired = fetchers.listLayers != null;
  const browseFilter = useMemo((): RegistryBrowseFilter => {
    const f: RegistryBrowseFilter = {};
    if (selectedLayer) f.layer = selectedLayer;
    if (selectedSourceSystem) f.sourceSystem = selectedSourceSystem;
    if (selectedTableGroup) f.tableGroup = selectedTableGroup;
    return f;
  }, [selectedLayer, selectedSourceSystem, selectedTableGroup]);

  const canFetchCatalogs =
    !layerRequired || Boolean(selectedLayer) || Boolean(value.catalog);
  const catalogDisabled =
    disabled || (layerRequired && !selectedLayer && !value.catalog);

  const report = useCallback(
    (err: unknown) => onError?.(err instanceof Error ? err.message : String(err)),
    [onError],
  );

  const { listSourceSystems, listTableGroups, listLayers, listCatalogs, listSchemas, listTables } =
    fetchers;

  useEffect(() => {
    if (!listSourceSystems) {
      return undefined;
    }
    let cancelled = false;
    listSourceSystems()
      .then((s) => {
        if (!cancelled) setSourceSystems(s);
      })
      .catch(report);
    return () => {
      cancelled = true;
    };
  }, [listSourceSystems, report]);

  useEffect(() => {
    if (!listTableGroups) {
      return undefined;
    }
    let cancelled = false;
    const sourceArg = selectedSourceSystem || undefined;
    listTableGroups(sourceArg)
      .then((g) => {
        if (!cancelled) setTableGroups(g);
      })
      .catch(report);
    return () => {
      cancelled = true;
    };
  }, [listTableGroups, selectedSourceSystem, report]);

  useEffect(() => {
    if (!listLayers) {
      return undefined;
    }
    let cancelled = false;
    listLayers()
      .then((l) => {
        if (!cancelled) setLayers(l);
      })
      .catch(report);
    return () => {
      cancelled = true;
    };
  }, [listLayers, report]);

  useEffect(() => {
    let cancelled = false;
    if (!canFetchCatalogs) {
      return undefined;
    }
    setLoading(true);
    listCatalogs(browseFilter)
      .then((c) => {
        if (!cancelled) setCatalogs(c);
      })
      .catch(report)
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [canFetchCatalogs, browseFilter, listCatalogs, report]);

  const sourceSystemOptions = listSourceSystems ? sourceSystems : [];
  const tableGroupOptions = listTableGroups ? tableGroups : [];
  const layerOptions = listLayers ? layers : [];
  const catalogOptions = canFetchCatalogs ? catalogs : [];

  useEffect(() => {
    let cancelled = false;
    if (!value.catalog) {
      setSchemas([]);
      return undefined;
    }
    listSchemas(value.catalog, browseFilter)
      .then((s) => {
        if (!cancelled) setSchemas(s);
      })
      .catch(report);
    return () => {
      cancelled = true;
    };
  }, [value.catalog, browseFilter, listSchemas, report]);

  useEffect(() => {
    let cancelled = false;
    if (!value.catalog || !value.schema) {
      setTables([]);
      return undefined;
    }
    listTables(value.catalog, value.schema, browseFilter)
      .then((t) => {
        if (!cancelled) setTables(t);
      })
      .catch(report);
    return () => {
      cancelled = true;
    };
  }, [value.catalog, value.schema, browseFilter, listTables, report]);

  function clearCascade() {
    onChange({ catalog: "", schema: "", table: "" });
  }

  function pickSourceSystem(sourceSystem: string) {
    setSelectedSourceSystem(sourceSystem);
    setSelectedTableGroup("");
    clearCascade();
  }

  function pickTableGroup(tableGroup: string) {
    setSelectedTableGroup(tableGroup);
    clearCascade();
  }

  function pickLayer(layer: string) {
    setSelectedLayer(layer);
    clearCascade();
  }

  function pickCatalog(catalog: string) {
    onChange({ catalog, schema: "", table: "" });
  }

  function pickSchema(schema: string) {
    onChange({ ...value, schema, table: "" });
  }

  function pickTable(table: string) {
    onChange({ ...value, table });
  }

  function caption(text: string, htmlFor: string) {
    if (compact) return null;
    return (
      <label htmlFor={htmlFor} className="srse-text-muted" style={{ fontSize: "0.72rem", display: "block" }}>
        {text}
      </label>
    );
  }

  return (
    <div>
      {label && (
        <div className="srse-text-muted" style={{ fontSize: "0.78rem", marginBottom: "0.3rem" }}>
          {label}
        </div>
      )}
      <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", alignItems: "flex-end" }}>
        {listSourceSystems && (
          <div>
            {caption("Source system", `${idPrefix}-source-system`)}
            <select
              id={`${idPrefix}-source-system`}
              className="srse-select"
              style={selectStyle}
              value={selectedSourceSystem}
              disabled={disabled}
              onChange={(e) => pickSourceSystem(e.target.value)}
            >
              <option value="">— any —</option>
              {sourceSystemOptions.map((s) => (
                <option key={s} value={s}>
                  {tagOptionLabel(s)}
                </option>
              ))}
            </select>
          </div>
        )}

        {listTableGroups && (
          <div>
            {caption("Table group", `${idPrefix}-table-group`)}
            <select
              id={`${idPrefix}-table-group`}
              className="srse-select"
              style={selectStyle}
              value={selectedTableGroup}
              disabled={disabled}
              onChange={(e) => pickTableGroup(e.target.value)}
            >
              <option value="">— any —</option>
              {tableGroupOptions.map((g) => (
                <option key={g} value={g}>
                  {tagOptionLabel(g)}
                </option>
              ))}
            </select>
          </div>
        )}

        {layerRequired && (
          <div>
            {caption("Layer", `${idPrefix}-layer`)}
            <select
              id={`${idPrefix}-layer`}
              className="srse-select"
              style={selectStyle}
              value={selectedLayer}
              disabled={disabled}
              onChange={(e) => pickLayer(e.target.value)}
            >
              <option value="">— layer —</option>
              {layerOptions.map((l) => (
                <option key={l} value={l}>
                  {tagOptionLabel(l)}
                </option>
              ))}
            </select>
          </div>
        )}

        <div>
          {caption("Catalog", `${idPrefix}-catalog`)}
          <select
            id={`${idPrefix}-catalog`}
            className="srse-select"
            style={selectStyle}
            value={value.catalog}
            disabled={catalogDisabled}
            onChange={(e) => pickCatalog(e.target.value)}
          >
            <option value="">{loading ? "— loading… —" : "— catalog —"}</option>
            {catalogOptions.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </div>

        <div>
          {caption("Schema", `${idPrefix}-schema`)}
          <select
            id={`${idPrefix}-schema`}
            className="srse-select"
            style={selectStyle}
            value={value.schema}
            disabled={disabled || !value.catalog}
            onChange={(e) => pickSchema(e.target.value)}
          >
            <option value="">— schema —</option>
            {schemas.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>

        <div>
          {caption("Table", `${idPrefix}-table`)}
          <select
            id={`${idPrefix}-table`}
            className="srse-select"
            style={selectStyle}
            value={value.table}
            disabled={disabled || !value.schema}
            onChange={(e) => pickTable(e.target.value)}
          >
            <option value="">— table —</option>
            {tables.map((t) => (
              <option key={t.name} value={t.name}>
                {t.layer ? `${t.name} (${t.layer})` : t.name}
              </option>
            ))}
          </select>
        </div>
      </div>
    </div>
  );
}
