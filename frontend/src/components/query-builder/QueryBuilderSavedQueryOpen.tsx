"use client";

import { useEffect, useState } from "react";
import { useShell } from "@/components/shell/ShellProviders";
import { getSavedQuery, listSavedQueries, type SavedQuerySummary } from "@/lib/savedQueryApi";
import { dualModeFromRequest } from "@/lib/savedQueryHydrate";
import { useQueryBuilderPipeline } from "@/lib/queryBuilderPipelineStore";

/** Lets officers reopen a saved query after navigation or reload, before Extract has run again. */
export function QueryBuilderSavedQueryOpen({ onLoaded }: { onLoaded: () => void }) {
  const { t } = useShell();
  const [queries, setQueries] = useState<SavedQuerySummary[]>([]);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    void listSavedQueries()
      .then(setQueries)
      .catch(() => setQueries([]));
  }, []);

  return (
    <section role="region" aria-label={t("qbSavedQueries")} className="section" style={{ marginBottom: "0.75rem" }}>
      <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem", alignItems: "center" }}>
        <select
          className="srse-input"
          aria-label="Open saved query"
          defaultValue=""
          onChange={async (e) => {
            const id = Number(e.target.value);
            e.target.value = "";
            if (!id) return;
            try {
              const detail = await getSavedQuery(id);
              const dual = dualModeFromRequest(detail.request);
              useQueryBuilderPipeline.getState().loadFromMergedRequest(dual, detail.request);
              setMessage(`Loaded “${detail.name}”. Review Extract, then run the query.`);
              onLoaded();
            } catch (err: unknown) {
              setMessage(err instanceof Error ? err.message : String(err));
            }
          }}
        >
          <option value="">{t("qbOpenSaved")}</option>
          {queries.map((q) => (
            <option key={q.id} value={q.id}>
              {q.name}
              {!q.ownedByMe ? " (shared)" : ""}
            </option>
          ))}
        </select>
      </div>
      {message && (
        <p className="srse-text-muted" style={{ marginBottom: 0, fontSize: "0.85rem" }}>
          {message}
        </p>
      )}
    </section>
  );
}
