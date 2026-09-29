"use client";

import { useId } from "react";
import ValueFilterPicker from "@/components/ValueFilterPicker";
import { useShell } from "@/components/shell/ShellProviders";
import type { RegisteredColumn, TableRef } from "@/lib/analysisApi";
import type { ReportConfig } from "@/lib/queryBuilderPipelineTypes";

function thresholdFromInput(raw: string): number | null {
  if (raw.trim() === "") return 0;
  const parsed = Number(raw);
  if (!Number.isFinite(parsed)) return null;
  return Math.min(100, Math.max(0, parsed));
}

export function ReportSourceFilters({
  table,
  columns,
  report,
  onPatch,
}: Readonly<{
  table: TableRef;
  columns: RegisteredColumn[];
  report: ReportConfig;
  onPatch: (patch: Partial<ReportConfig>) => void;
}>) {
  const { t } = useShell();
  const baseId = useId();
  const valueEnabledId = `${baseId}-value-enabled`;
  const valueColumnId = `${baseId}-value-column`;
  const fuzzyEnabledId = `${baseId}-fuzzy-enabled`;
  const fuzzyColumnId = `${baseId}-fuzzy-column`;
  const fuzzyNameId = `${baseId}-fuzzy-name`;
  const fuzzyThresholdId = `${baseId}-fuzzy-threshold`;
  const fuzzyIgnoreSpacesId = `${baseId}-fuzzy-ignore-spaces`;
  const fuzzyCaseSensitiveId = `${baseId}-fuzzy-case-sensitive`;

  return (
    <div>
      <div role="group" aria-label={t("secFilter")}>
        <label className="checkbox-row" htmlFor={valueEnabledId}>
          <input
            id={valueEnabledId}
            type="checkbox"
            checked={report.sourceValueFilterEnabled}
            onChange={(event) => onPatch({ sourceValueFilterEnabled: event.target.checked })}
          />
          {t("qbValuePickerIn")}
        </label>
        {report.sourceValueFilterEnabled ? (
          <>
            <div className="field">
              <label htmlFor={valueColumnId}>{t("lblColumn")}</label>
              <select
                id={valueColumnId}
                value={report.sourceValueFilterColumn}
                onChange={(event) =>
                  onPatch({
                    sourceValueFilterColumn: event.target.value,
                    sourceValueFilterSpec: null,
                  })
                }
              >
                <option value="">{t("selNone")}</option>
                {columns.map((column) => (
                  <option key={column.name} value={column.name}>
                    {column.businessName ?? column.name}
                  </option>
                ))}
              </select>
            </div>
            <ValueFilterPicker
              key={report.sourceValueFilterColumn}
              table={table}
              column={report.sourceValueFilterColumn}
              hydratedSpec={report.sourceValueFilterSpec}
              onChange={(spec) => onPatch({ sourceValueFilterSpec: spec })}
            />
          </>
        ) : null}
      </div>
      <div role="group" aria-label={t("secFuzzy")}>
        <label className="checkbox-row" htmlFor={fuzzyEnabledId}>
          <input
            id={fuzzyEnabledId}
            type="checkbox"
            checked={report.sourceFuzzyEnabled}
            onChange={(event) => onPatch({ sourceFuzzyEnabled: event.target.checked })}
          />
          {t("qbFuzzyRuleScoped")}
        </label>
        {report.sourceFuzzyEnabled ? (
          <div className="row">
            <div className="field">
              <label htmlFor={fuzzyColumnId}>{t("lblColumn")}</label>
              <select
                id={fuzzyColumnId}
                value={report.sourceFuzzyColumn}
                onChange={(event) => onPatch({ sourceFuzzyColumn: event.target.value })}
              >
                <option value="">{t("selNone")}</option>
                {columns.map((column) => (
                  <option key={column.name} value={column.name}>
                    {column.businessName ?? column.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor={fuzzyNameId}>{t("qbFuzzyName")}</label>
              <input
                id={fuzzyNameId}
                type="text"
                value={report.sourceFuzzyName}
                onChange={(event) => onPatch({ sourceFuzzyName: event.target.value })}
              />
            </div>
            <div className="field">
              <label htmlFor={fuzzyThresholdId}>{t("qbFuzzyThreshold")}</label>
              <input
                id={fuzzyThresholdId}
                type="number"
                min={0}
                max={100}
                value={report.sourceFuzzyThreshold}
                onChange={(event) => {
                  const threshold = thresholdFromInput(event.target.value);
                  if (threshold !== null) onPatch({ sourceFuzzyThreshold: threshold });
                }}
              />
            </div>
            <div className="field">
              <label className="checkbox-row" htmlFor={fuzzyIgnoreSpacesId}>
                <input
                  id={fuzzyIgnoreSpacesId}
                  type="checkbox"
                  checked={report.sourceFuzzyIgnoreSpaces}
                  onChange={(event) => onPatch({ sourceFuzzyIgnoreSpaces: event.target.checked })}
                />
                {t("qbFuzzyIgnoreSpaces")}
              </label>
              <label className="checkbox-row" htmlFor={fuzzyCaseSensitiveId}>
                <input
                  id={fuzzyCaseSensitiveId}
                  type="checkbox"
                  checked={report.sourceFuzzyCaseSensitive}
                  onChange={(event) => onPatch({ sourceFuzzyCaseSensitive: event.target.checked })}
                />
                {t("qbFuzzyCaseSensitive")}
              </label>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}
