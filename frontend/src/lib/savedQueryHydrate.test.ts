import { describe, expect, it } from "vitest";
import {
  hydrateExtractConfigFromRequest,
  hydrateReportStateFromSavedRequest,
} from "@/lib/savedQueryHydrate";
import type { RecordMatchRequest } from "@/lib/analysisApi";

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

  it("hydrateExtract restores value-picker IN rules", () => {
    const req = {
      sourceCriteria: [],
      targetCriteria: [],
      sourceDisplayColumns: [{ catalog: "iceberg", schema: "srse", table: "beneficiary", column: "district" }],
      highlightDuplicates: false,
      dedup: null,
      singleSource: true,
      sourceRules: {
        root: {
          type: "PREDICATE" as const,
          column: {
            table: { catalog: "iceberg", schema: "srse", table: "beneficiary" },
            column: "district",
          },
          operator: "IN" as const,
          value: ["Jaipur"],
        },
      },
    };
    const extract = hydrateExtractConfigFromRequest(req);
    expect(extract.useValuePicker).toBe(true);
    expect(extract.ruleColumn).toBe("district");
    expect(extract.valuePickerSpec?.root).toMatchObject({ operator: "IN" });
  });
});
