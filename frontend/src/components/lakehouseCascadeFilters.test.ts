import { describe, expect, it } from "vitest";
import {
  meaningfulLabelFilterOptions,
  shouldShowLabelFilter,
  UNTAGGED_FILTER,
} from "@/components/LakehouseCascade";

describe("optional label filter visibility", () => {
  it("hides when empty or only UNTAGGED", () => {
    expect(shouldShowLabelFilter([])).toBe(false);
    expect(shouldShowLabelFilter([UNTAGGED_FILTER])).toBe(false);
    expect(meaningfulLabelFilterOptions([UNTAGGED_FILTER])).toEqual([]);
  });

  it("shows when at least one configured tag exists", () => {
    expect(shouldShowLabelFilter(["SILVER", UNTAGGED_FILTER])).toBe(true);
    expect(meaningfulLabelFilterOptions(["SILVER", UNTAGGED_FILTER])).toEqual(["SILVER"]);
  });
});
