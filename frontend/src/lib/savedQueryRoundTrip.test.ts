import { describe, expect, it } from "vitest";
import type { RecordMatchRequest } from "@/lib/analysisApi";
import { EMPTY_EXTRACT_CONFIG } from "@/lib/queryBuilderPipelineTypes";
import { buildMergedRequest } from "@/lib/queryBuilderRequestBuild";
import {
  dualModeFromRequest,
  hydrateExtractConfigFromRequest,
  hydrateReportConfigFromRequest,
} from "@/lib/savedQueryHydrate";

const BENEFICIARY = { catalog: "iceberg", schema: "srse", table: "beneficiary" };
const BANK = { catalog: "iceberg_silver", schema: "silver_txn", table: "tbl_txn_bankdtl" };

function roundTrip(req: RecordMatchRequest) {
  const dual = dualModeFromRequest(req);
  const extract = hydrateExtractConfigFromRequest(req);
  const report = hydrateReportConfigFromRequest(req);
  const merged = buildMergedRequest(dual, extract, report);
  return { dual, extract, report, merged };
}

describe("saved query request round-trip", () => {
  it("preserves IN / value-picker sourceRules", () => {
    const original: RecordMatchRequest = {
      sourceCriteria: [],
      targetCriteria: [],
      sourceDisplayColumns: [{ ...BENEFICIARY, column: "district" }],
      highlightDuplicates: false,
      dedup: null,
      singleSource: true,
      sourceRules: {
        root: {
          type: "PREDICATE",
          column: { table: BENEFICIARY, column: "district" },
          operator: "IN",
          value: ["Jaipur"],
        },
      },
    };
    const { extract, report, merged } = roundTrip(original);
    expect(extract.useValuePicker).toBe(EMPTY_EXTRACT_CONFIG.useValuePicker);
    expect(extract.ruleColumn).toBe(EMPTY_EXTRACT_CONFIG.ruleColumn);
    expect(extract.ruleOp).toBe(EMPTY_EXTRACT_CONFIG.ruleOp);
    expect(extract.ruleValue).toBe(EMPTY_EXTRACT_CONFIG.ruleValue);
    expect(extract.valuePickerSpec).toBe(EMPTY_EXTRACT_CONFIG.valuePickerSpec);
    expect(extract.fuzzyRuleEnabled).toBe(EMPTY_EXTRACT_CONFIG.fuzzyRuleEnabled);
    expect(report.sourceValueFilterEnabled).toBe(true);
    expect(report.sourceValueFilterColumn).toBe("district");
    expect(report.sourceValueFilterSpec?.root).toMatchObject({ operator: "IN", value: ["Jaipur"] });
    expect(merged?.sourceRules?.root).toMatchObject({
      operator: "IN",
      value: ["Jaipur"],
    });
  });

  it("preserves FUZZY_MATCH predicate with threshold and options", () => {
    const original: RecordMatchRequest = {
      sourceCriteria: [],
      targetCriteria: [],
      sourceDisplayColumns: [{ ...BENEFICIARY, column: "full_name" }],
      highlightDuplicates: false,
      dedup: null,
      singleSource: true,
      sourceRules: {
        root: {
          type: "PREDICATE",
          column: { table: BENEFICIARY, column: "full_name" },
          operator: "FUZZY_MATCH",
          value: ["Kumari", 72, { ignoreSpaces: true, caseSensitive: true }],
        },
      },
    };
    const { extract, report, merged } = roundTrip(original);
    expect(extract.fuzzyRuleEnabled).toBe(EMPTY_EXTRACT_CONFIG.fuzzyRuleEnabled);
    expect(extract.ruleColumn).toBe(EMPTY_EXTRACT_CONFIG.ruleColumn);
    expect(extract.ruleOp).toBe(EMPTY_EXTRACT_CONFIG.ruleOp);
    expect(extract.ruleValue).toBe(EMPTY_EXTRACT_CONFIG.ruleValue);
    expect(extract.fuzzyRuleName).toBe(EMPTY_EXTRACT_CONFIG.fuzzyRuleName);
    expect(extract.fuzzyRuleThreshold).toBe(EMPTY_EXTRACT_CONFIG.fuzzyRuleThreshold);
    expect(extract.fuzzyIgnoreSpaces).toBe(EMPTY_EXTRACT_CONFIG.fuzzyIgnoreSpaces);
    expect(extract.fuzzyCaseSensitive).toBe(EMPTY_EXTRACT_CONFIG.fuzzyCaseSensitive);
    expect(extract.useValuePicker).toBe(EMPTY_EXTRACT_CONFIG.useValuePicker);
    expect(extract.valuePickerSpec).toBe(EMPTY_EXTRACT_CONFIG.valuePickerSpec);
    expect(report.sourceFuzzyEnabled).toBe(true);
    expect(report.sourceFuzzyColumn).toBe("full_name");
    expect(report.sourceFuzzyName).toBe("Kumari");
    expect(report.sourceFuzzyThreshold).toBe(72);
    expect(report.sourceFuzzyIgnoreSpaces).toBe(true);
    expect(report.sourceFuzzyCaseSensitive).toBe(true);
    const rebuilt = merged?.sourceRules?.root;
    expect(rebuilt).toMatchObject({ operator: "FUZZY_MATCH" });
    expect(rebuilt?.value).toEqual(["Kumari", 72, { ignoreSpaces: true, caseSensitive: true }]);
  });

  it("preserves comparisonGroups on dual-table merged requests", () => {
    const original: RecordMatchRequest = {
      sourceCriteria: [{ ...BENEFICIARY, column: "id", fuzzyThresholdPercent: null }],
      targetCriteria: [{ ...BANK, column: "m_id", fuzzyThresholdPercent: null }],
      sourceDisplayColumns: [{ ...BENEFICIARY, column: "district" }],
      highlightDuplicates: false,
      dedup: null,
      singleSource: false,
      joinType: "LEFT",
      comparisonGroups: [
        {
          source: [{ ...BENEFICIARY, column: "district", fuzzyThresholdPercent: null }],
          target: [{ ...BANK, column: "district", fuzzyThresholdPercent: null }],
          mode: "COMBINE",
          fuzzyThresholdPercent: null,
          separator: " ",
        },
      ],
    };
    const { report, merged } = roundTrip(original);
    expect(report.comparisonPairs).toEqual([
      { sourceColumn: "district", targetColumn: "district", fuzzyThresholdPercent: 80 },
    ]);
    expect(merged?.comparisonGroups).toHaveLength(1);
    expect(merged?.comparisonGroups?.[0].source[0].column).toBe("district");
    expect(merged?.comparisonGroups?.[0].target[0].column).toBe("district");
  });

  it("restores source and destination display columns, including saves without a destination list", () => {
    const original: RecordMatchRequest = {
      sourceCriteria: [{ ...BENEFICIARY, column: "id", fuzzyThresholdPercent: null }],
      targetCriteria: [{ ...BANK, column: "m_id", fuzzyThresholdPercent: null }],
      sourceDisplayColumns: [
        { ...BENEFICIARY, column: "district" },
        { ...BENEFICIARY, column: "full_name" },
      ],
      targetDisplayColumns: [{ ...BANK, column: "ifsc" }],
      highlightDuplicates: false,
      dedup: null,
      singleSource: false,
    };
    const { extract, merged } = roundTrip(original);
    expect(extract.displayCols).toEqual(["district", "full_name"]);
    expect(extract.targetDisplayCols).toEqual(["ifsc"]);
    expect(merged?.sourceDisplayColumns).toEqual(original.sourceDisplayColumns);
    expect(merged?.targetDisplayColumns).toEqual(original.targetDisplayColumns);

    const legacy: RecordMatchRequest = {
      sourceCriteria: [{ ...BENEFICIARY, column: "id", fuzzyThresholdPercent: null }],
      targetCriteria: [{ ...BANK, column: "m_id", fuzzyThresholdPercent: null }],
      sourceDisplayColumns: [{ ...BENEFICIARY, column: "district" }],
      highlightDuplicates: false,
      dedup: null,
      singleSource: false,
    };
    const reopened = roundTrip(legacy);
    expect(reopened.extract.displayCols).toEqual(["district"]);
    expect(reopened.extract.targetDisplayCols).toEqual([]);
    expect(reopened.merged?.sourceDisplayColumns).toEqual(legacy.sourceDisplayColumns);
    expect(reopened.merged?.targetDisplayColumns).toBeUndefined();
  });

  it("preserves a dual request that selects destination columns only", () => {
    const original: RecordMatchRequest = {
      sourceCriteria: [{ ...BENEFICIARY, column: "id", fuzzyThresholdPercent: null }],
      targetCriteria: [{ ...BANK, column: "m_id", fuzzyThresholdPercent: null }],
      sourceDisplayColumns: [],
      targetDisplayColumns: [{ ...BANK, column: "ifsc" }, { ...BANK, column: "branch" }],
      highlightDuplicates: false,
      dedup: null,
      singleSource: false,
    };
    const { extract, merged } = roundTrip(original);
    expect(extract.displayCols).toEqual([]);
    expect(extract.targetDisplayCols).toEqual(["ifsc", "branch"]);
    expect(merged?.singleSource).toBe(false);
    expect(merged?.sourceDisplayColumns).toEqual([]);
    expect(merged?.targetDisplayColumns).toEqual(original.targetDisplayColumns);
    expect(merged?.sourceCriteria?.[0]).toMatchObject({ ...BENEFICIARY, column: "id" });
    expect(merged?.targetCriteria?.[0]).toMatchObject({ ...BANK, column: "m_id" });
  });

  it("preserves dedup on dual-table merged requests without grouping", () => {
    const original: RecordMatchRequest = {
      sourceCriteria: [{ ...BENEFICIARY, column: "id", fuzzyThresholdPercent: null }],
      targetCriteria: [{ ...BANK, column: "m_id", fuzzyThresholdPercent: null }],
      sourceDisplayColumns: [{ ...BENEFICIARY, column: "id" }],
      highlightDuplicates: false,
      dedup: { ...BENEFICIARY, column: "id" },
      singleSource: false,
      joinType: "INNER",
    };
    const { report, merged } = roundTrip(original);
    expect(report.dedupEnabled).toBe(true);
    expect(merged?.dedup).toMatchObject({ column: "id" });
    expect(merged?.groupByColumns ?? []).toHaveLength(0);
  });
});
