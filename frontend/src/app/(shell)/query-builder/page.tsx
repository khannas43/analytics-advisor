"use client";

import { useState } from "react";
import { useShell } from "@/components/shell/ShellProviders";
import ExtractRecordsTab from "@/components/query-builder/ExtractRecordsTab";
import { QueryBuilderSavedQueryOpen } from "@/components/query-builder/QueryBuilderSavedQueryOpen";
import ReportAnalysisTab from "@/components/query-builder/ReportAnalysisTab";
import { useQueryBuilderPipeline } from "@/lib/queryBuilderPipelineStore";

export default function QueryBuilderPage() {
  const { t } = useShell();
  const [tab, setTab] = useState<1 | 2>(1);
  const dualMode = useQueryBuilderPipeline((s) => s.dualMode);
  const setDualMode = useQueryBuilderPipeline((s) => s.setDualMode);
  const extractSucceeded = useQueryBuilderPipeline((s) => s.extractSucceeded);

  return (
    <div className="page active" data-mode={dualMode ? "dual" : "single"}>
      <div className="top-action-bar">
        <div className="page-header">
          <h2>{t("p2Title")}</h2>
          <p className="sub">{t("p2Sub")}</p>
        </div>
      </div>

      <QueryBuilderSavedQueryOpen onLoaded={() => setTab(1)} />

      <div className="tabs">
        <button
          type="button"
          className={tab === 1 ? "active" : ""}
          aria-label="Extract Records tab"
          onClick={() => setTab(1)}
        >
          {t("tabExtract")}
        </button>
        <button
          type="button"
          className={tab === 2 ? "active" : ""}
          onClick={() => setTab(2)}
          aria-label="Report Analysis tab"
          aria-disabled={!extractSucceeded}
          title={!extractSucceeded ? t("msgNoExtractYet") : undefined}
        >
          {t("tabReport")}
        </button>
      </div>

      {tab === 1 && (
        <div id="qb1">
          <div className="mode-switch">
            <span className="mode-label">{t("lblMode")}</span>
            <button
              type="button"
              className={dualMode ? "active" : ""}
              aria-label="Dual Source mode"
              onClick={() => setDualMode(true)}
            >
              {t("modeDual")}
            </button>
            <button
              type="button"
              className={!dualMode ? "active" : ""}
              aria-label="Single Source mode"
              onClick={() => setDualMode(false)}
            >
              {t("modeSingle")}
            </button>
          </div>
          <ExtractRecordsTab onGoReport={() => setTab(2)} />
        </div>
      )}

      {tab === 2 && !extractSucceeded && (
        <div id="qb2" className="section empty-state" role="status">
          <p>{t("msgNoExtractYet")}</p>
          <button type="button" className="btn secondary" onClick={() => setTab(1)}>
            {t("btnBackExtract")}
          </button>
        </div>
      )}

      {tab === 2 && extractSucceeded && (
        <div id="qb2">
          <ReportAnalysisTab
            embedded
            onGoExtract={() => setTab(1)}
            onSavedQueryLoaded={() => setTab(1)}
          />
        </div>
      )}
    </div>
  );
}
