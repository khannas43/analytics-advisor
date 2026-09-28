"use client";

import { type FormEvent, type KeyboardEvent, useEffect, useId, useMemo, useRef, useState } from "react";
import {
  browseExternalCatalogs,
  browseExternalColumns,
  browseExternalSchemas,
  browseExternalTables,
  createExternalDataSource,
  deleteExternalDataSource,
  listExternalDataSources,
  listExternalSourceTypes,
  listExternalTableRegistrations,
  registerExternalTable,
  testExternalDataSource,
  unregisterExternalTable,
  updateExternalFederationCatalog,
  type ExternalColumn,
  type ExternalDataSource,
  type ExternalSourceType,
  type ExternalTable,
  type ExternalTablePage,
  type ExternalTableRegistration,
} from "@/lib/externalDataSourceApi";

const EMPTY_TABLE_PAGE: ExternalTablePage = { items: [], page: 0, size: 25, total: 0 };

function message(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function tableIdentity(table: ExternalTable): string {
  return `${table.catalog ?? ""}\u0000${table.schema ?? ""}\u0000${table.name}`;
}

function registrationFor(
  table: ExternalTable,
  rows: ExternalTableRegistration[],
): ExternalTableRegistration | undefined {
  return rows.find((row) => {
    if (row.table !== table.name) return false;
    const catalog = table.catalog ?? "";
    const storedCatalog = row.physicalCatalog ?? "";
    if (catalog !== "" && storedCatalog !== "" && catalog !== storedCatalog) return false;
    const schema = table.schema ?? "";
    if (schema !== "") return row.schema === schema;
    return row.schema === catalog || row.schema === "";
  });
}

export function ExternalDataSourcesPanel() {
  const [types, setTypes] = useState<ExternalSourceType[]>([]);
  const [sources, setSources] = useState<ExternalDataSource[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [federationFor, setFederationFor] = useState<ExternalDataSource | null>(null);
  const [federationError, setFederationError] = useState<string | null>(null);
  const [federationBusy, setFederationBusy] = useState(false);
  const federationButtonRef = useRef<HTMLButtonElement | null>(null);

  async function refresh(preferredId?: number) {
    const rows = await listExternalDataSources();
    setSources(rows);
    const next = preferredId ?? selectedId;
    setSelectedId(next != null && rows.some((row) => row.id === next) ? next : (rows[0]?.id ?? null));
  }

  useEffect(() => {
    let cancelled = false;
    Promise.all([listExternalSourceTypes(), listExternalDataSources()])
      .then(([sourceTypes, sourceRows]) => {
        if (cancelled) return;
        setTypes(sourceTypes);
        setSources(sourceRows);
        setSelectedId(sourceRows[0]?.id ?? null);
      })
      .catch((cause: unknown) => !cancelled && setError(message(cause)))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

  const selected = useMemo(
    () => sources.find((source) => source.id === selectedId) ?? null,
    [selectedId, sources],
  );

  async function runTest(source: ExternalDataSource) {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const result = await testExternalDataSource(source.id);
      if (result.success) {
        setNotice(result.message);
      } else {
        setError(result.message);
      }
      await refresh(source.id);
    } catch (cause: unknown) {
      setError(message(cause));
    } finally {
      setBusy(false);
    }
  }

  async function remove(source: ExternalDataSource) {
    if (!window.confirm(`Remove data source “${source.name}”?`)) return;
    setBusy(true);
    setError(null);
    try {
      await deleteExternalDataSource(source.id);
      await refresh();
      setNotice(`Removed ${source.name}.`);
    } catch (cause: unknown) {
      setError(message(cause));
    } finally {
      setBusy(false);
    }
  }

  function closeFederation() {
    setFederationFor(null);
    setFederationError(null);
    federationButtonRef.current?.focus();
  }

  async function saveFederation(alias: string) {
    if (!federationFor) return;
    setFederationBusy(true);
    setFederationError(null);
    setError(null);
    try {
      await updateExternalFederationCatalog(federationFor.id, alias || null);
      await refresh(federationFor.id);
      setNotice(alias ? `Federation alias set to ${alias}.` : "Federation alias removed.");
      closeFederation();
    } catch (cause: unknown) {
      setFederationError(message(cause));
    } finally {
      setFederationBusy(false);
    }
  }

  return (
    <div className="external-source-layout">
      <section className="srse-card external-source-list" aria-label="Configured data sources">
        <div className="external-source-card-header">
          <div>
            <h2 className="srse-card-title">Data sources</h2>
            <p className="srse-text-muted">Connections are available only to Super Admins.</p>
          </div>
          <button type="button" className="srse-btn srse-btn-primary" onClick={() => setShowForm((open) => !open)}>
            {showForm ? "Cancel" : "Add connection"}
          </button>
        </div>

        {showForm && (
          <ExternalSourceForm
            types={types}
            busy={busy}
            onSave={async (source) => {
              setBusy(true);
              setError(null);
              try {
                const created = await createExternalDataSource(source);
                await refresh(created.id);
                setShowForm(false);
                setNotice(`${created.name} connected successfully.`);
              } catch (cause: unknown) {
                setError(message(cause));
              } finally {
                setBusy(false);
              }
            }}
          />
        )}

        {error && <p className="srse-text-danger" role="alert">{error}</p>}
        {notice && <p className="srse-text-success" role="status">{notice}</p>}
        {loading && <p className="srse-text-muted">Loading data sources…</p>}
        {!loading && sources.length === 0 && !showForm && (
          <div className="external-source-empty">
            <strong>No data sources connected</strong>
            <span>Add PostgreSQL or DB2 to browse its schemas, tables and columns.</span>
          </div>
        )}
        <div className="external-source-cards">
          {sources.map((source) => (
            <article
              key={source.id}
              className={source.id === selectedId ? "external-source-item active" : "external-source-item"}
            >
              <button type="button" className="external-source-select" onClick={() => setSelectedId(source.id)}>
                <span>
                  <strong>{source.name}</strong>
                  <small>{source.databaseTypeLabel} · {source.username}</small>
                  {source.federationCatalog && <small>Presto: {source.federationCatalog}</small>}
                </span>
                <span className={`external-source-status ${source.lastTestStatus?.toLowerCase() ?? "unknown"}`}>
                  {source.lastTestStatus ?? "Not tested"}
                </span>
              </button>
              <div className="external-source-actions">
                <button
                  type="button"
                  className="srse-btn srse-btn-ghost srse-btn-sm"
                  disabled={busy || federationBusy}
                  onClick={(event) => {
                    federationButtonRef.current = event.currentTarget;
                    setFederationError(null);
                    setFederationFor(source);
                  }}
                >
                  Federation
                </button>
                <button type="button" className="srse-btn srse-btn-ghost srse-btn-sm" disabled={busy} onClick={() => runTest(source)}>
                  Test
                </button>
                <button
                  type="button"
                  className="srse-btn srse-btn-danger srse-btn-sm"
                  disabled={busy || (source.registrationCount ?? 0) > 0}
                  aria-describedby={(source.registrationCount ?? 0) > 0 ? `remove-note-${source.id}` : undefined}
                  onClick={() => remove(source)}
                >
                  Remove
                </button>
              </div>
              {(source.registrationCount ?? 0) > 0 && (
                <p className="external-source-remove-note" id={`remove-note-${source.id}`}>
                  Remove its {source.registrationCount} registered table{source.registrationCount === 1 ? "" : "s"} before deleting this connection.
                </p>
              )}
            </article>
          ))}
        </div>
      </section>

      <section className="srse-card external-source-explorer" aria-label="Database explorer">
        <h2 className="srse-card-title">Database explorer</h2>
        {selected ? (
          <ExternalMetadataExplorer
            key={selected.id}
            source={selected}
            onRegistrationsChanged={() => { void refresh(selected.id); }}
          />
        ) : (
          <p className="srse-text-muted">Select or add a data source to browse its metadata.</p>
        )}
      </section>
      {federationFor && (
        <FederationAliasDialog
          source={federationFor}
          busy={federationBusy}
          error={federationError}
          onCancel={() => { if (!federationBusy) closeFederation(); }}
          onSave={saveFederation}
        />
      )}
    </div>
  );
}

function FederationAliasDialog({
  source,
  busy,
  error,
  onCancel,
  onSave,
}: Readonly<{
  source: ExternalDataSource;
  busy: boolean;
  error: string | null;
  onCancel: () => void;
  onSave: (alias: string) => Promise<void>;
}>) {
  const titleId = useId();
  const helpId = useId();
  const inputId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [alias, setAlias] = useState(source.federationCatalog ?? "");
  const [localError, setLocalError] = useState<string | null>(null);

  useEffect(() => {
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);

  function onKeyDown(event: KeyboardEvent<HTMLFormElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      if (!busy) onCancel();
      return;
    }
    if (event.key !== "Tab") return;
    const focusable = event.currentTarget.querySelectorAll<HTMLElement>(
      "button:not(:disabled), input:not(:disabled)",
    );
    if (focusable.length === 0) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = alias.trim();
    if (trimmed !== "" && !/^[A-Za-z_][A-Za-z0-9_]*$/.test(trimmed)) {
      setLocalError("Use letters, digits, and underscores, starting with a letter or underscore.");
      return;
    }
    setLocalError(null);
    await onSave(trimmed);
  }

  const shownError = localError ?? error;

  return (
    <div
      className="external-dialog-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !busy) onCancel();
      }}
    >
      <form
        className="external-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={helpId}
        aria-busy={busy}
        onKeyDown={onKeyDown}
        onSubmit={submit}
      >
        <h3 id={titleId}>Presto catalog alias</h3>
        <p id={helpId} className="srse-text-muted">
          Optional name of a Presto catalog that already points at this database.
          Query Builder uses it only when a question combines this database with another source.
          Leave the alias blank to turn that off. This form never shows the database password.
        </p>
        <label className="field" htmlFor={inputId}>
          Catalog alias
          <input
            id={inputId}
            ref={inputRef}
            className="srse-input"
            value={alias}
            onChange={(event) => setAlias(event.target.value)}
            autoComplete="off"
            spellCheck={false}
            maxLength={128}
            aria-describedby={helpId}
            disabled={busy}
          />
        </label>
        {shownError && <p className="srse-text-danger" role="alert">{shownError}</p>}
        <div className="external-dialog-actions">
          <button type="button" className="srse-btn srse-btn-ghost" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="srse-btn srse-btn-primary" disabled={busy}>
            {busy ? "Saving…" : "Save"}
          </button>
        </div>
      </form>
    </div>
  );
}

function ExternalSourceForm({
  types,
  busy,
  onSave,
}: Readonly<{
  types: ExternalSourceType[];
  busy: boolean;
  onSave: (source: {
    name: string;
    databaseType: string;
    host: string;
    port: number;
    database: string;
    username: string;
    password: string;
    ssl: boolean;
    federationCatalog?: string;
  }) => Promise<void>;
}>) {
  const fallback = types[0] ?? { code: "POSTGRESQL", label: "PostgreSQL", defaultPort: 5432 };
  const [databaseType, setDatabaseType] = useState(fallback.code);
  const [port, setPort] = useState(fallback.defaultPort);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    await onSave({
      name: String(form.get("name") ?? ""),
      databaseType,
      host: String(form.get("host") ?? ""),
      port,
      database: String(form.get("database") ?? ""),
      username: String(form.get("username") ?? ""),
      password: String(form.get("password") ?? ""),
      ssl: form.get("ssl") === "on",
      federationCatalog: String(form.get("federationCatalog") ?? "").trim() || undefined,
    });
  }

  return (
    <form className="external-source-form" onSubmit={submit}>
      <label className="field">Connection name<input className="srse-input" name="name" required maxLength={128} /></label>
      <label className="field">Database type
        <select
          className="srse-select"
          value={databaseType}
          onChange={(event) => {
            const next = event.target.value;
            setDatabaseType(next);
            setPort(types.find((type) => type.code === next)?.defaultPort ?? port);
          }}
        >
          {(types.length ? types : [fallback]).map((type) => <option key={type.code} value={type.code}>{type.label}</option>)}
        </select>
      </label>
      <label className="field">Host<input className="srse-input" name="host" required placeholder="database.example.gov.in" /></label>
      <label className="field">Port<input className="srse-input" name="port" type="number" min={1} max={65535} value={port} onChange={(event) => setPort(Number(event.target.value))} required /></label>
      <label className="field">Database<input className="srse-input" name="database" required /></label>
      <label className="field">Username<input className="srse-input" name="username" autoComplete="username" required /></label>
      <label className="field">Password<input className="srse-input" name="password" type="password" autoComplete="new-password" required /></label>
      <label className="checkbox-row"><input name="ssl" type="checkbox" /> Require SSL</label>
      <label className="field">Presto catalog alias <span className="srse-text-muted">(optional)</span>
        <input className="srse-input" name="federationCatalog" placeholder="For joins across data sources" pattern="[A-Za-z_][A-Za-z0-9_]*" />
      </label>
      <button type="submit" className="srse-btn srse-btn-primary" disabled={busy}>{busy ? "Testing…" : "Test & save"}</button>
      <p className="srse-text-muted external-source-form-note">The password is encrypted before storage and is never returned to the browser.</p>
    </form>
  );
}

