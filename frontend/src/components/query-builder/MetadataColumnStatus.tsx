"use client";

import { useEffect, useState } from "react";
import { isCascadeComplete, type CascadeValue } from "@/components/LakehouseCascade";
import { useShell } from "@/components/shell/ShellProviders";
import { fetchColumnMetadataStatus, type ColumnMetadataStatus } from "@/lib/analysisApi";

function fillCatalog(template: string, values: Record<string, string>): string {
  return template.replaceAll(/\{(\w+)\}/g, (_match, name: string) => values[name] ?? "");
}

export function MetadataColumnStatus({ tableRef }: Readonly<{ tableRef: CascadeValue }>) {
  const { t } = useShell();
  const [status, setStatus] = useState<ColumnMetadataStatus | null>(null);

  useEffect(() => {
    if (!isCascadeComplete(tableRef)) {
      setStatus(null);
      return;
    }
    let cancelled = false;
    fetchColumnMetadataStatus({
      catalog: tableRef.catalog!,
      schema: tableRef.schema!,
      table: tableRef.table!,
    })
      .then((next) => {
        if (!cancelled) setStatus(next);
      })
      .catch(() => {
        if (!cancelled) setStatus(null);
      });
    return () => {
      cancelled = true;
    };
  }, [tableRef.catalog, tableRef.schema, tableRef.table]);

  if (!status) {
    return null;
  }

  const dateLabel =
    status.syncedAt != null && status.syncedAt !== ""
      ? new Date(status.syncedAt).toLocaleDateString()
      : t("metadataStatusLive");

  return (
    <p className="metadata-column-status srse-text-muted" style={{ fontSize: "0.85rem", margin: "0.35rem 0 0" }}>
      {fillCatalog(t("metadataAsOf"), { date: dateLabel })}
      {status.degraded ? (
        <span className="metadata-degraded-badge" style={{ marginLeft: "0.5rem", color: "var(--srse-warning, #b45309)" }}>
          {t("metadataDegradedBadge")}
        </span>
      ) : null}
    </p>
  );
}
