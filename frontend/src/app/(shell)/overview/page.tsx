"use client";

import { useShell } from "@/components/shell/ShellProviders";
import DatabaseOverviewView from "@/components/overview/DatabaseOverviewView";

export default function OverviewPage() {
  const { t } = useShell();

  return (
    <div className="page active">
      <div className="top-action-bar">
        <div className="page-header">
          <h2>{t("p1Title")}</h2>
          <p className="sub">{t("p1Sub")}</p>
        </div>
      </div>
      <DatabaseOverviewView />
    </div>
  );
}
