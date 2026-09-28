import { describe, expect, it } from "vitest";
import type { RuleOperator } from "@/lib/analysisApi";
import { buildExtractRequest, buildMergedRequest } from "@/lib/queryBuilderRequestBuild";
import { EMPTY_EXTRACT_CONFIG, EMPTY_REPORT_CONFIG, type ExtractConfig } from "@/lib/queryBuilderPipelineTypes";

const SOURCE = { catalog: "iceberg", schema: "srse", table: "beneficiary" };
const TARGET = { catalog: "iceberg_silver", schema: "silver_txn", table: "tbl_txn_bankdtl" };

const VALUE_OPS: RuleOperator[] = ["EQ", "NE", "LT", "LTE", "GT", "GTE"];

function extract(overrides: Partial<ExtractConfig> = {}): ExtractConfig {
  return {
    ...EMPTY_EXTRACT_CONFIG,
    sourceRef: SOURCE,
    targetRef: TARGET,
    displayCols: ["district", "full_name"],
    joinKeySource: "m_id",
    joinKeyTarget: "m_id",
    ...overrides,
  };
}

/** State after fuzzy source-rule mode selected district and a name. */
function afterFuzzyOn(overrides: Partial<ExtractConfig> = {}): ExtractConfig {
  return extract({
    ruleColumn: "district",
    ruleOp: "GT",
    ruleValue: "",
    fuzzyRuleEnabled: true,
    fuzzyRuleName: "Geeta",
    fuzzyRuleThreshold: 80,
    ...overrides,
  });
}

describe("ordinary source rules", () => {
  it("emits a fuzzy predicate while fuzzy mode is on, then none after it is turned off", () => {
    const fuzzyOn = afterFuzzyOn();
    const whileOn = buildExtractRequest(false, fuzzyOn)!;
    expect(whileOn.sourceRules?.root).toMatchObject({
      operator: "FUZZY_MATCH",
      column: { column: "district" },
      value: ["Geeta", 80, { ignoreSpaces: false, caseSensitive: false }],
    });

    const fuzzyOff = { ...fuzzyOn, fuzzyRuleEnabled: false };
    for (const dualMode of [false, true]) {
      const req = buildExtractRequest(dualMode, fuzzyOff)!;
      const merged = buildMergedRequest(dualMode, fuzzyOff, EMPTY_REPORT_CONFIG)!;
      expect(req.sourceRules).toBeUndefined();
      expect(merged.sourceRules).toBeUndefined();
    }
  });

  it.each(["", "   "])("omits a value-requiring rule when the value is blank (%j)", (ruleValue) => {
    for (const ruleOp of VALUE_OPS) {
      const single = buildExtractRequest(false, afterFuzzyOn({ fuzzyRuleEnabled: false, ruleOp, ruleValue }))!;
      const dual = buildExtractRequest(true, afterFuzzyOn({ fuzzyRuleEnabled: false, ruleOp, ruleValue }))!;
      expect(single.sourceRules).toBeUndefined();
      expect(dual.sourceRules).toBeUndefined();
    }
  });

  it("still emits a comparison once the ordinary value is filled in", () => {
    const req = buildExtractRequest(
      false,
      afterFuzzyOn({ fuzzyRuleEnabled: false, ruleOp: "GT", ruleValue: "10" }),
    )!;
    expect(req.sourceRules?.root).toMatchObject({
      operator: "GT",
      column: { column: "district" },
      value: 10,
    });
  });

  it.each(["IS_NULL", "NOT_NULL"] as const)("emits %s with a blank value", (ruleOp) => {
    const configured = afterFuzzyOn({ fuzzyRuleEnabled: false, ruleOp, ruleValue: "" });
    for (const dualMode of [false, true]) {
      const req = buildExtractRequest(dualMode, configured)!;
      expect(req.sourceRules?.root).toEqual({
        type: "PREDICATE",
        column: { table: SOURCE, column: "district" },
        operator: ruleOp,
      });
    }
  });
});
