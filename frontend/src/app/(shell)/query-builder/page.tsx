"use client";

import { useState } from "react";
import { useShell } from "@/components/shell/ShellProviders";
import ExtractRecordsTab from "@/components/query-builder/ExtractRecordsTab";
import { QueryBuilderSavedQueryOpen } from "@/components/query-builder/QueryBuilderSavedQueryOpen";
import ReportAnalysisTab from "@/components/query-builder/ReportAnalysisTab";
import { useQueryBuilderPipeline } from "@/lib/queryBuilderPipelineStore";

const TAB_EXTRACT_ID = "qb-tab-extract";
const TAB_REPORT_ID = "qb-tab-report";
const PANEL_EXTRACT_ID = "qb-panel-extract";
const PANEL_REPORT_ID = "qb-panel-report";
const REPORT_PREREQ_ID = "qb-report-prerequisite";

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

      <div className="tabs" role="tablist" aria-label={t("p2Title")}>
        <button
          type="button"
          role="tab"
          id={TAB_EXTRACT_ID}
          className={tab === 1 ? "active" : ""}
          aria-label="Extract Records tab"
          aria-selected={tab === 1}
          aria-controls={PANEL_EXTRACT_ID}
          tabIndex={tab === 1 ? 0 : -1}
          onClick={() => setTab(1)}
        >
          {t("tabExtract")}
        </button>
        <button
          type="button"
          role="tab"
          id={TAB_REPORT_ID}
          className={tab === 2 ? "active" : ""}
          onClick={() => setTab(2)}
          aria-label="Report Analysis tab"
          aria-selected={tab === 2}
          aria-controls={PANEL_REPORT_ID}
          aria-describedby={tab === 2 && !extractSucceeded ? REPORT_PREREQ_ID : undefined}
          tabIndex={tab === 2 ? 0 : -1}
          title={!extractSucceeded ? t("msgNoExtractYet") : undefined}
        >
          {t("tabReport")}
        </button>
      </div>

      {tab === 1 ? (
        <div id={PANEL_EXTRACT_ID} role="tabpanel" aria-labelledby={TAB_EXTRACT_ID} tabIndex={0}>
          <QueryBuilderSavedQueryOpen onLoaded={() => setTab(1)} />
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
        </div>
      ) : null}

      {tab === 2 && !extractSucceeded ? (
        <div
          id={PANEL_REPORT_ID}
          role="tabpanel"
          aria-labelledby={TAB_REPORT_ID}
          className="section empty-state"
          tabIndex={0}
        >
          <div id="qb2" role="status">
            <p id={REPORT_PREREQ_ID}>{t("msgNoExtractYet")}</p>
            <button type="button" className="btn secondary" onClick={() => setTab(1)}>
              {t("btnBackExtract")}
            </button>
          </div>
        </div>
      ) : null}

      {tab === 2 && extractSucceeded ? (
        <div id={PANEL_REPORT_ID} role="tabpanel" aria-labelledby={TAB_REPORT_ID} tabIndex={0}>
          <div id="qb2">
            <ReportAnalysisTab
              embedded
              onGoExtract={() => setTab(1)}
              onSavedQueryLoaded={() => setTab(1)}
            />
          </div>
        </div>
      ) : null}
    </div>
  );
}

export const queryBuilderTabIds = {
  TAB_EXTRACT_ID,
  TAB_REPORT_ID,
  PANEL_EXTRACT_ID,
  PANEL_REPORT_ID,
  REPORT_PREREQ_ID,
} as const;
