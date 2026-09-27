import { isCascadeComplete, type CascadeValue } from "@/components/LakehouseCascade";
import type {
  ComparisonGroup,
  DisplayColumn,
  MatchCriterion,
  PredicateSpecWire,
  RecordMatchRequest,
  RuleOperator,
  TableRef,
} from "@/lib/analysisApi";
import type { ExtractConfig, ReportConfig } from "@/lib/queryBuilderPipelineTypes";

const RULE_OPS: { value: RuleOperator; needsValue: boolean }[] = [
  { value: "EQ", needsValue: true },
  { value: "NE", needsValue: true },
  { value: "LT", needsValue: true },
  { value: "LTE", needsValue: true },
  { value: "GT", needsValue: true },
  { value: "GTE", needsValue: true },
  { value: "IS_NULL", needsValue: false },
  { value: "NOT_NULL", needsValue: false },
];

function parseRuleValue(raw: string): string | number {
  const trimmed = raw.trim();
  if (trimmed === "") return trimmed;
  const asNum = Number(trimmed);
  if (!Number.isNaN(asNum) && /^-?\d+(\.\d+)?$/.test(trimmed)) return asNum;
  return trimmed;
}

function buildRuleSpec(
  ref: TableRef,
  column: string,
  operator: RuleOperator,
  valueRaw: string,
) {
  if (!column) return null;
  const opMeta = RULE_OPS.find((o) => o.value === operator);
  if (!opMeta) return null;
  const node: {
    type: "PREDICATE";
    column: { table: { catalog: string; schema: string; table: string }; column: string };
    operator: RuleOperator;
    value?: unknown;
  } = {
    type: "PREDICATE",
    column: {
      table: { catalog: ref.catalog, schema: ref.schema, table: ref.table },
      column,
    },
    operator,
  };
  if (opMeta.needsValue) {
    node.value = parseRuleValue(valueRaw);
  }
  return { root: node };
}

function refAsTable(ref: CascadeValue): TableRef | null {
  return isCascadeComplete(ref) ? (ref as TableRef) : null;
}

function isNameColumn(column: string): boolean {
  return column.toLowerCase().includes("name");
}

function pairIsFuzzy(sourceCol: string, targetCol: string, threshold: number): boolean {
  if (threshold > 0 && (isNameColumn(sourceCol) || isNameColumn(targetCol))) return true;
  return false;
}

