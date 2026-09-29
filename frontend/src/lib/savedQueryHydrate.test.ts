import { describe, expect, it } from "vitest";
import {
  hydrateExtractConfigFromRequest,
  hydrateReportConfigFromRequest,
  hydrateReportStateFromSavedRequest,
} from "@/lib/savedQueryHydrate";
import type { PredicateNodeWire, RecordMatchRequest, RuleOperator } from "@/lib/analysisApi";
import {
  EMPTY_EXTRACT_CONFIG,
  EMPTY_REPORT_CONFIG,
  type ExtractConfig,
} from "@/lib/queryBuilderPipelineTypes";

describe("hydrateReportStateFromSavedRequest", () => {
  it("restores criteria, display columns, join type, and comparisons", () => {
    const req: RecordMatchRequest = {
      sourceCriteria: [
        {
          catalog: "iceberg",
          schema: "srse",
          table: "beneficiary",
          column: "id",
          fuzzyThresholdPercent: null,
        },
      ],
      targetCriteria: [
        {
          catalog: "iceberg",
          schema: "srse",
          table: "beneficiary",
          column: "id",
          fuzzyThresholdPercent: null,
        },
      ],
      sourceDisplayColumns: [
        { catalog: "iceberg", schema: "srse", table: "beneficiary", column: "district" },
      ],
      targetDisplayColumns: [],
      highlightDuplicates: true,
      dedup: null,
      joinType: "LEFT",
      comparisonGroups: [
        {
          source: [{ catalog: "iceberg", schema: "srse", table: "beneficiary", column: "gender", fuzzyThresholdPercent: null }],
          target: [{ catalog: "iceberg", schema: "srse", table: "beneficiary", column: "gender", fuzzyThresholdPercent: null }],
          mode: "COMBINE",
          fuzzyThresholdPercent: null,
          separator: " ",
        },
      ],
      mismatchOnly: true,
    };

    const state = hydrateReportStateFromSavedRequest(req);
    expect(state.sourceRows[0].ref.table).toBe("beneficiary");
    expect(state.sourceRows[0].column).toBe("id");
    expect(state.sourceDisplayRows[0].column).toBe("district");
    expect(state.joinType).toBe("LEFT");
    expect(state.highlightDuplicates).toBe(true);
    expect(state.mismatchOnly).toBe(true);
    expect(state.comparisonPairs[0].sourceColumn).toBe("gender");
  });

  it("restores source and destination display columns, and loads saves that omit the destination list", () => {
    const withBoth = hydrateExtractConfigFromRequest({
      sourceCriteria: [
        { catalog: "iceberg", schema: "srse", table: "beneficiary", column: "m_id", fuzzyThresholdPercent: null },
      ],
      targetCriteria: [
        {
          catalog: "iceberg_silver",
          schema: "silver_txn",
          table: "tbl_txn_bankdtl",
          column: "m_id",
          fuzzyThresholdPercent: null,
        },
      ],
      sourceDisplayColumns: [
        { catalog: "iceberg", schema: "srse", table: "beneficiary", column: "district" },
        { catalog: "iceberg", schema: "srse", table: "beneficiary", column: "full_name" },
      ],
      targetDisplayColumns: [
        { catalog: "iceberg_silver", schema: "silver_txn", table: "tbl_txn_bankdtl", column: "ifsc" },
      ],
      highlightDuplicates: false,
      dedup: null,
    });
    expect(withBoth.displayCols).toEqual(["district", "full_name"]);
    expect(withBoth.targetDisplayCols).toEqual(["ifsc"]);

    const legacy = hydrateExtractConfigFromRequest({
      sourceCriteria: [
        { catalog: "iceberg", schema: "srse", table: "beneficiary", column: "m_id", fuzzyThresholdPercent: null },
      ],
      targetCriteria: [
        {
          catalog: "iceberg_silver",
          schema: "silver_txn",
          table: "tbl_txn_bankdtl",
          column: "m_id",
          fuzzyThresholdPercent: null,
        },
      ],
      sourceDisplayColumns: [{ catalog: "iceberg", schema: "srse", table: "beneficiary", column: "district" }],
      highlightDuplicates: false,
      dedup: null,
    });
    expect(legacy.displayCols).toEqual(["district"]);
    expect(legacy.targetDisplayCols).toEqual([]);
  });
});

