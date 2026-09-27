import { describe, expect, it, beforeEach } from "vitest";
import type { RecordMatchRequest } from "@/lib/analysisApi";
import { buildExtractRequest, buildMergedRequest } from "@/lib/queryBuilderRequestBuild";
import {
  EMPTY_EXTRACT_CONFIG,
  EMPTY_REPORT_CONFIG,
  requestsEqual,
} from "@/lib/queryBuilderPipelineTypes";
import { useQueryBuilderPipeline, extractRequestIsBuildable } from "@/lib/queryBuilderPipelineStore";
import {
  dualModeFromRequest,
  hydrateExtractConfigFromRequest,
  hydrateReportConfigFromRequest,
} from "@/lib/savedQueryHydrate";

const sampleExtract = {
  ...EMPTY_EXTRACT_CONFIG,
  sourceRef: { catalog: "iceberg", schema: "srse", table: "beneficiary" },
  targetRef: { catalog: "iceberg_silver", schema: "silver_txn", table: "tbl_txn_bankdtl" },
  displayCols: ["m_id", "full_name"],
  joinKeySource: "m_id",
  joinKeyTarget: "m_id",
};

describe("Query Builder pipeline workflow", () => {
  beforeEach(() => {
    useQueryBuilderPipeline.getState().resetWorkflow();
  });

  it("does not mark extract succeeded when the request is only buildable", () => {
    useQueryBuilderPipeline.setState({ dualMode: true, extract: sampleExtract });
    expect(extractRequestIsBuildable(true, sampleExtract)).toBe(true);
    expect(useQueryBuilderPipeline.getState().extractSucceeded).toBe(false);
  });

  it("marks extract succeeded only after commitExtractSuccess", () => {
    const req = buildExtractRequest(true, sampleExtract)!;
    useQueryBuilderPipeline.getState().commitExtractSuccess(req, {
      columns: ["m_id"],
      rows: [{ m_id: 1 }],
      totalRows: 1,
      sql: "SELECT 1",
    });
    expect(useQueryBuilderPipeline.getState().extractSucceeded).toBe(true);
    expect(useQueryBuilderPipeline.getState().baseExtractRequest).toEqual(req);
  });

  it("buildMergedRequest extends extract with report grouping", () => {
    const report = {
      ...EMPTY_REPORT_CONFIG,
      groupEnabled: true,
      groupByCol: "district",
      aggregateFn: "COUNT" as const,
    };
    const merged = buildMergedRequest(true, sampleExtract, report)!;
    expect(merged.groupByColumns?.[0].column).toBe("district");
    expect(merged.aggregates?.[0].function).toBe("COUNT");
    const extractOnly = buildExtractRequest(true, sampleExtract)!;
    expect(requestsEqual(merged, extractOnly)).toBe(false);
  });

  it("buildMergedRequest drops comparisons and dedup when grouping (backend mutual exclusion)", () => {
    const report = {
      ...EMPTY_REPORT_CONFIG,
      groupEnabled: true,
      groupByCol: "district",
      aggregateFn: "COUNT" as const,
      dedupEnabled: true,
      comparisonPairs: [
        { sourceColumn: "full_name", targetColumn: "name", fuzzyThresholdPercent: 80 },
      ],
    };
    const merged = buildMergedRequest(true, sampleExtract, report)!;
    expect(merged.comparisonGroups ?? []).toHaveLength(0);
    expect(merged.dedup).toBeNull();
  });

  it("hydrates extract and report configs from a merged saved request", () => {
    const merged: RecordMatchRequest = {
      sourceCriteria: [
        {
          catalog: "iceberg",
          schema: "srse",
          table: "beneficiary",
          column: "m_id",
          fuzzyThresholdPercent: 85,
        },
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
        { catalog: "iceberg", schema: "srse", table: "beneficiary", column: "full_name" },
      ],
      highlightDuplicates: true,
      dedup: null,
      mismatchOnly: false,
      joinType: "LEFT",
      comparisonGroups: [
        {
          source: [
            {
              catalog: "iceberg",
              schema: "srse",
              table: "beneficiary",
              column: "full_name",
              fuzzyThresholdPercent: null,
            },
          ],
          target: [
            {
              catalog: "iceberg_silver",
              schema: "silver_txn",
              table: "tbl_txn_bankdtl",
              column: "name",
              fuzzyThresholdPercent: null,
            },
          ],
          mode: "COMBINE",
          fuzzyThresholdPercent: 80,
          separator: " ",
        },
      ],
      groupByColumns: [{ catalog: "iceberg", schema: "srse", table: "beneficiary", column: "district" }],
      aggregates: [{ function: "COUNT", distinct: false }],
    };
    const extract = hydrateExtractConfigFromRequest(merged);
    expect(extract.displayCols).toEqual(["full_name"]);
    expect(extract.joinKeySource).toBe("m_id");
    expect(dualModeFromRequest(merged)).toBe(true);

    const report = hydrateReportConfigFromRequest(merged);
    expect(report.joinType).toBe("LEFT");
    expect(report.highlightDuplicates).toBe(true);
    expect(report.groupEnabled).toBe(true);
    expect(report.comparisonPairs).toHaveLength(1);
  });

  it("patchExtract clears extractSucceeded after a successful extract", () => {
    const req = buildExtractRequest(true, sampleExtract)!;
    useQueryBuilderPipeline.getState().commitExtractSuccess(req, {
      columns: ["m_id"],
      rows: [],
      totalRows: 0,
      sql: "",
    });
    useQueryBuilderPipeline.getState().patchExtract({ joinKeySource: "other_col" });
    expect(useQueryBuilderPipeline.getState().extractSucceeded).toBe(false);
  });
});
