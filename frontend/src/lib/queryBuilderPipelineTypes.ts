import type { CascadeValue } from "@/components/LakehouseCascade";
import type {
  AggregateFunction,
  JoinType,
  PredicateSpecWire,
  RecordMatchRequest,
  RuleOperator,
} from "@/lib/analysisApi";

/** Serializable comparison pair (no React row id). */
export type ComparisonPairConfig = {
  sourceColumn: string;
  targetColumn: string;
  fuzzyThresholdPercent: number;
};

/** Extract Records tab — tables, attributes, keys, and row-level rules only. */
export type ExtractConfig = {
  sourceRef: CascadeValue;
  targetRef: CascadeValue;
  displayCols: string[];
  joinKeySource: string;
  joinKeyTarget: string;
  joinType: JoinType;
  ruleColumn: string;
  ruleOp: RuleOperator;
  ruleValue: string;
  useValuePicker: boolean;
  valuePickerSpec: PredicateSpecWire | null;
  fuzzyRuleEnabled: boolean;
  fuzzyRuleName: string;
  fuzzyRuleThreshold: number;
  fuzzyIgnoreSpaces: boolean;
  fuzzyCaseSensitive: boolean;
  targetRuleColumn: string;
  targetRuleOp: RuleOperator;
  targetRuleValue: string;
};

/** Report Analysis stage — filters, fuzzy/join options, grouping, comparisons. */
export type ReportConfig = {
  mismatchOnly: boolean;
  highlightDuplicates: boolean;
  dedupEnabled: boolean;
  joinType: JoinType;
  comparisonPairs: ComparisonPairConfig[];
  groupEnabled: boolean;
  groupByCol: string;
  aggregateFn: AggregateFunction;
  aggregateCol: string;
  countDistinct: boolean;
  /** Per-side fuzzy threshold on the primary join key (source side). */
  joinFuzzyThresholdPercent: number;
  joinFuzzyIgnoreSpaces: boolean;
  joinFuzzyCaseSensitive: boolean;
};

export type QueryExecutionSnapshot = {
  columns: string[];
  rows: Record<string, unknown>[];
  totalRows: number | null;
  sql: string;
};

export type QueryBuilderPipelineMeta = {
  dualMode: boolean;
  extract: ExtractConfig;
  report: ReportConfig;
};

export const EMPTY_EXTRACT_CONFIG: ExtractConfig = {
  sourceRef: { catalog: "", schema: "", table: "" },
  targetRef: { catalog: "", schema: "", table: "" },
  displayCols: [],
  joinKeySource: "",
  joinKeyTarget: "",
  joinType: "INNER",
  ruleColumn: "",
  ruleOp: "GT",
  ruleValue: "",
  useValuePicker: false,
  valuePickerSpec: null,
  fuzzyRuleEnabled: false,
  fuzzyRuleName: "",
  fuzzyRuleThreshold: 80,
  fuzzyIgnoreSpaces: false,
  fuzzyCaseSensitive: false,
  targetRuleColumn: "",
  targetRuleOp: "GT",
  targetRuleValue: "",
};

export const EMPTY_REPORT_CONFIG: ReportConfig = {
  mismatchOnly: false,
  highlightDuplicates: false,
  dedupEnabled: false,
  joinType: "INNER",
  comparisonPairs: [],
  groupEnabled: false,
  groupByCol: "",
  aggregateFn: "COUNT",
  aggregateCol: "",
  countDistinct: false,
  joinFuzzyThresholdPercent: 80,
  joinFuzzyIgnoreSpaces: false,
  joinFuzzyCaseSensitive: false,
};

/** Stable JSON comparison for “did server-side report options change since last apply?” */
export function requestsEqual(a: RecordMatchRequest | null, b: RecordMatchRequest | null): boolean {
  if (a === b) return true;
  if (!a || !b) return false;
  return JSON.stringify(a) === JSON.stringify(b);
}