function ExternalMetadataExplorer({
  source,
  onRegistrationsChanged,
}: Readonly<{ source: ExternalDataSource; onRegistrationsChanged?: () => void }>) {
  const [catalogs, setCatalogs] = useState<string[]>([]);
  const [catalog, setCatalog] = useState("");
  const [schemas, setSchemas] = useState<string[]>([]);
  const [schema, setSchema] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [tables, setTables] = useState<ExternalTablePage>(EMPTY_TABLE_PAGE);
  const [selectedTable, setSelectedTable] = useState<ExternalTable | null>(null);
  const [columns, setColumns] = useState<ExternalColumn[]>([]);
  const [registrations, setRegistrations] = useState<ExternalTableRegistration[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([browseExternalCatalogs(source.id), listExternalTableRegistrations(source.id)])
      .then(([values, registered]) => {
        if (cancelled) return;
        setCatalogs(values);
        setCatalog(values[0] ?? "");
        setRegistrations(registered);
      })
      .catch((cause: unknown) => !cancelled && setError(message(cause)))
      .finally(() => !cancelled && setLoading(false));
    return () => { cancelled = true; };
  }, [source.id]);

  useEffect(() => {
    let cancelled = false;
    browseExternalSchemas(source.id, catalog)
      .then((values) => {
        if (cancelled) return;
        setSchemas(values);
        setSchema(values[0] ?? "");
        setPage(0);
      })
      .catch((cause: unknown) => !cancelled && setError(message(cause)))
      .finally(() => !cancelled && setLoading(false));
    return () => { cancelled = true; };
  }, [catalog, source.id]);

  useEffect(() => {
    let cancelled = false;
    browseExternalTables(source.id, { catalog, schema, search, page, size: 25 })
      .then((value) => !cancelled && setTables(value))
      .catch((cause: unknown) => !cancelled && setError(message(cause)))
      .finally(() => !cancelled && setLoading(false));
    return () => { cancelled = true; };
  }, [catalog, page, schema, search, source.id]);

  async function selectTable(table: ExternalTable) {
    setSelectedTable(table);
    setColumns([]);
    setLoading(true);
    setError(null);
    try {
      setColumns(await browseExternalColumns(source.id, table));
    } catch (cause: unknown) {
      setError(message(cause));
    } finally {
      setLoading(false);
    }
  }

  const lastPage = Math.max(0, Math.ceil(tables.total / tables.size) - 1);
  const selectedRegistration = selectedTable ? registrationFor(selectedTable, registrations) : undefined;

  async function toggleRegistration() {
    if (!selectedTable) return;
    setLoading(true);
    setError(null);
    try {
      if (selectedRegistration) {
        await unregisterExternalTable(source.id, selectedRegistration.registrationId);
      } else {
        await registerExternalTable(source.id, selectedTable, { sourceSystem: source.name });
      }
      setRegistrations(await listExternalTableRegistrations(source.id));
      onRegistrationsChanged?.();
    } catch (cause: unknown) {
      setError(message(cause));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="external-metadata-explorer">
      <p className="external-source-address"><strong>{source.name}</strong><code>{source.jdbcUrl}</code></p>
      <div className="external-explorer-filters">
        <label className="field">Catalog
          <select className="srse-select" value={catalog} onChange={(event) => { setCatalog(event.target.value); setSelectedTable(null); setColumns([]); }}>
            {catalogs.length === 0 && <option value="">Default</option>}
            {catalogs.map((value) => <option key={value} value={value}>{value}</option>)}
          </select>
        </label>
        <label className="field">Schema
          <select className="srse-select" value={schema} onChange={(event) => { setSchema(event.target.value); setPage(0); setSelectedTable(null); setColumns([]); }}>
            {schemas.length === 0 && <option value="">All accessible schemas</option>}
            {schemas.map((value) => <option key={value} value={value}>{value}</option>)}
          </select>
        </label>
        <label className="field">Find table
          <input className="srse-input" type="search" value={search} onChange={(event) => { setSearch(event.target.value); setPage(0); }} placeholder="Search tables and views" />
        </label>
      </div>
      {error && <p className="srse-text-danger" role="alert">{error}</p>}
      {loading && <p className="srse-text-muted">Reading database metadata…</p>}
      <div className="external-explorer-grid">
        <div className="external-table-browser">
          <div className="external-table-browser-head"><strong>Tables and views</strong><span>{tables.total.toLocaleString()}</span></div>
          <ul>
            {tables.items.map((table) => {
              const registered = registrationFor(table, registrations);
              return (
              <li key={tableIdentity(table)}>
                <button
                  type="button"
                  className={selectedTable && tableIdentity(selectedTable) === tableIdentity(table) ? "active" : ""}
                  aria-pressed={selectedTable != null && tableIdentity(selectedTable) === tableIdentity(table)}
                  onClick={() => selectTable(table)}
                >
                  <span>{table.name}</span>
                  <small>{registered ? "Registered" : table.type}</small>
                </button>
              </li>
              );
            })}
          </ul>
          <div className="external-table-pager">
            <button type="button" className="srse-btn srse-btn-ghost srse-btn-sm" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</button>
            <span>Page {page + 1} of {lastPage + 1}</span>
            <button type="button" className="srse-btn srse-btn-ghost srse-btn-sm" disabled={page >= lastPage} onClick={() => setPage((value) => value + 1)}>Next</button>
          </div>
        </div>
        <div className="external-column-browser">
          <div className="external-column-heading">
            <h3>{selectedTable ? `${selectedTable.name} columns` : "Columns"}</h3>
            {selectedTable && (
              <button
                type="button"
                className={selectedRegistration ? "srse-btn srse-btn-ghost srse-btn-sm" : "srse-btn srse-btn-primary srse-btn-sm"}
                disabled={loading}
                onClick={toggleRegistration}
              >
                {selectedRegistration ? "Remove from Query Builder" : "Register for Query Builder"}
              </button>
            )}
          </div>
          {selectedRegistration && (
            <p className="external-registration-note" role="status">
              Available to permitted officers as <code>{selectedRegistration.logicalCatalog}.{selectedRegistration.schema}.{selectedRegistration.table}</code>.
            </p>
          )}
          {!selectedTable && <p className="srse-text-muted">Select a table to inspect its columns.</p>}
          {selectedTable && (
            <div className="results-scroll"><table className="data-table">
              <thead><tr><th>Column</th><th>Type</th><th>Nullable</th><th>Key</th></tr></thead>
              <tbody>{columns.map((column) => (
                <tr key={column.name}>
                  <td>{column.name}</td>
                  <td><code>{column.dataType}{column.size ? `(${column.size})` : ""}</code></td>
                  <td>{column.nullable ? "Yes" : "No"}</td>
                  <td>{column.primaryKey ? "Primary" : "—"}</td>
                </tr>
              ))}</tbody>
            </table></div>
          )}
        </div>
      </div>
    </div>
  );
}
