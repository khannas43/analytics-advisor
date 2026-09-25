"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import LakehouseCascade, {
  EMPTY_CASCADE,
  isCascadeComplete,
  type CascadeFetchers,
  type CascadeValue,
} from "@/components/LakehouseCascade";
import { AnalysisResultsGrid } from "@/components/AnalysisResultsGrid";
import {
  downloadRecordMatchCsv,
  fetchMatchSql,
  listAnalysisCatalogs,
  listAnalysisColumns,
  listAnalysisLayers,
  listAnalysisSchemas,
  listAnalysisTables,
  runRecordMatchStream,
  type DisplayColumn,
  type JoinType,
  type MatchCriterion,
  type PredicateSpecWire,
  type RecordMatchRequest,
  type RegisteredColumn,
  type RuleOperator,
  type TableRef,
} from "@/lib/analysisApi";

const REGISTRY_FETCHERS: CascadeFetchers = {
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

function parseRuleValue(raw: string): string | number {
  const trimmed = raw.trim();
  if (trimmed === "") {
    return trimmed;
  }
  const asNum = Number(trimmed);
  if (!Number.isNaN(asNum) && /^-?\d+(\.\d+)?$/.test(trimmed)) {
    return asNum;
  }
  return trimmed;
}

function buildRuleSpec(
  ref: TableRef,
  column: string,
  operator: RuleOperator,
  valueRaw: string,
): PredicateSpecWire | null {
  if (!column) {
    return null;
  }
  const opMeta = RULE_OPS.find((o) => o.value === operator);
  if (!opMeta) {
    return null;
  }
  const node: PredicateSpecWire["root"] = {
    type: "PREDICATE",
    column: {
      table: { catalog: ref.catalog, schema: ref.schema, table: ref.table },
      column,
    },
    operator,
  };
  if (opMeta.needsValue) {
    (node as { value?: unknown }).value = parseRuleValue(valueRaw);
  }
  return { root: node };
}

export default function ExtractPage() {
  const [mode, setMode] = useState<"single" | "join">("single");
  const [sourceRef, setSourceRef] = useState<CascadeValue>(EMPTY_CASCADE);
  const [targetRef, setTargetRef] = useState<CascadeValue>(EMPTY_CASCADE);
  const [sourceColumns, setSourceColumns] = useState<RegisteredColumn[]>([]);
  const [targetColumns, setTargetColumns] = useState<RegisteredColumn[]>([]);
  const [displayCols, setDisplayCols] = useState<string[]>([]);
  const [joinKeySource, setJoinKeySource] = useState("");
  const [joinKeyTarget, setJoinKeyTarget] = useState("");
  const [joinType, setJoinType] = useState<JoinType>("INNER");
  const [ruleColumn, setRuleColumn] = useState("");
  const [ruleOp, setRuleOp] = useState<RuleOperator>("GT");
  const [ruleValue, setRuleValue] = useState("");
  const [targetRuleColumn, setTargetRuleColumn] = useState("");
  const [targetRuleOp, setTargetRuleOp] = useState<RuleOperator>("GT");
  const [targetRuleValue, setTargetRuleValue] = useState("");

  const [sqlPreview, setSqlPreview] = useState<string | null>(null);
  const [sqlPreviewError, setSqlPreviewError] = useState<string | null>(null);
  const [sqlPreviewLoading, setSqlPreviewLoading] = useState(false);

  const [matchStatus, setMatchStatus] = useState<"idle" | "loading" | "ok" | "error">("idle");
  const [matchError, setMatchError] = useState<string | null>(null);
  const [matchColumns, setMatchColumns] = useState<string[]>([]);
  const [matchRows, setMatchRows] = useState<Record<string, unknown>[]>([]);
  const [matchSql, setMatchSql] = useState("");
  const [matchTotalRows, setMatchTotalRows] = useState<number | null>(null);
  const [matchTooManyToDisplay, setMatchTooManyToDisplay] = useState(false);
  const [matchCountIsPartial, setMatchCountIsPartial] = useState(false);
  const lastRunRequestRef = useRef<RecordMatchRequest | null>(null);
  const pendingRowsRef = useRef<Record<string, unknown>[]>([]);
  const flushIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const rowsSeenRef = useRef(0);

  const loadColumns = useCallback(async (ref: CascadeValue, which: "source" | "target") => {
    if (!isCascadeComplete(ref)) {
      if (which === "source") {
        setSourceColumns([]);
      } else {
        setTargetColumns([]);
      }
      return;
    }
    const cols = await listAnalysisColumns(ref);
    if (which === "source") {
      setSourceColumns(cols);
    } else {
      setTargetColumns(cols);
    }
  }, []);

  useEffect(() => {
    void loadColumns(sourceRef, "source");
  }, [sourceRef, loadColumns]);

  useEffect(() => {
    if (mode === "join") {
      void loadColumns(targetRef, "target");
    }
  }, [targetRef, mode, loadColumns]);

  function toggleDisplayColumn(name: string) {
    setDisplayCols((prev) =>
      prev.includes(name) ? prev.filter((c) => c !== name) : [...prev, name],
    );
  }

  function buildRequest(): RecordMatchRequest | null {
    if (!isCascadeComplete(sourceRef)) {
      return null;
    }
    const ref = sourceRef as TableRef;
    const displays: DisplayColumn[] = displayCols.map((column) => ({ ...ref, column }));
    if (displays.length === 0) {
      return null;
    }
    const sourceRules = buildRuleSpec(ref, ruleColumn, ruleOp, ruleValue);

    if (mode === "single") {
      return {
        sourceCriteria: [],
        targetCriteria: [],
        sourceDisplayColumns: displays,
        highlightDuplicates: false,
        dedup: null,
        sourceRules: sourceRules ?? undefined,
        singleSource: true,
      };
    }

    if (!isCascadeComplete(targetRef) || !joinKeySource || !joinKeyTarget) {
      return null;
    }
    const tgt = targetRef as TableRef;
    const sourceCriteria: MatchCriterion[] = [
      { ...ref, column: joinKeySource, fuzzyThresholdPercent: null },
    ];
    const targetCriteria: MatchCriterion[] = [
      { ...tgt, column: joinKeyTarget, fuzzyThresholdPercent: null },
    ];
    const targetRules = buildRuleSpec(tgt, targetRuleColumn, targetRuleOp, targetRuleValue);
    const req: RecordMatchRequest = {
      sourceCriteria,
      targetCriteria,
      sourceDisplayColumns: displays,
      highlightDuplicates: false,
      dedup: null,
      sourceRules: sourceRules ?? undefined,
      targetRules: targetRules ?? undefined,
      singleSource: false,
    };
    if (joinType !== "INNER") {
      req.joinType = joinType;
    }
    return req;
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
            setMatchColumns(columns);
            setMatchSql(sql);
          },
          onRow: (row) => {
            rowsSeenRef.current += 1;
            if (rowsSeenRef.current <= MAX_ROWS_TO_PARSE) {
              pendingRowsRef.current.push(row);
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

  async function downloadFullCsv() {
    const req = lastRunRequestRef.current;
    if (!req) {
      return;
    }
    const blob = await downloadRecordMatchCsv(req);
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "extract.csv";
    a.click();
    URL.revokeObjectURL(url);
  }

  const ruleNeedsValue = RULE_OPS.find((o) => o.value === ruleOp)?.needsValue ?? true;
  const targetRuleNeedsValue = RULE_OPS.find((o) => o.value === targetRuleOp)?.needsValue ?? true;

  return (
    <main className="srse-page" style={{ maxWidth: 1100, margin: "0 auto", padding: "1.5rem 1rem 3rem" }}>
      <h1 style={{ marginTop: 0 }}>Extract records</h1>
      <p className="srse-text-muted" style={{ marginBottom: "1.25rem" }}>
        Pick one table to extract rows, or two tables to join — scope and filter rules are applied inside each
        side before the join.
      </p>

      <div className="srse-card" style={{ marginBottom: "1rem", display: "flex", gap: "1rem", flexWrap: "wrap" }}>
        <label>
          <input
            type="radio"
            name="mode"
            checked={mode === "single"}
            onChange={() => setMode("single")}
          />{" "}
          Single table
        </label>
        <label>
          <input
            type="radio"
            name="mode"
            checked={mode === "join"}
            onChange={() => setMode("join")}
          />{" "}
          Two tables (join)
        </label>
      </div>

      <section className="srse-card" style={{ marginBottom: "1rem" }}>
        <h2 style={{ marginTop: 0, fontSize: "1.05rem" }}>{mode === "single" ? "Table" : "Source table"}</h2>
        <LakehouseCascade idPrefix="extract-source" value={sourceRef} onChange={setSourceRef} fetchers={REGISTRY_FETCHERS} />
      </section>

      {mode === "join" && (
        <section className="srse-card" style={{ marginBottom: "1rem" }}>
          <h2 style={{ marginTop: 0, fontSize: "1.05rem" }}>Target table</h2>
          <LakehouseCascade idPrefix="extract-target" value={targetRef} onChange={setTargetRef} fetchers={REGISTRY_FETCHERS} />
        </section>
      )}

      {isCascadeComplete(sourceRef) && (
        <section className="srse-card" style={{ marginBottom: "1rem" }}>
          <h2 style={{ marginTop: 0, fontSize: "1.05rem" }}>Output columns</h2>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem 1rem" }}>
            {sourceColumns.map((c) => (
              <label key={c.name} style={{ fontSize: "0.9rem" }}>
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

      {mode === "join" && isCascadeComplete(sourceRef) && isCascadeComplete(targetRef) && (
        <section className="srse-card" style={{ marginBottom: "1rem" }}>
          <h2 style={{ marginTop: 0, fontSize: "1.05rem" }}>Join key</h2>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem" }}>
            <label>
              Source column
              <select className="srse-select" style={{ width: "100%" }} value={joinKeySource} onChange={(e) => setJoinKeySource(e.target.value)}>
                <option value="">— select —</option>
                {sourceColumns.map((c) => (
                  <option key={c.name} value={c.name}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Target column
              <select className="srse-select" style={{ width: "100%" }} value={joinKeyTarget} onChange={(e) => setJoinKeyTarget(e.target.value)}>
                <option value="">— select —</option>
                {targetColumns.map((c) => (
                  <option key={c.name} value={c.name}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <label style={{ display: "block", marginTop: "0.75rem" }}>
            Join type
            <select className="srse-select" value={joinType} onChange={(e) => setJoinType(e.target.value as JoinType)}>
              {JOIN_TYPE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
        </section>
      )}

      <section className="srse-card" style={{ marginBottom: "1rem" }}>
        <h2 style={{ marginTop: 0, fontSize: "1.05rem" }}>Source filter rule</h2>
        <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr", gap: "0.75rem", alignItems: "end" }}>
          <label>
            Column
            <select className="srse-select" style={{ width: "100%" }} value={ruleColumn} onChange={(e) => setRuleColumn(e.target.value)}>
              <option value="">— none —</option>
              {sourceColumns.map((c) => (
                <option key={c.name} value={c.name}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Operator
            <select className="srse-select" style={{ width: "100%" }} value={ruleOp} onChange={(e) => setRuleOp(e.target.value as RuleOperator)}>
              {RULE_OPS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          {ruleNeedsValue && (
            <label>
              Value
              <input className="srse-input" style={{ width: "100%" }} value={ruleValue} onChange={(e) => setRuleValue(e.target.value)} />
            </label>
          )}
        </div>
      </section>

      {mode === "join" && (
        <section className="srse-card" style={{ marginBottom: "1rem" }}>
          <h2 style={{ marginTop: 0, fontSize: "1.05rem" }}>Target filter rule</h2>
          <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr", gap: "0.75rem", alignItems: "end" }}>
            <label>
              Column
              <select
                className="srse-select"
                style={{ width: "100%" }}
                value={targetRuleColumn}
                onChange={(e) => setTargetRuleColumn(e.target.value)}
              >
                <option value="">— none —</option>
                {targetColumns.map((c) => (
                  <option key={c.name} value={c.name}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Operator
              <select
                className="srse-select"
                style={{ width: "100%" }}
                value={targetRuleOp}
                onChange={(e) => setTargetRuleOp(e.target.value as RuleOperator)}
              >
                {RULE_OPS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </label>
            {targetRuleNeedsValue && (
              <label>
                Value
                <input
                  className="srse-input"
                  style={{ width: "100%" }}
                  value={targetRuleValue}
                  onChange={(e) => setTargetRuleValue(e.target.value)}
                />
              </label>
            )}
          </div>
        </section>
      )}

      <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.6rem", flexWrap: "wrap" }}>
        <button type="button" className="srse-btn srse-btn-ghost" disabled={sqlPreviewLoading || matchStatus === "loading"} onClick={() => void previewSql()}>
          {sqlPreviewLoading ? "Planning…" : "Preview SQL"}
        </button>
        <button type="button" className="srse-btn srse-btn-primary" disabled={matchStatus === "loading"} onClick={() => void runExtract()}>
          {matchStatus === "loading" ? "Running…" : "Run extract"}
        </button>
      </div>

      {sqlPreviewError && <p className="srse-text-danger">{sqlPreviewError}</p>}
      {sqlPreview && (
        <pre className="srse-card" style={{ marginTop: "0.75rem", fontSize: "0.75rem", whiteSpace: "pre-wrap" }}>
          {sqlPreview}
        </pre>
      )}
      {matchError && <p className="srse-text-danger">{matchError}</p>}

      {matchColumns.length > 0 && (
        <div style={{ marginTop: "1.5rem" }}>
          <AnalysisResultsGrid
            columns={matchColumns}
            rows={matchRows}
            sql={matchSql}
            streaming={matchStatus === "loading"}
            totalRows={matchTotalRows}
            totalRowsIsPartial={matchCountIsPartial}
            tooManyToDisplay={matchTooManyToDisplay}
            displayLimit={MAX_DISPLAYED_ROWS}
            onDownloadFullCsv={downloadFullCsv}
            highlightDuplicates={false}
            dedupAvailable={false}
            dedupEnabled={false}
            onDedupToggle={() => {}}
          />
        </div>
      )}
    </main>
  );
}
