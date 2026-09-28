"use client";

import { type ReactNode } from "react";
import type { I18nKey } from "@/lib/i18n/catalog";
import { ResultColumnSelector } from "@/components/results/ResultColumnSelector";
import { RESULT_PAGE_SIZE_OPTIONS } from "@/lib/resultTableConstants";

export type ResultTableToolbarProps = Readonly<{
  appearance?: "legacy" | "prototype";
  rowCountLabel: ReactNode;
  columns: string[];
  hiddenColumnIds: string[];
  onHiddenChange: (hidden: string[]) => void;
  labelFor: (id: string) => string;
  streaming?: boolean;
  pageSize: number;
  onPageSizeChange: (size: number) => void;
  pageIndex: number;
  pageCount: number;
  totalFilteredRows: number;
  totalLoadedRows: number;
  onFirstPage: () => void;
  onPrevPage: () => void;
  onNextPage: () => void;
  onLastPage: () => void;
  canPrevious: boolean;
  canNext: boolean;
  onDownloadCsv: () => void;
  onDownloadExcel: () => void;
  onDownloadJson?: () => void;
  onDownloadXml?: () => void;
  exportUsesServerOrder?: boolean;
  csvHeaderHelp?: string;
  extraControls?: ReactNode;
  dedupControl?: ReactNode;
  t: (key: I18nKey) => string;
}>;

export function ResultTableToolbar({
  appearance = "prototype",
  rowCountLabel,
  columns,
  hiddenColumnIds,
  onHiddenChange,
  labelFor,
  streaming,
  pageSize,
  onPageSizeChange,
  pageIndex,
  pageCount,
  totalFilteredRows,
  totalLoadedRows,
  onFirstPage,
  onPrevPage,
  onNextPage,
  onLastPage,
  canPrevious,
  canNext,
  onDownloadCsv,
  onDownloadExcel,
  onDownloadJson,
  onDownloadXml,
  exportUsesServerOrder,
  csvHeaderHelp,
  extraControls,
  dedupControl,
  t,
}: ResultTableToolbarProps) {
  const proto = appearance === "prototype";
  const btnSm = proto ? "btn secondary sm" : "srse-btn srse-btn-sm";
  const btnPrimary = proto ? "btn sm" : "srse-btn srse-btn-primary srse-btn-sm";
  const rangeStart = totalFilteredRows === 0 ? 0 : pageIndex * pageSize + 1;
  const rangeEnd = Math.min(totalFilteredRows, (pageIndex + 1) * pageSize);
  const showFirstLast = pageCount > 1;

  return (
    <div className="result-table-toolbar">
      <div className="result-table-toolbar-top">
        <div className="result-table-toolbar-meta">{rowCountLabel}</div>
        <div className="result-table-toolbar-actions">
          {dedupControl}
          <ResultColumnSelector
            appearance={appearance}
            columns={columns}
            hiddenColumnIds={hiddenColumnIds}
            onHiddenChange={onHiddenChange}
            labelFor={labelFor}
            disabled={streaming}
          />
          <button
            type="button"
            className={btnPrimary}
            disabled={!!streaming}
            onClick={onDownloadCsv}
            title={csvHeaderHelp ?? "Download complete result as CSV (UTF-8, header row 1)"}
            aria-label="Download CSV"
          >
            CSV
          </button>
          <button
            type="button"
            className={btnPrimary}
            disabled={!!streaming}
            onClick={onDownloadExcel}
            aria-label="Download Excel"
          >
            Excel
          </button>
          {onDownloadJson && (
            <button type="button" className={btnSm} disabled={!!streaming} onClick={onDownloadJson} aria-label="Download JSON">
              JSON
            </button>
          )}
          {onDownloadXml && (
            <button type="button" className={btnSm} disabled={!!streaming} onClick={onDownloadXml} aria-label="Download XML">
              XML
            </button>
          )}
          {extraControls}
        </div>
      </div>
      {exportUsesServerOrder && (
        <p className="result-table-export-note">
          Downloads use the server query order (not the temporary sort applied on screen).
        </p>
      )}
      <div className="result-table-toolbar-bottom">
        <span className="result-table-range">
          {t("lblShowing")} {rangeStart}–{rangeEnd} {t("lblOf")} {totalFilteredRows}{" "}
          {t("lblTotalRows")}
          {totalLoadedRows !== totalFilteredRows ? ` (${totalLoadedRows} loaded)` : ""}
        </span>
        <div className="result-table-pagination">
          <label htmlFor="result-rows-per-page" className="result-table-page-size">
            <span>{t("lblRowsPerPage")}</span>
            <select
              id="result-rows-per-page"
              value={pageSize}
              onChange={(e) => onPageSizeChange(Number(e.target.value))}
            >
              {RESULT_PAGE_SIZE_OPTIONS.map((size) => (
                <option key={size} value={size}>
                  {size}
                </option>
              ))}
            </select>
          </label>
          {showFirstLast && (
            <button type="button" className={btnSm} onClick={onFirstPage} disabled={!canPrevious} aria-label="First page">
              «
            </button>
          )}
          <button type="button" className={btnSm} onClick={onPrevPage} disabled={!canPrevious} aria-label="Previous page">
            {t("btnPrev")}
          </button>
          <span className="result-table-page-label" aria-live="polite">
            {t("lblPage")} {pageCount === 0 ? 0 : pageIndex + 1} {t("lblOf")} {pageCount}
          </span>
          <button type="button" className={btnSm} onClick={onNextPage} disabled={!canNext} aria-label="Next page">
            {t("btnNext")}
          </button>
          {showFirstLast && (
            <button type="button" className={btnSm} onClick={onLastPage} disabled={!canNext} aria-label="Last page">
              »
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
