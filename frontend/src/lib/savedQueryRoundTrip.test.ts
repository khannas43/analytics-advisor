import { describe, expect, it } from "vitest";
import type { RecordMatchRequest } from "@/lib/analysisApi";
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
    const { extract, merged } = roundTrip(original);
    expect(extract.useValuePicker).toBe(true);
    expect(extract.ruleColumn).toBe("district");
    expect(extract.valuePickerSpec?.root).toMatchObject({ operator: "IN", value: ["Jaipur"] });
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
    const { extract, merged } = roundTrip(original);
    expect(extract.fuzzyRuleEnabled).toBe(true);
    expect(extract.ruleColumn).toBe("full_name");
    expect(extract.fuzzyRuleName).toBe("Kumari");
    expect(extract.fuzzyRuleThreshold).toBe(72);
    expect(extract.fuzzyIgnoreSpaces).toBe(true);
    expect(extract.fuzzyCaseSensitive).toBe(true);
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
