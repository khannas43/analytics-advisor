"use client";

import { useCallback, useEffect, useState } from "react";
import {
  downloadAuditCsv,
  fetchAuditPage,
  type AuditEventRow,
  type AuditPage,
} from "@/lib/auditLogApi";

const ACTION_TYPES = [
  "LOGIN_SUCCESS",
  "LOGIN_FAILED",
  "LOGOUT",
  "QUERY_EXECUTED",
  "QUERY_REFUSED",
  "EXPORT",
  "SAVED_QUERY_CREATED",
  "SAVED_QUERY_UPDATED",
  "SAVED_QUERY_DELETED",
  "SAVED_QUERY_SHARED",
  "ROLE_GRANTED",
  "USER_CREATED",
  "CONTACT_CHANGED",
  "OTP_SMTP_GATEWAY_UPDATED",
  "OTP_SMTP_GATEWAY_TEST",
  "OTP_SMS_GATEWAY_UPDATED",
  "OTP_SMS_GATEWAY_TEST",
] as const;

export function AuditLogPanel() {
  const [filters, setFilters] = useState({
    from: "",
    to: "",
    actorUserId: "",
    actionType: "",
    outcome: "",
  });
  const [page, setPage] = useState(0);
  const [data, setData] = useState<AuditPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchAuditPage({
        from: filters.from || undefined,
        to: filters.to || undefined,
        actorUserId: filters.actorUserId ? Number(filters.actorUserId) : undefined,
        actionType: filters.actionType || undefined,
        outcome: filters.outcome || undefined,
        page,
        size: 50,
      });
      setData(result);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    load().catch(() => {});
  }, [load]);

  async function onExport() {
    try {
      await downloadAuditCsv({
        from: filters.from || undefined,
        to: filters.to || undefined,
        actorUserId: filters.actorUserId ? Number(filters.actorUserId) : undefined,
        actionType: filters.actionType || undefined,
        outcome: filters.outcome || undefined,
      });
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-default-500">
        Query shapes show SQL placeholders only — bound values are never stored (§7.3).
        {data?.retentionNote ? ` ${data.retentionNote} (${data.retentionDays} days configured).` : null}
      </p>
      <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-6 gap-2">
        <label className="text-xs">
          From (ISO)
          <input
            className="w-full border rounded px-2 py-1 text-sm"
            value={filters.from}
            onChange={(e) => setFilters((f) => ({ ...f, from: e.target.value }))}
            placeholder="2026-01-01T00:00:00Z"
          />
        </label>
        <label className="text-xs">
          To (ISO)
          <input
            className="w-full border rounded px-2 py-1 text-sm"
            value={filters.to}
            onChange={(e) => setFilters((f) => ({ ...f, to: e.target.value }))}
          />
        </label>
        <label className="text-xs">
          Actor user id
          <input
            className="w-full border rounded px-2 py-1 text-sm"
            value={filters.actorUserId}
            onChange={(e) => setFilters((f) => ({ ...f, actorUserId: e.target.value }))}
          />
        </label>
        <label className="text-xs">
          Action
          <select
            className="w-full border rounded px-2 py-1 text-sm"
            value={filters.actionType}
            onChange={(e) => setFilters((f) => ({ ...f, actionType: e.target.value }))}
          >
            <option value="">Any</option>
            {ACTION_TYPES.map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </select>
        </label>
        <label className="text-xs">
          Outcome
          <select
            className="w-full border rounded px-2 py-1 text-sm"
            value={filters.outcome}
            onChange={(e) => setFilters((f) => ({ ...f, outcome: e.target.value }))}
          >
            <option value="">Any</option>
            <option value="SUCCESS">SUCCESS</option>
            <option value="FAILURE">FAILURE</option>
            <option value="REFUSED">REFUSED</option>
          </select>
        </label>
        <div className="flex items-end gap-2">
          <button
            type="button"
            className="px-3 py-1 rounded bg-primary text-white text-sm"
            onClick={() => {
              setPage(0);
              load();
            }}
          >
            Apply
          </button>
          <button type="button" className="px-3 py-1 rounded border text-sm" onClick={onExport}>
            Export CSV
          </button>
        </div>
      </div>
      {error ? <p className="text-danger text-sm">{error}</p> : null}
      {loading ? <p className="text-sm">Loading…</p> : null}
      <div className="overflow-x-auto border rounded">
        <table className="min-w-full text-xs">
          <thead className="bg-default-100">
            <tr>
              <th className="p-2 text-left">When</th>
              <th className="p-2 text-left">Actor</th>
              <th className="p-2 text-left">Action</th>
              <th className="p-2 text-left">Outcome</th>
              <th className="p-2 text-left">Detail</th>
              <th className="p-2 text-left">Query shape</th>
            </tr>
          </thead>
          <tbody>
            {(data?.entries ?? []).map((row: AuditEventRow) => (
              <tr key={row.id} className="border-t">
                <td className="p-2 whitespace-nowrap">{row.occurredAt}</td>
                <td className="p-2">{row.actorUsername ?? row.actorUserId ?? "—"}</td>
                <td className="p-2">{row.actionType}</td>
                <td className="p-2">{row.outcome ?? "—"}</td>
                <td className="p-2 max-w-xs truncate" title={row.detail ?? ""}>
                  {row.detail ?? "—"}
                </td>
                <td className="p-2 max-w-md truncate font-mono" title={row.queryShape ?? ""}>
                  {row.queryShape ?? "—"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {data && data.totalPages > 1 ? (
        <div className="flex gap-2 items-center text-sm">
          <button
            type="button"
            disabled={page <= 0}
            className="px-2 py-1 border rounded disabled:opacity-40"
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Previous
          </button>
          <span>
            Page {data.page + 1} of {data.totalPages} ({data.totalElements} rows)
          </span>
          <button
            type="button"
            disabled={page + 1 >= data.totalPages}
            className="px-2 py-1 border rounded disabled:opacity-40"
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </button>
        </div>
      ) : null}
    </div>
  );
}
