"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import LakehouseCascade, {
  EMPTY_CASCADE,
  isCascadeComplete,
  type CascadeFetchers,
  type CascadeValue,
} from "@/components/LakehouseCascade";
import { AnalysisResultsGrid } from "@/components/AnalysisResultsGrid";
import ValueFilterPicker from "@/components/ValueFilterPicker";
import {
  downloadRecordMatchExport,
  type MatchExportFormat,
  fetchMatchSql,
  listAnalysisCatalogs,
  listAnalysisColumns,
  listAnalysisLayers,
  listAnalysisSourceSystems,
  listAnalysisTableGroups,
  listAnalysisSchemas,
  listAnalysisTables,
  runRecordMatchStream,
  type JoinType,
  type PredicateSpecWire,
  type RecordMatchRequest,
  type RegisteredColumn,
  type RuleOperator,
  type TableRef,
} from "@/lib/analysisApi";
import { useQueryResultsStore } from "@/lib/queryResultsStore";
import { useShell } from "@/components/shell/ShellProviders";
import { buildExtractRequest } from "@/lib/queryBuilderRequestBuild";
import { useQueryBuilderPipeline } from "@/lib/queryBuilderPipelineStore";

const REGISTRY_FETCHERS: CascadeFetchers = {
  listSourceSystems: listAnalysisSourceSystems,
  listTableGroups: listAnalysisTableGroups,
  listLayers: listAnalysisLayers,
  listCatalogs: listAnalysisCatalogs,
  listSchemas: listAnalysisSchemas,
  listTables: listAnalysisTables,
};

const JOIN_TYPE_OPTIONS: { value: JoinType; label: string }[] = [
  { value: "INNER", label: "Only matching records" },
  { value: "LEFT", label: "All source records" },
  { value: "RIGHT", label: "All target records" },
  { value: "FULL", label: "All records from both" },
];

const RULE_OPS: { value: RuleOperator; label: string; needsValue: boolean }[] = [
  { value: "EQ", label: "=", needsValue: true },
  { value: "NE", label: "≠", needsValue: true },
  { value: "LT", label: "<", needsValue: true },
  { value: "LTE", label: "≤", needsValue: true },
  { value: "GT", label: ">", needsValue: true },
  { value: "GTE", label: "≥", needsValue: true },
  { value: "IS_NULL", label: "is null", needsValue: false },
  { value: "NOT_NULL", label: "is not null", needsValue: false },
];

const MAX_DISPLAYED_ROWS = 10000;
const MAX_ROWS_TO_PARSE = 200000;

type ExtractRecordsTabProps = {
  onGoReport?: () => void;
};