/** Extract-stage request: tables, projections, keys, rules — no report grouping/comparisons. */
export function buildExtractRequest(dualMode: boolean, extract: ExtractConfig): RecordMatchRequest | null {
  const ref = refAsTable(extract.sourceRef);
  if (!ref) return null;

  const displays: DisplayColumn[] = extract.displayCols.map((column) => ({ ...ref, column }));

  let sourceRules: PredicateSpecWire | null = buildRuleSpec(
    ref,
    extract.ruleColumn,
    extract.ruleOp,
    extract.ruleValue,
  );
  if (extract.useValuePicker && extract.valuePickerSpec) {
    sourceRules = extract.valuePickerSpec;
  } else if (extract.fuzzyRuleEnabled && extract.ruleColumn && extract.fuzzyRuleName.trim()) {
    sourceRules = {
      root: {
        type: "PREDICATE",
        column: {
          table: { catalog: ref.catalog, schema: ref.schema, table: ref.table },
          column: extract.ruleColumn,
        },
        operator: "FUZZY_MATCH",
        value: [
          extract.fuzzyRuleName.trim(),
          extract.fuzzyRuleThreshold,
          {
            ignoreSpaces: extract.fuzzyIgnoreSpaces,
            caseSensitive: extract.fuzzyCaseSensitive,
          },
        ],
      },
    };
  }

  if (displays.length === 0) return null;

  if (!dualMode) {
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

  const tgt = refAsTable(extract.targetRef);
  if (!tgt || !extract.joinKeySource || !extract.joinKeyTarget) return null;

  const sourceCriteria: MatchCriterion[] = [
    { ...ref, column: extract.joinKeySource, fuzzyThresholdPercent: null },
  ];
  const targetCriteria: MatchCriterion[] = [
    { ...tgt, column: extract.joinKeyTarget, fuzzyThresholdPercent: null },
  ];
  const targetRules = buildRuleSpec(tgt, extract.targetRuleColumn, extract.targetRuleOp, extract.targetRuleValue);

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
  if (extract.joinType !== "INNER") {
    req.joinType = extract.joinType;
  }
  return req;
}

function buildComparisonGroups(
  sourceRef: TableRef,
  targetRef: TableRef,
  report: ReportConfig,
): ComparisonGroup[] {
  return report.comparisonPairs
    .filter((p) => p.sourceColumn && p.targetColumn)
    .map((p) => ({
      source: [{ ...sourceRef, column: p.sourceColumn, fuzzyThresholdPercent: null }],
      target: [{ ...targetRef, column: p.targetColumn, fuzzyThresholdPercent: null }],
      mode: "COMBINE" as const,
      fuzzyThresholdPercent:
        isNameColumn(p.sourceColumn) || isNameColumn(p.targetColumn) ? p.fuzzyThresholdPercent : null,
      separator: " ",
    }));
}

/** Full server request = extract base + report-stage options. */
export function buildMergedRequest(
  dualMode: boolean,
  extract: ExtractConfig,
  report: ReportConfig,
): RecordMatchRequest | null {
  const base = buildExtractRequest(dualMode, extract);
  if (!base) return null;

  const ref = refAsTable(extract.sourceRef)!;
  const req: RecordMatchRequest = { ...base };

  req.highlightDuplicates = report.highlightDuplicates;
  req.mismatchOnly = report.mismatchOnly;

  const joinType = report.joinType ?? extract.joinType;
  if (!dualMode) {
    req.singleSource = true;
  } else if (joinType !== "INNER") {
    req.joinType = joinType;
  } else {
    delete req.joinType;
  }

  if (dualMode && req.sourceCriteria?.[0] && req.targetCriteria?.[0]) {
    const srcCol = req.sourceCriteria[0].column;
    const tgtCol = req.targetCriteria[0].column;
    const fuzzy = pairIsFuzzy(srcCol, tgtCol, report.joinFuzzyThresholdPercent);
    req.sourceCriteria = [
      {
        ...req.sourceCriteria[0],
        fuzzyThresholdPercent: fuzzy ? report.joinFuzzyThresholdPercent : null,
      },
    ];
  }

  if (report.mismatchOnly && req.comparisonGroups && req.comparisonGroups.length > 0) {
    req.mismatchOnly = true;
  } else {
    delete req.mismatchOnly;
  }

  if (report.groupEnabled && report.groupByCol) {
    req.groupByColumns = [{ ...ref, column: report.groupByCol }];
    req.sourceDisplayColumns = [];
    req.aggregates = [];
    if (report.aggregateFn === "COUNT" && !report.aggregateCol) {
      req.aggregates.push({ function: "COUNT", distinct: report.countDistinct });
    } else if (report.aggregateCol) {
      req.aggregates.push({
        function: report.aggregateFn,
        column: { ...ref, column: report.aggregateCol },
        distinct: report.aggregateFn === "COUNT" ? report.countDistinct : false,
      });
    }
    // Backend rejects grouping combined with comparisons or dedup (MatchGroupingSql).
    req.comparisonGroups = [];
    req.dedup = null;
    delete req.mismatchOnly;
  }

  const tgt = refAsTable(extract.targetRef);
  if (dualMode && tgt && !(report.groupEnabled && report.groupByCol)) {
    const groups = buildComparisonGroups(ref, tgt, report);
    if (groups.length > 0) {
      req.comparisonGroups = groups;
    }
  }

  if (report.dedupEnabled && dualMode && tgt && !(report.groupEnabled && report.groupByCol)) {
    const dedupCol =
      extract.sourceRef.catalog && extract.displayCols.includes("last_refreshed_at")
        ? "last_refreshed_at"
        : extract.joinKeySource;
    req.dedup = { ...ref, column: dedupCol };
  }

  return req;
}

/** Persist extract attribute picks on saved queries even when report grouping clears display columns on the wire. */
export function preserveExtractDisplayColumnsOnSave(
  extract: ExtractConfig,
  req: RecordMatchRequest,
): RecordMatchRequest {
  const ref = refAsTable(extract.sourceRef);
  if (!ref || extract.displayCols.length === 0) {
    return req;
  }
  const displays: DisplayColumn[] = extract.displayCols.map((column) => ({ ...ref, column }));
  return { ...req, sourceDisplayColumns: displays };
}