const BENEFICIARY = { catalog: "iceberg", schema: "srse", table: "beneficiary" };

function basicPredicate(column: string, operator: RuleOperator, value?: unknown): PredicateNodeWire {
  const node: PredicateNodeWire = {
    type: "PREDICATE",
    column: { table: BENEFICIARY, column },
    operator,
  };
  if (value !== undefined) {
    node.value = value;
  }
  return node;
}

describe("hydrateExtractConfigFromRequest basic rule rows", () => {
  it("maps two basic source children of an AND group and skips a fuzzy child", () => {
    const extract = hydrateExtractConfigFromRequest({
      sourceCriteria: [],
      targetCriteria: [],
      sourceDisplayColumns: [{ ...BENEFICIARY, column: "district" }],
      highlightDuplicates: false,
      dedup: null,
      singleSource: true,
      sourceRules: {
        root: {
          type: "GROUP",
          op: "AND",
          children: [
            basicPredicate("district", "EQ", "Jaipur"),
            basicPredicate("age_years", "GT", 18),
            basicPredicate("full_name", "FUZZY_MATCH", ["Geeta", 80, { ignoreSpaces: false }]),
          ],
        },
      },
    });

    expect(extract.sourceRuleRows.map(({ column, operator, value }) => ({ column, operator, value }))).toEqual([
      { column: "district", operator: "EQ", value: "Jaipur" },
      { column: "age_years", operator: "GT", value: "18" },
    ]);
    expect(extract.sourceRuleRows[0]?.id).toEqual(expect.any(String));
    expect(extract.sourceRuleRows[0]?.id.length).toBeGreaterThan(0);
    expect(extract.sourceRuleRows[1]?.id).not.toBe(extract.sourceRuleRows[0]?.id);
    expect(extract.targetRuleRows).toEqual([]);
    expect(extract.ruleColumn).toBe("");
    expect(extract.ruleOp).toBe("GT");
    expect(extract.ruleValue).toBe("");
    expect(extract.fuzzyRuleEnabled).toBe(false);
  });

  it("maps two basic target children of an AND group, with an empty value for a null operator", () => {
    const extract = hydrateExtractConfigFromRequest({
      sourceCriteria: [],
      targetCriteria: [],
      highlightDuplicates: false,
      dedup: null,
      targetRules: {
        root: {
          type: "GROUP",
          op: "AND",
          children: [
            basicPredicate("ifsc", "EQ", "SBIN0001"),
            basicPredicate("branch", "NOT_NULL", "ignored"),
          ],
        },
      },
    });

    expect(extract.targetRuleRows.map(({ column, operator, value }) => ({ column, operator, value }))).toEqual([
      { column: "ifsc", operator: "EQ", value: "SBIN0001" },
      { column: "branch", operator: "NOT_NULL", value: "" },
    ]);
    expect(extract.targetRuleRows[0]?.id).toEqual(expect.any(String));
    expect(extract.targetRuleRows[1]?.id).not.toBe(extract.targetRuleRows[0]?.id);
    expect(extract.sourceRuleRows).toEqual([]);
    expect(extract.targetRuleColumn).toBe("");
    expect(extract.targetRuleOp).toBe("GT");
    expect(extract.targetRuleValue).toBe("");
  });

  it("keeps a single predicate on the legacy rule fields and leaves both rule arrays empty", () => {
    const sourceRules = {
      root: basicPredicate("age_years", "LTE", 60),
    };
    const targetRules = {
      root: basicPredicate("ifsc", "IS_NULL"),
    };
    const extract = hydrateExtractConfigFromRequest({
      sourceCriteria: [
        { ...BENEFICIARY, column: "m_id", fuzzyThresholdPercent: null },
      ],
      targetCriteria: [
        { ...BENEFICIARY, column: "m_id", fuzzyThresholdPercent: null },
      ],
      sourceDisplayColumns: [{ ...BENEFICIARY, column: "district" }],
      highlightDuplicates: false,
      dedup: null,
      sourceRules,
      targetRules,
    });

    expect(extract.ruleColumn).toBe("age_years");
    expect(extract.ruleOp).toBe("LTE");
    expect(extract.ruleValue).toBe("60");
    expect(extract.useValuePicker).toBe(false);
    expect(extract.valuePickerSpec).toEqual(sourceRules);
    expect(extract.fuzzyRuleEnabled).toBe(false);
    expect(extract.fuzzyRuleName).toBe("");
    expect(extract.fuzzyRuleThreshold).toBe(80);
    expect(extract.targetRuleColumn).toBe("ifsc");
    expect(extract.targetRuleOp).toBe("IS_NULL");
    expect(extract.targetRuleValue).toBe("");
    expect(extract.sourceRuleRows).toEqual([]);
    expect(extract.targetRuleRows).toEqual([]);
  });
});

