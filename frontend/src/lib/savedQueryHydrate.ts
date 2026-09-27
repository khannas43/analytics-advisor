import type {
  DisplayColumn,
  MatchCriterion,
  MatchGroup,
  PredicateNodeWire,
  PredicateSpecWire,
  RecordMatchRequest,
  RuleOperator,
} from "@/lib/analysisApi";
import type { ComparisonPairRow, CriterionRowModel } from "@/lib/analysisCriterionModel";
import type { CascadeValue } from "@/components/LakehouseCascade";
import {
  EMPTY_EXTRACT_CONFIG,
  type ComparisonPairConfig,
  type ExtractConfig,
  type ReportConfig,
} from "@/lib/queryBuilderPipelineTypes";

export type HydratedDisplayRow = {
  id: string;
  ref: CascadeValue;
  column: string;
};

export type HydratedCriterionRow = CriterionRowModel & { id: string };

export type HydratedReportState = {
  sourceRows: HydratedCriterionRow[];
  targetRows: HydratedCriterionRow[];
  sourceDisplayRows: HydratedDisplayRow[];
  targetDisplayRows: HydratedDisplayRow[];
  joinType: RecordMatchRequest["joinType"];
  comparisonPairs: ComparisonPairRow[];
  highlightDuplicates: boolean;
  dedupEnabled: boolean;
  mismatchOnly: boolean;
};

function refFrom(c: { catalog: string; schema: string; table: string }): CascadeValue {
  return { catalog: c.catalog, schema: c.schema, table: c.table };
}

function rowFromSide(
  criteria: MatchCriterion[],
  groupSide: MatchCriterion[] | undefined,
  group: MatchGroup | undefined,
  side: "source" | "target",
): HydratedCriterionRow {
  const primary = criteria[0];
  const cols = groupSide?.map((c) => c.column) ?? criteria.map((c) => c.column);
  const fuzzyFromGroup = side === "source" ? group?.fuzzyThresholdPercent : null;
  const fuzzyOpts = side === "source" ? group?.fuzzyOptions : null;
  return {
    id: crypto.randomUUID(),
    ref: refFrom(primary),
    column: cols[0] ?? "",
    columns: [],
    extraColumns: cols.slice(1),
    fuzzyThresholdPercent:
      primary.fuzzyThresholdPercent ?? fuzzyFromGroup ?? 80,
    fuzzyIgnoreSpaces: fuzzyOpts?.ignoreSpaces ?? false,
    fuzzyCaseSensitive: fuzzyOpts?.caseSensitive ?? false,
    mode: group?.mode ?? "COMBINE",
    separator: group?.separator ?? " ",
  };
}

function displayFrom(list: DisplayColumn[] | undefined): HydratedDisplayRow[] {
  if (!list?.length) {
    return [];
  }
  return list.map((d) => ({
    id: crypto.randomUUID(),
    ref: refFrom(d),
    column: d.column,
  }));
}

/**
 * Maps a persisted {@link RecordMatchRequest} back into Report Analysis form state.
 * Column lists are left empty — callers should load them via {@code listAnalysisColumns}.
 */
export function hydrateReportStateFromSavedRequest(req: RecordMatchRequest): HydratedReportState {
  const groups = req.joinGroups ?? [];
  const n = Math.max(req.sourceCriteria.length, req.targetCriteria.length, groups.length);
  const sourceRows: HydratedCriterionRow[] = [];
  const targetRows: HydratedCriterionRow[] = [];

  for (let i = 0; i < n; i++) {
    const srcCrit = req.sourceCriteria[i];
    const tgtCrit = req.targetCriteria[i];
    if (!srcCrit || !tgtCrit) {
      continue;
    }
    const group = groups[i];
    sourceRows.push(rowFromSide([srcCrit], group?.source, group, "source"));
    targetRows.push(rowFromSide([tgtCrit], group?.target, group, "target"));
  }

  const comparisonPairs: ComparisonPairRow[] = (req.comparisonGroups ?? []).map((g) => ({
    id: crypto.randomUUID(),
    sourceColumn: g.source[0]?.column ?? "",
    targetColumn: g.target[0]?.column ?? "",
    fuzzyThresholdPercent: g.fuzzyThresholdPercent ?? 80,
  }));

  return {
    sourceRows: sourceRows.length > 0 ? sourceRows : [],
    targetRows: targetRows.length > 0 ? targetRows : [],
    sourceDisplayRows: displayFrom(req.sourceDisplayColumns),
    targetDisplayRows: displayFrom(req.targetDisplayColumns),
    joinType: req.joinType ?? "INNER",
    comparisonPairs,
    highlightDuplicates: req.highlightDuplicates ?? false,
    dedupEnabled: Boolean(req.dedup),
    mismatchOnly: req.mismatchOnly ?? false,
  };
}

function predicateRoot(spec: PredicateSpecWire | null | undefined): PredicateNodeWire | null {
  if (!spec?.root || spec.root.type !== "PREDICATE") {
    return null;
  }
  return spec.root;
}

