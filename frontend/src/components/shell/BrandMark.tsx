"use client";

import { useShell } from "@/components/shell/ShellProviders";

export function BrandMark({ compact = false }: Readonly<{ compact?: boolean }>) {
  const { t } = useShell();
  return (
    <div className={`brand-mark${compact ? " brand-mark-compact" : ""}`}>
      <div className="brand-icon" aria-hidden>
        A
      </div>
      {!compact && (
        <div className="brand-info">
          <h1>{t("appTitle")}</h1>
          <span>{t("appSub")}</span>
        </div>
      )}
    </div>
  );
}