function expectLegacyExtractFiltersAtDefaults(extract: ExtractConfig) {
  expect(extract.ruleColumn).toBe(EMPTY_EXTRACT_CONFIG.ruleColumn);
  expect(extract.ruleOp).toBe(EMPTY_EXTRACT_CONFIG.ruleOp);
  expect(extract.ruleValue).toBe(EMPTY_EXTRACT_CONFIG.ruleValue);
  expect(extract.useValuePicker).toBe(EMPTY_EXTRACT_CONFIG.useValuePicker);
  expect(extract.valuePickerSpec).toBe(EMPTY_EXTRACT_CONFIG.valuePickerSpec);
  expect(extract.fuzzyRuleEnabled).toBe(EMPTY_EXTRACT_CONFIG.fuzzyRuleEnabled);
  expect(extract.fuzzyRuleName).toBe(EMPTY_EXTRACT_CONFIG.fuzzyRuleName);
  expect(extract.fuzzyRuleThreshold).toBe(EMPTY_EXTRACT_CONFIG.fuzzyRuleThreshold);
  expect(extract.fuzzyIgnoreSpaces).toBe(EMPTY_EXTRACT_CONFIG.fuzzyIgnoreSpaces);
  expect(extract.fuzzyCaseSensitive).toBe(EMPTY_EXTRACT_CONFIG.fuzzyCaseSensitive);
}