function ruleFieldsFromSpec(spec: PredicateSpecWire | null | undefined): {
  column: string;
  operator: RuleOperator;
  value: string;
  fuzzyRuleEnabled: boolean;
  fuzzyRuleName: string;
  fuzzyRuleThreshold: number;
  fuzzyIgnoreSpaces: boolean;
  fuzzyCaseSensitive: boolean;
  useValuePicker: boolean;
  valuePickerSpec: PredicateSpecWire | null;
} {
  const root = predicateRoot(spec);
  if (!root) {
    return {
      column: "",
      operator: "GT",
      value: "",
      fuzzyRuleEnabled: false,
      fuzzyRuleName: "",
      fuzzyRuleThreshold: 80,
      fuzzyIgnoreSpaces: false,
      fuzzyCaseSensitive: false,
      useValuePicker: false,
      valuePickerSpec: null,
    };
  }
  if (root.operator === "IN") {
    return {
      column: root.column.column,
      operator: "IN",
      value: "",
      fuzzyRuleEnabled: false,
      fuzzyRuleName: "",
      fuzzyRuleThreshold: 80,
      fuzzyIgnoreSpaces: false,
      fuzzyCaseSensitive: false,
      useValuePicker: true,
      valuePickerSpec: spec ?? null,
    };
  }
  if (root.operator === "FUZZY_MATCH" && Array.isArray(root.value)) {
    const [name, threshold, opts] = root.value as [string, number, { ignoreSpaces?: boolean; caseSensitive?: boolean }];
    return {
      column: root.column.column,
      operator: "GT",
      value: "",
      fuzzyRuleEnabled: true,
      fuzzyRuleName: name,
      fuzzyRuleThreshold: threshold,
      fuzzyIgnoreSpaces: opts?.ignoreSpaces ?? false,
      fuzzyCaseSensitive: opts?.caseSensitive ?? false,
      useValuePicker: false,
      valuePickerSpec: null,
    };
  }
  const value =
    root.value === undefined || root.value === null
      ? ""
      : typeof root.value === "object"
        ? JSON.stringify(root.value)
        : String(root.value);
  return {
    column: root.column.column,
    operator: root.operator,
    value,
    fuzzyRuleEnabled: false,
    fuzzyRuleName: "",
    fuzzyRuleThreshold: 80,
    fuzzyIgnoreSpaces: false,
    fuzzyCaseSensitive: false,
    useValuePicker: false,
    valuePickerSpec: spec ?? null,
  };
}

/** Query Builder Extract tab — physical tables, attributes, keys, rules (no report-stage options). */
export function hydrateExtractConfigFromRequest(req: RecordMatchRequest): ExtractConfig {
  const srcCrit = req.sourceCriteria[0];
  const tgtCrit = req.targetCriteria[0];
  const displayRef =
    req.sourceDisplayColumns?.[0] ?? (srcCrit ? refFrom(srcCrit) : EMPTY_EXTRACT_CONFIG.sourceRef);
  const sourceRef: CascadeValue = refFrom(displayRef);
  const targetRef: CascadeValue = tgtCrit ? refFrom(tgtCrit) : EMPTY_EXTRACT_CONFIG.targetRef;
  const sourceRules = ruleFieldsFromSpec(req.sourceRules);
  const targetRules = ruleFieldsFromSpec(req.targetRules);

  return {
    sourceRef,
    targetRef,
    displayCols: (req.sourceDisplayColumns ?? []).map((d) => d.column),
    joinKeySource: srcCrit?.column ?? "",
    joinKeyTarget: tgtCrit?.column ?? "",
    joinType: req.joinType ?? "INNER",
    ruleColumn: sourceRules.column,
    ruleOp: sourceRules.operator,
    ruleValue: sourceRules.value,
    useValuePicker: sourceRules.useValuePicker,
    valuePickerSpec: sourceRules.valuePickerSpec,
    fuzzyRuleEnabled: sourceRules.fuzzyRuleEnabled,
    fuzzyRuleName: sourceRules.fuzzyRuleName,
    fuzzyRuleThreshold: sourceRules.fuzzyRuleThreshold,
    fuzzyIgnoreSpaces: sourceRules.fuzzyIgnoreSpaces,
    fuzzyCaseSensitive: sourceRules.fuzzyCaseSensitive,
    targetRuleColumn: targetRules.column,
    targetRuleOp: targetRules.operator,
    targetRuleValue: targetRules.value,
  };
}

function comparisonConfigs(req: RecordMatchRequest): ComparisonPairConfig[] {
  return (req.comparisonGroups ?? []).map((g) => ({
    sourceColumn: g.source[0]?.column ?? "",
    targetColumn: g.target[0]?.column ?? "",
    fuzzyThresholdPercent: g.fuzzyThresholdPercent ?? 80,
  }));
}

/** Query Builder Report tab — filters, fuzzy/join, grouping, comparisons. */
export function hydrateReportConfigFromRequest(req: RecordMatchRequest): ReportConfig {
  const groupCol = req.groupByColumns?.[0]?.column ?? "";
  const agg = req.aggregates?.[0];
  const joinFuzzy = req.sourceCriteria[0]?.fuzzyThresholdPercent ?? 80;
  const groups = req.joinGroups ?? [];
  const fuzzyOpts = groups[0]?.fuzzyOptions;

  return {
    mismatchOnly: req.mismatchOnly ?? false,
    highlightDuplicates: req.highlightDuplicates ?? false,
    dedupEnabled: Boolean(req.dedup),
    joinType: req.joinType ?? "INNER",
    comparisonPairs: comparisonConfigs(req),
    groupEnabled: Boolean(groupCol || agg),
    groupByCol: groupCol,
    aggregateFn: agg?.function ?? "COUNT",
    aggregateCol: agg?.column?.column ?? "",
    countDistinct: agg?.distinct ?? false,
    joinFuzzyThresholdPercent: joinFuzzy,
    joinFuzzyIgnoreSpaces: fuzzyOpts?.ignoreSpaces ?? false,
    joinFuzzyCaseSensitive: fuzzyOpts?.caseSensitive ?? false,
  };
}

export function dualModeFromRequest(req: RecordMatchRequest): boolean {
  return req.singleSource !== true;
}