export default function ExtractRecordsTab({ onGoReport }: Readonly<ExtractRecordsTabProps>) {
  const dualMode = useQueryBuilderPipeline((s) => s.dualMode);
  const extract = useQueryBuilderPipeline((s) => s.extract);
  const patchExtract = useQueryBuilderPipeline((s) => s.patchExtract);
  const commitExtractSuccess = useQueryBuilderPipeline((s) => s.commitExtractSuccess);
  const mode: "single" | "join" = dualMode ? "join" : "single";

  const {
    sourceRef,
    targetRef,
    displayCols,
    joinKeySource,
    joinKeyTarget,
    joinType,
    ruleColumn,
    ruleOp,
    ruleValue,
    useValuePicker,
    valuePickerSpec,
    fuzzyRuleEnabled,
    fuzzyRuleName,
    fuzzyRuleThreshold,
    fuzzyIgnoreSpaces,
    fuzzyCaseSensitive,
    targetRuleColumn,
    targetRuleOp,
    targetRuleValue,
  } = extract;

  const setSourceRef = (v: CascadeValue) => patchExtract({ sourceRef: v });
  const setTargetRef = (v: CascadeValue) => patchExtract({ targetRef: v });
  const setJoinKeySource = (v: string) => patchExtract({ joinKeySource: v });
  const setJoinKeyTarget = (v: string) => patchExtract({ joinKeyTarget: v });
  const setJoinType = (v: JoinType) => patchExtract({ joinType: v });
  const setRuleColumn = (v: string) => patchExtract({ ruleColumn: v });
  const setRuleOp = (v: RuleOperator) => patchExtract({ ruleOp: v });
  const setRuleValue = (v: string) => patchExtract({ ruleValue: v });
  const setUseValuePicker = (v: boolean) => patchExtract({ useValuePicker: v });
  const setValuePickerSpec = useCallback(
    (v: PredicateSpecWire | null) => patchExtract({ valuePickerSpec: v }),
    [patchExtract],
  );
  const setFuzzyRuleEnabled = (v: boolean) => patchExtract({ fuzzyRuleEnabled: v });
  const setFuzzyRuleName = (v: string) => patchExtract({ fuzzyRuleName: v });
  const setFuzzyRuleThreshold = (v: number) => patchExtract({ fuzzyRuleThreshold: v });
  const setFuzzyIgnoreSpaces = (v: boolean) => patchExtract({ fuzzyIgnoreSpaces: v });
  const setFuzzyCaseSensitive = (v: boolean) => patchExtract({ fuzzyCaseSensitive: v });
  const setTargetRuleColumn = (v: string) => patchExtract({ targetRuleColumn: v });
  const setTargetRuleOp = (v: RuleOperator) => patchExtract({ targetRuleOp: v });
  const setTargetRuleValue = (v: string) => patchExtract({ targetRuleValue: v });

  const [sourceColumns, setSourceColumns] = useState<RegisteredColumn[]>([]);
  const [targetColumns, setTargetColumns] = useState<RegisteredColumn[]>([]);

  const [sqlPreview, setSqlPreview] = useState<string | null>(null);
  const [sqlPreviewError, setSqlPreviewError] = useState<string | null>(null);
  const [sqlPreviewLoading, setSqlPreviewLoading] = useState(false);

  const [matchStatus, setMatchStatus] = useState<"idle" | "loading" | "ok" | "error">("idle");
  const [matchError, setMatchError] = useState<string | null>(null);
  const [matchColumns, setMatchColumns] = useState<string[]>([]);
  const [matchRows, setMatchRows] = useState<Record<string, unknown>[]>([]);

  /**
   * Totals a SUM or AVG could not include, surfaced above the grid rather than
   * left as another column to scroll past.
   *
   * The backend emits `<alias>_unparseable` beside any aggregate it had to cast,
   * counting non-null values that would not parse as numbers. Zero stays silent —
   * there is nothing to warn about, and a permanent banner is one people learn to
   * ignore. A wrong total that looks right is the thing this exists to prevent.
   */
  const unparseableTotals = useMemo(() => {
    const suffix = "_unparseable";
    return matchColumns
      .filter((c) => c.endsWith(suffix))
      .map((column) => ({
        column,
        forColumn: column.slice(0, -suffix.length),
        total: matchRows.reduce((sum, row) => {
          const raw = row[column];
          const n = typeof raw === "number" ? raw : Number(raw ?? 0);
          return sum + (Number.isFinite(n) ? n : 0);
        }, 0),
      }))
      .filter((u) => u.total > 0);
  }, [matchColumns, matchRows]);
  const [matchSql, setMatchSql] = useState("");
  const [matchTotalRows, setMatchTotalRows] = useState<number | null>(null);
  const [matchTooManyToDisplay, setMatchTooManyToDisplay] = useState(false);
  const [matchCountIsPartial, setMatchCountIsPartial] = useState(false);
  const lastRunRequestRef = useRef<RecordMatchRequest | null>(null);
  const pendingRowsRef = useRef<Record<string, unknown>[]>([]);
  const flushIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const rowsSeenRef = useRef(0);
  const streamColumnsRef = useRef<string[]>([]);
  const carriedRowsRef = useRef<Record<string, unknown>[]>([]);
  const lastSqlRef = useRef("");
  const setLastResult = useQueryResultsStore((s) => s.setLastResult);

  function publishExtractResult(
    columns: string[],
    rows: Record<string, unknown>[],
    totalRows: number | null,
  ) {
    if (columns.length === 0 || rows.length === 0) {
      return;
    }
    setLastResult({
      columns,
      rows,
      totalRows,
      source: "extract",
      matchExportRequest: lastRunRequestRef.current,
    });
  }

  const sourceRefKey = `${sourceRef.catalog}.${sourceRef.schema}.${sourceRef.table}`;
  const targetRefKey = `${targetRef.catalog}.${targetRef.schema}.${targetRef.table}`;

  useEffect(() => {
    if (!isCascadeComplete(sourceRef)) {
      return undefined;
    }
    let cancelled = false;
    listAnalysisColumns(sourceRef).then((cols) => {
      if (!cancelled) setSourceColumns(cols);
    });
    return () => {
      cancelled = true;
    };
  }, [sourceRefKey, sourceRef]);

  useEffect(() => {
    if (mode !== "join" || !isCascadeComplete(targetRef)) {
      return undefined;
    }
    let cancelled = false;
    listAnalysisColumns(targetRef).then((cols) => {
      if (!cancelled) setTargetColumns(cols);
    });
    return () => {
      cancelled = true;
    };
  }, [mode, targetRefKey, targetRef]);

  const sourceColumnsDisplay = isCascadeComplete(sourceRef) ? sourceColumns : [];

  const valuePickerTable = useMemo(
    (): TableRef => ({
      catalog: sourceRef.catalog,
      schema: sourceRef.schema,
      table: sourceRef.table,
    }),
    [sourceRef.catalog, sourceRef.schema, sourceRef.table],
  );
  const targetColumnsDisplay = mode === "join" && isCascadeComplete(targetRef) ? targetColumns : [];

  function toggleDisplayColumn(name: string) {
    const next = displayCols.includes(name)
      ? displayCols.filter((c) => c !== name)
      : [...displayCols, name];
    patchExtract({ displayCols: next });
  }

  function buildRequest(): RecordMatchRequest | null {
    return buildExtractRequest(dualMode, extract);
  }

  function commitSuccess(req: RecordMatchRequest, totalRows: number | null, sql: string) {
    commitExtractSuccess(req, {
      columns: streamColumnsRef.current,
      rows: carriedRowsRef.current,
      totalRows,
      sql,
    });
  }

  function flushPendingRows() {
    const batch = pendingRowsRef.current;
    if (batch.length === 0) {
      return;
    }
    pendingRowsRef.current = [];
    setMatchRows((prev) => (rowsSeenRef.current > MAX_DISPLAYED_ROWS ? [] : [...prev, ...batch]));
  }

  async function runExtract() {
    const req = buildRequest();
    if (!req) {
      setMatchError("Pick a table, at least one output column, and (for two-table mode) a join key.");
      return;
    }
    if (flushIntervalRef.current) {
      clearInterval(flushIntervalRef.current);
    }
    rowsSeenRef.current = 0;
    pendingRowsRef.current = [];
    streamColumnsRef.current = [];
    carriedRowsRef.current = [];
    setMatchStatus("loading");
    setMatchError(null);
    setMatchColumns([]);
    setMatchRows([]);
    setMatchSql("");
    setMatchTotalRows(null);
    setMatchTooManyToDisplay(false);
    setMatchCountIsPartial(false);
    lastRunRequestRef.current = req;

    flushIntervalRef.current = setInterval(flushPendingRows, 200);
    const controller = new AbortController();

    try {
      await runRecordMatchStream(
        req,
        {
          onMeta: ({ columns, sql }) => {
            streamColumnsRef.current = columns;
            setMatchColumns(columns);
            setMatchSql(sql);
            lastSqlRef.current = sql;
          },
          onRow: (row) => {
            rowsSeenRef.current += 1;
            if (rowsSeenRef.current <= MAX_ROWS_TO_PARSE) {
              pendingRowsRef.current.push(row);
            }
            if (carriedRowsRef.current.length < MAX_DISPLAYED_ROWS) {
              carriedRowsRef.current.push(row);
            }
            if (rowsSeenRef.current === MAX_ROWS_TO_PARSE + 1) {
              setMatchCountIsPartial(true);
              controller.abort();
            }
            if (rowsSeenRef.current === MAX_DISPLAYED_ROWS + 1) {
              setMatchTooManyToDisplay(true);
              pendingRowsRef.current = [];
              setMatchRows([]);
            }
          },
          onDone: (totalRows) => {
            flushPendingRows();
            setMatchTotalRows(totalRows);
            setMatchStatus("ok");
            publishExtractResult(streamColumnsRef.current, carriedRowsRef.current, totalRows);
            commitSuccess(req, totalRows, lastSqlRef.current);
          },
          onError: (message) => {
            setMatchError(message);
            setMatchStatus("error");
          },
        },
        controller.signal,
      );
      if (matchStatus !== "error") {
        flushPendingRows();
        setMatchStatus((s) => (s === "loading" ? "ok" : s));
      }
    } catch (err: unknown) {
      if (controller.signal.aborted && rowsSeenRef.current > MAX_ROWS_TO_PARSE) {
        flushPendingRows();
        setMatchStatus("ok");
        publishExtractResult(
          streamColumnsRef.current,
          carriedRowsRef.current,
          rowsSeenRef.current,
        );
        commitSuccess(req, rowsSeenRef.current, lastSqlRef.current);
      } else {
        setMatchError(err instanceof Error ? err.message : String(err));
        setMatchStatus("error");
      }
    } finally {
      if (flushIntervalRef.current) {
        clearInterval(flushIntervalRef.current);
        flushIntervalRef.current = null;
      }
    }
  }

  async function previewSql() {
    const req = buildRequest();
    if (!req) {
      setSqlPreviewError("Complete the table, columns, and join key (if two-table) first.");
      return;
    }
    setSqlPreviewLoading(true);
    setSqlPreviewError(null);
    try {
      setSqlPreview(await fetchMatchSql(req));
    } catch (err: unknown) {
      setSqlPreview(null);
      setSqlPreviewError(err instanceof Error ? err.message : String(err));
    } finally {
      setSqlPreviewLoading(false);
    }
  }

  async function downloadFullExport(format: MatchExportFormat) {
    const req = lastRunRequestRef.current;
    if (!req) {
      throw new Error("Run a query first.");
    }
    const ext = format === "xlsx" ? "xlsx" : format;
    const blob = await downloadRecordMatchExport(req, format);
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `extract-${new Date().toISOString().slice(0, 19).replaceAll(/[:T]/g, "-")}.${ext}`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }

  const ruleNeedsValue = RULE_OPS.find((o) => o.value === ruleOp)?.needsValue ?? true;
  const targetRuleNeedsValue = RULE_OPS.find((o) => o.value === targetRuleOp)?.needsValue ?? true;
  const { t, modeKey } = useShell();
  const dual = mode === "join";
  const previewText =
    sqlPreview ??
    (matchSql ||
      (isCascadeComplete(sourceRef)
        ? "Ready to plan SQL — use Preview or Run Query."
        : "Select attributes and tables to see the query preview."));

  return (
    <div className="query-builder-embed" data-mode={dual ? "dual" : "single"}>
      <section className="section">
        <h4>
          <span className="step-num">1</span>
          {modeKey("secChooseTables", "secChooseTablesSingle", dual)}
        </h4>
        <p className="desc">{t("secChooseTablesDesc")}</p>
        <div className="two-col">
          <div className="pick">
            <div className="ttl">{t("lblSourceTable")}</div>
            <LakehouseCascade
              idPrefix="extract-source"
              value={sourceRef}
              onChange={setSourceRef}
              fetchers={REGISTRY_FETCHERS}
              appearance="prototype"
            />
          </div>
          <div className="pick dest-only">
            <div className="ttl">{t("lblDestTable")}</div>
            <LakehouseCascade
              idPrefix="extract-target"
              value={targetRef}
              onChange={setTargetRef}
              fetchers={REGISTRY_FETCHERS}
              appearance="prototype"
            />
          </div>
        </div>
      </section>

      {isCascadeComplete(sourceRef) && (
        <section className="section">
          <h4>
            <span className="step-num">2</span>
            {modeKey("secSelectAttrs", "secSelectAttrsSingle", dual)}
          </h4>
          <p className="desc">{t("secSelectAttrsDesc")}</p>
          <div className="attr-grid">
            {sourceColumnsDisplay.map((c) => (
              <label key={c.name}>
                <input
                  type="checkbox"
                  checked={displayCols.includes(c.name)}
                  onChange={() => toggleDisplayColumn(c.name)}
                />{" "}
                {c.businessName ?? c.name}
              </label>
            ))}
          </div>
        </section>
      )}

      {dual && isCascadeComplete(sourceRef) && isCascadeComplete(targetRef) && (
        <section className="section">
          <h4>
            <span className="step-num">3</span>
            {modeKey("secPrimaryKeys", "secPrimaryKeysSingle", dual)}
          </h4>
          <p className="desc">{modeKey("secPrimaryKeysDesc", "secPrimaryKeysDescSingle", dual)}</p>
          <div className="two-col">
            <div className="field">
              <label>{t("lblSourcePK")}</label>
              <select
                aria-label={t("lblSourcePK")}
                value={joinKeySource}
                onChange={(e) => setJoinKeySource(e.target.value)}
              >
                <option value="">{t("selPKPlaceholder")}</option>
                {sourceColumnsDisplay.map((c) => (
                  <option key={c.name} value={c.name}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="field dest-only">
              <label>{t("lblDestPK")}</label>
              <select
                aria-label={t("lblDestPK")}
                value={joinKeyTarget}
                onChange={(e) => setJoinKeyTarget(e.target.value)}
              >
                <option value="">{t("selPKPlaceholder")}</option>
                {targetColumnsDisplay.map((c) => (
                  <option key={c.name} value={c.name}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="field" style={{ marginTop: 12, maxWidth: 360 }}>
            <label>{t("qbJoinType")}</label>
            <select
              aria-label={t("qbJoinType")}
              value={joinType}
              onChange={(e) => setJoinType(e.target.value as JoinType)}
            >
              {JOIN_TYPE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
        </section>
      )}

      <section className="section">
        <h4>
          <span className="step-num">4</span>
          {modeKey("secRules", "secRulesSingle", dual)}
        </h4>
        <p className="desc">{t("secRulesDesc")}</p>
        <div className="two-col">
          <div className="pick">
            <div className="ttl">{t("lblRuleSource")}</div>
            <label className="checkbox-row">
              <input
                type="checkbox"
                aria-label="Pick values from list"
                checked={useValuePicker}
                onChange={(e) => {
                  setUseValuePicker(e.target.checked);
                  if (e.target.checked) setFuzzyRuleEnabled(false);
                }}
              />
              {t("qbValuePickerIn")}
            </label>
            <label className="checkbox-row">
              <input
                type="checkbox"
                aria-label="Typed-text fuzzy match"
                checked={fuzzyRuleEnabled}
                onChange={(e) => {
                  setFuzzyRuleEnabled(e.target.checked);
                  if (e.target.checked) setUseValuePicker(false);
                }}
              />
              {t("qbFuzzyRuleScoped")}
            </label>
            {useValuePicker && isCascadeComplete(sourceRef) && (
              <div className="field" style={{ marginTop: 8, maxWidth: 360 }}>
                <label>{t("lblColumn")}</label>
                <select
                  aria-label="Source rule column"
                  value={ruleColumn}
                  onChange={(e) => setRuleColumn(e.target.value)}
                >
                  <option value="">{t("selNone")}</option>
                  {sourceColumnsDisplay.map((c) => (
                    <option key={c.name} value={c.name}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </div>
            )}
            {useValuePicker && isCascadeComplete(sourceRef) && ruleColumn && (
              <ValueFilterPicker
                table={valuePickerTable}
                column={ruleColumn}
                hydratedSpec={valuePickerSpec}
                onChange={setValuePickerSpec}
              />
            )}
            {fuzzyRuleEnabled && isCascadeComplete(sourceRef) && (
              <div className="field" style={{ marginTop: 8, maxWidth: 360 }}>
                <label>{t("lblColumn")}</label>
                <select
                  aria-label="Source rule column"
                  value={ruleColumn}
                  onChange={(e) => setRuleColumn(e.target.value)}
                >
                  <option value="">{t("selNone")}</option>
                  {sourceColumnsDisplay.map((c) => (
                    <option key={c.name} value={c.name}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </div>
            )}
            {fuzzyRuleEnabled && (
              <div className="row" style={{ flexWrap: "wrap", gap: 12 }}>
                <div className="field" style={{ flex: "2 1 160px" }}>
                  <label>{t("qbFuzzyName")}</label>
                  <input
                    aria-label="Name to match"
                    value={fuzzyRuleName}
                    onChange={(e) => setFuzzyRuleName(e.target.value)}
                  />
                </div>
                <div className="field" style={{ flex: "1 1 100px" }}>
                  <label>{t("qbFuzzyThreshold")}</label>
                  <input
                    aria-label="Threshold %"
                    type="number"
                    min={0}
                    max={100}
                    value={fuzzyRuleThreshold}
                    onChange={(e) => setFuzzyRuleThreshold(Number(e.target.value))}
                  />
                </div>
                <div className="field" style={{ flex: "1 1 140px" }}>
                  <label className="checkbox-row">
                    <input
                      type="checkbox"
                      aria-label="Ignore spaces"
                      checked={fuzzyIgnoreSpaces}
                      onChange={(e) => setFuzzyIgnoreSpaces(e.target.checked)}
                    />
                    {t("qbFuzzyIgnoreSpaces")}
                  </label>
                  <label className="checkbox-row">
                    <input
                      type="checkbox"
                      aria-label="Case sensitive"
                      checked={fuzzyCaseSensitive}
                      onChange={(e) => setFuzzyCaseSensitive(e.target.checked)}
                    />
                    {t("qbFuzzyCaseSensitive")}
                  </label>
                </div>
              </div>
            )}
            {!useValuePicker && !fuzzyRuleEnabled && (
              <div className="row" style={{ flexWrap: "wrap", gap: 12, alignItems: "flex-end" }}>
                <div className="field" style={{ flex: "2 1 160px" }}>
                  <label>{t("lblColumn")}</label>
                  <select
                    aria-label="Source rule column"
                    value={ruleColumn}
                    onChange={(e) => setRuleColumn(e.target.value)}
                  >
                    <option value="">{t("selNone")}</option>
                    {sourceColumnsDisplay.map((c) => (
                      <option key={c.name} value={c.name}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="field" style={{ flex: "1 1 120px" }}>
                  <label>{t("lblOperator")}</label>
                  <select
                    aria-label="Source rule operator"
                    value={ruleOp}
                    onChange={(e) => setRuleOp(e.target.value as RuleOperator)}
                  >
                    {RULE_OPS.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                </div>
                {ruleNeedsValue && (
                  <div className="field" style={{ flex: "1 1 120px" }}>
                    <label>{t("lblValue")}</label>
                    <input
                      aria-label="Source rule value"
                      value={ruleValue}
                      onChange={(e) => setRuleValue(e.target.value)}
                    />
                  </div>
                )}
              </div>
            )}
          </div>
          <div className="pick dest-only">
            <div className="ttl">{t("lblRuleDest")}</div>
            {dual ? (
              <div className="row" style={{ flexWrap: "wrap", gap: 12, alignItems: "flex-end" }}>
                <div className="field" style={{ flex: "2 1 160px" }}>
                  <label>{t("lblColumn")}</label>
                  <select
                    aria-label="Target rule column"
                    value={targetRuleColumn}
                    onChange={(e) => setTargetRuleColumn(e.target.value)}
                  >
                    <option value="">{t("selNone")}</option>
                    {targetColumnsDisplay.map((c) => (
                      <option key={c.name} value={c.name}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="field" style={{ flex: "1 1 120px" }}>
                  <label>{t("lblOperator")}</label>
                  <select value={targetRuleOp} onChange={(e) => setTargetRuleOp(e.target.value as RuleOperator)}>
                    {RULE_OPS.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                </div>
                {targetRuleNeedsValue && (
                  <div className="field" style={{ flex: "1 1 120px" }}>
                    <label>{t("lblValue")}</label>
                    <input value={targetRuleValue} onChange={(e) => setTargetRuleValue(e.target.value)} />
                  </div>
                )}
              </div>
            ) : (
              <p className="text-muted" style={{ margin: 0 }}>
                {t("secRulesDescSingle")}
              </p>
            )}
          </div>
        </div>
      </section>

      <section className="section">
        <div className="row" style={{ justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: 12 }}>
          <div style={{ flex: "1 1 280px" }}>
            <h4 style={{ margin: "0 0 8px" }}>{t("lblQueryPreview")}</h4>
            <div className="query-box">{previewText}</div>
          </div>
          <div className="row" style={{ gap: 8, alignSelf: "flex-end" }}>
            <button
              type="button"
              className="btn secondary"
              disabled={sqlPreviewLoading || matchStatus === "loading"}
              onClick={() => void previewSql()}
            >
              {sqlPreviewLoading ? t("btnPlanning") : t("btnPreviewSql")}
            </button>
            <button type="button" className="btn" disabled={matchStatus === "loading"} onClick={() => void runExtract()}>
              {matchStatus === "loading" ? t("btnRunning") : t("btnRunQuery")}
            </button>
            {onGoReport && matchStatus === "ok" && matchColumns.length > 0 && (
              <button
                type="button"
                className="btn secondary"
                aria-label="Go to Report Analysis"
                onClick={onGoReport}
              >
                {t("btnGoReport")}
              </button>
            )}
          </div>
        </div>
        {sqlPreviewError && <p className="text-danger">{sqlPreviewError}</p>}
        {matchError && <p className="text-danger">{matchError}</p>}
      </section>

      {unparseableTotals.length > 0 && (
        <div className="alert-warn" role="alert">
          <strong>Some values could not be added to these totals.</strong>
          <p style={{ margin: "0.4rem 0 0.2rem" }}>
            The column holds text, so each value was converted to a number before adding.
            Values that are not numbers were skipped — the totals below are of everything else.
          </p>
          <ul style={{ margin: "0.3rem 0 0", paddingLeft: "1.1rem" }}>
            {unparseableTotals.map((u) => (
              <li key={u.column}>
                <code>{u.forColumn}</code> — {u.total.toLocaleString()} value
                {u.total === 1 ? "" : "s"} skipped
              </li>
            ))}
          </ul>
        </div>
      )}

      {matchColumns.length > 0 && (
        <div className="section" style={{ marginTop: "1.5rem" }}>
          <AnalysisResultsGrid
            appearance="prototype"
            columns={matchColumns}
            rows={matchRows}
            sql={matchSql}
            streaming={matchStatus === "loading"}
            totalRows={matchTotalRows}
            totalRowsIsPartial={matchCountIsPartial}
            tooManyToDisplay={matchTooManyToDisplay}
            displayLimit={MAX_DISPLAYED_ROWS}
            onDownloadFullCsv={() => downloadFullExport("csv")}
            onDownloadFullExport={downloadFullExport}
            highlightDuplicates={false}
            dedupAvailable={false}
            dedupEnabled={false}
            onDedupToggle={() => {}}
          />
        </div>
      )}
    </div>
  );
}