describe("IN and fuzzy source predicates hydrate only onto Report", () => {
  const base: RecordMatchRequest = {
    sourceCriteria: [],
    targetCriteria: [],
    sourceDisplayColumns: [{ ...BENEFICIARY, column: "district" }],
    highlightDuplicates: false,
    dedup: null,
    singleSource: true,
  };

  it("leaves Extract legacy fields empty for a single IN predicate and restores it on Report", () => {
    const predicate = basicPredicate("district", "IN", ["Jaipur", "Kota"]);
    const req: RecordMatchRequest = { ...base, sourceRules: { root: predicate } };
    const extract = hydrateExtractConfigFromRequest(req);
    const report = hydrateReportConfigFromRequest(req);

    expectLegacyExtractFiltersAtDefaults(extract);
    expect(extract.sourceRuleRows).toEqual([]);
    expect(extract.targetDisplayCols).toEqual([]);
    expect(report.sourceValueFilterEnabled).toBe(true);
    expect(report.sourceValueFilterColumn).toBe("district");
    expect(report.sourceValueFilterSpec).toEqual({ root: predicate });
    expect(report.sourceFuzzyEnabled).toBe(EMPTY_REPORT_CONFIG.sourceFuzzyEnabled);
    expect(report.sourceFuzzyColumn).toBe(EMPTY_REPORT_CONFIG.sourceFuzzyColumn);
    expect(report.sourceFuzzyName).toBe(EMPTY_REPORT_CONFIG.sourceFuzzyName);
  });

  it("leaves Extract legacy fields empty for a single fuzzy predicate and restores it on Report", () => {
    const req: RecordMatchRequest = {
      ...base,
      sourceRules: {
        root: basicPredicate("full_name", "FUZZY_MATCH", [
          "Geeta Kumari",
          75,
          { ignoreSpaces: true, caseSensitive: true },
        ]),
      },
    };
    const extract = hydrateExtractConfigFromRequest(req);
    const report = hydrateReportConfigFromRequest(req);

    expectLegacyExtractFiltersAtDefaults(extract);
    expect(extract.sourceRuleRows).toEqual([]);
    expect(report.sourceFuzzyEnabled).toBe(true);
    expect(report.sourceFuzzyColumn).toBe("full_name");
    expect(report.sourceFuzzyName).toBe("Geeta Kumari");
    expect(report.sourceFuzzyThreshold).toBe(75);
    expect(report.sourceFuzzyIgnoreSpaces).toBe(true);
    expect(report.sourceFuzzyCaseSensitive).toBe(true);
    expect(report.sourceValueFilterEnabled).toBe(EMPTY_REPORT_CONFIG.sourceValueFilterEnabled);
    expect(report.sourceValueFilterColumn).toBe(EMPTY_REPORT_CONFIG.sourceValueFilterColumn);
    expect(report.sourceValueFilterSpec).toBe(EMPTY_REPORT_CONFIG.sourceValueFilterSpec);
  });

  it("restores basic AND rows on Extract and IN/fuzzy children on Report", () => {
    const valueFilter = basicPredicate("district", "IN", ["Jaipur"]);
    const fuzzy = basicPredicate("full_name", "FUZZY_MATCH", [
      "Geeta",
      80,
      { ignoreSpaces: false, caseSensitive: false },
    ]);
    const req: RecordMatchRequest = {
      ...base,
      sourceRules: {
        root: {
          type: "GROUP",
          op: "AND",
          children: [
            basicPredicate("district", "EQ", "Jaipur"),
            valueFilter,
            basicPredicate("age_years", "GT", 18),
            fuzzy,
          ],
        },
      },
    };
    const extract = hydrateExtractConfigFromRequest(req);
    const report = hydrateReportConfigFromRequest(req);

    expect(extract.sourceRuleRows.map(({ column, operator, value }) => ({ column, operator, value }))).toEqual([
      { column: "district", operator: "EQ", value: "Jaipur" },
      { column: "age_years", operator: "GT", value: "18" },
    ]);
    expectLegacyExtractFiltersAtDefaults(extract);
    expect(report.sourceValueFilterEnabled).toBe(true);
    expect(report.sourceValueFilterColumn).toBe("district");
    expect(report.sourceValueFilterSpec).toEqual({ root: valueFilter });
    expect(report.sourceFuzzyEnabled).toBe(true);
    expect(report.sourceFuzzyColumn).toBe("full_name");
    expect(report.sourceFuzzyName).toBe("Geeta");
    expect(report.sourceFuzzyThreshold).toBe(80);
    expect(report.sourceFuzzyIgnoreSpaces).toBe(false);
    expect(report.sourceFuzzyCaseSensitive).toBe(false);
  });
});

