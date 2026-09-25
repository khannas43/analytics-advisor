"use client";

import { useState } from "react";
import { useShell } from "@/components/shell/ShellProviders";
import ExtractRecordsTab from "@/components/query-builder/ExtractRecordsTab";
import ReportAnalysisTab from "@/components/query-builder/ReportAnalysisTab";

export default function QueryBuilderPage() {
  const { t } = useShell();
  const [tab, setTab] = useState<1 | 2>(1);
  const [dualMode, setDualMode] = useState(true);
  const [hasExtract, setHasExtract] = useState(false);

  return (
    <div className="page active" data-mode={dualMode ? "dual" : "single"}>
      <div className="top-action-bar">
        <div className="page-header">
          <h2>{t("p2Title")}</h2>
          <p className="sub">{t("p2Sub")}</p>
        </div>
      </div>

      <div className="tabs">
        <button type="button" className={tab === 1 ? "active" : ""} onClick={() => setTab(1)}>
          {t("tabExtract")}
        </button>
        <button type="button" className={tab === 2 ? "active" : ""} onClick={() => setTab(2)}>
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
              onClick={() => setDualMode(true)}
            >
              {t("modeDual")}
            </button>
            <button
              type="button"
              className={!dualMode ? "active" : ""}
              onClick={() => setDualMode(false)}
            >
              {t("modeSingle")}
            </button>
          </div>
          <ExtractRecordsTab
            dualMode={dualMode}
            onExtractComplete={() => setHasExtract(true)}
            onGoReport={() => {
              setHasExtract(true);
              setTab(2);
            }}
          />
        </div>
      )}

      {tab === 2 && (
        <div id="qb2">
          {!hasExtract ? (
            <div className="section">
              <div className="empty">{t("msgNoExtractYet")}</div>
              <button type="button" className="btn" style={{ marginTop: 14 }} onClick={() => setTab(1)}>
                ← {t("btnBackExtract")}
              </button>
            </div>
          ) : (
            <ReportAnalysisTab embedded onRunComplete={() => setHasExtract(true)} />
          )}
        </div>
      )}
    </div>
  );
}