describe("hydrateReportConfigFromRequest source value and fuzzy filters", () => {
  const base: RecordMatchRequest = {
    sourceCriteria: [],
    targetCriteria: [],
    highlightDuplicates: false,
    dedup: null,
    singleSource: true,
  };

  it("hydrates a single IN predicate as the source value filter", () => {
    const predicate = basicPredicate("district", "IN", ["Jaipur", "Kota"]);
    const report = hydrateReportConfigFromRequest({
      ...base,
      sourceRules: { root: predicate },
    });

    expect(report.sourceValueFilterEnabled).toBe(true);
    expect(report.sourceValueFilterColumn).toBe("district");
    expect(report.sourceValueFilterSpec).toEqual({ root: predicate });
    expect(report.sourceFuzzyEnabled).toBe(EMPTY_REPORT_CONFIG.sourceFuzzyEnabled);
    expect(report.sourceFuzzyColumn).toBe(EMPTY_REPORT_CONFIG.sourceFuzzyColumn);
    expect(report.sourceFuzzyName).toBe(EMPTY_REPORT_CONFIG.sourceFuzzyName);
    expect(report.sourceFuzzyThreshold).toBe(EMPTY_REPORT_CONFIG.sourceFuzzyThreshold);
    expect(report.sourceFuzzyIgnoreSpaces).toBe(EMPTY_REPORT_CONFIG.sourceFuzzyIgnoreSpaces);
    expect(report.sourceFuzzyCaseSensitive).toBe(EMPTY_REPORT_CONFIG.sourceFuzzyCaseSensitive);
  });

  it("hydrates a single fuzzy predicate into every source fuzzy field", () => {
    const report = hydrateReportConfigFromRequest({
      ...base,
      sourceRules: {
        root: basicPredicate("full_name", "FUZZY_MATCH", [
          "Geeta Kumari",
          75,
          { ignoreSpaces: true, caseSensitive: true },
        ]),
      },
    });

    expect(report.sourceFuzzyEnabled).toBe(true);
    expect(report.sourceFuzzyColumn).toBe("full_name");
    expect(report.sourceFuzzyName).toBe("Geeta Kumari");
    expect(report.sourceFuzzyThreshold).toBe(75);
    expect(report.sourceFuzzyIgnoreSpaces).toBe(true);
    expect(report.sourceFuzzyCaseSensitive).toBe(true);
    expect(report.sourceValueFilterEnabled).toBe(EMPTY_REPORT_CONFIG.sourceValueFilterEnabled);
    expect(report.sourceValueFilterColumn).toBe(EMPTY_REPORT_CONFIG.sourceValueFilterColumn);
    expect(report.sourceValueFilterSpec).toBe(EMPTY_REPORT_CONFIG.sourceValueFilterSpec);
  });

  it("hydrates the first IN and fuzzy children of an AND group that also has a basic rule", () => {
    const valueFilter = basicPredicate("district", "IN", ["Jaipur"]);
    const fuzzy = basicPredicate("full_name", "FUZZY_MATCH", [
      "Geeta",
      80,
      { ignoreSpaces: false, caseSensitive: false },
    ]);
    const report = hydrateReportConfigFromRequest({
      ...base,
      sourceRules: {
        root: {
          type: "GROUP",
          op: "AND",
          children: [basicPredicate("age_years", "GT", 18), valueFilter, fuzzy],
        },
      },
    });

    expect(report.sourceValueFilterEnabled).toBe(true);
    expect(report.sourceValueFilterColumn).toBe("district");
    expect(report.sourceValueFilterSpec).toEqual({ root: valueFilter });
    expect(report.sourceFuzzyEnabled).toBe(true);
    expect(report.sourceFuzzyColumn).toBe("full_name");
    expect(report.sourceFuzzyName).toBe("Geeta");
    expect(report.sourceFuzzyThreshold).toBe(80);
    expect(report.sourceFuzzyIgnoreSpaces).toBe(false);
    expect(report.sourceFuzzyCaseSensitive).toBe(false);
  });

  it("leaves value and fuzzy filters at empty defaults when source rules have no filters", () => {
    const report = hydrateReportConfigFromRequest({
      ...base,
      joinType: "LEFT",
      highlightDuplicates: true,
    });

    expect(report.sourceValueFilterEnabled).toBe(EMPTY_REPORT_CONFIG.sourceValueFilterEnabled);
    expect(report.sourceValueFilterColumn).toBe(EMPTY_REPORT_CONFIG.sourceValueFilterColumn);
    expect(report.sourceValueFilterSpec).toBe(EMPTY_REPORT_CONFIG.sourceValueFilterSpec);
    expect(report.sourceFuzzyEnabled).toBe(EMPTY_REPORT_CONFIG.sourceFuzzyEnabled);
    expect(report.sourceFuzzyColumn).toBe(EMPTY_REPORT_CONFIG.sourceFuzzyColumn);
    expect(report.sourceFuzzyName).toBe(EMPTY_REPORT_CONFIG.sourceFuzzyName);
    expect(report.sourceFuzzyThreshold).toBe(EMPTY_REPORT_CONFIG.sourceFuzzyThreshold);
    expect(report.sourceFuzzyIgnoreSpaces).toBe(EMPTY_REPORT_CONFIG.sourceFuzzyIgnoreSpaces);
    expect(report.sourceFuzzyCaseSensitive).toBe(EMPTY_REPORT_CONFIG.sourceFuzzyCaseSensitive);
    expect(report.joinType).toBe("LEFT");
    expect(report.highlightDuplicates).toBe(true);
  });
});
