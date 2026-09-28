import { describe, expect, it } from "vitest";
import {
  isLockedResultColumn,
  resetColumnVisibilityForSchema,
  toggleColumnHidden,
  visibleColumnIds,
} from "@/lib/resultTableColumns";

describe("resultTableColumns", () => {
  it("keeps match_status visible when hidden set tries to hide it", () => {
    const cols = ["source_a", "match_status"];
    expect(visibleColumnIds(cols, ["match_status", "source_a"])).toEqual(["match_status"]);
  });

  it("resets hidden ids when schema changes", () => {
    expect(resetColumnVisibilityForSchema(["old_col", "source_a"], ["source_a", "target_b"])).toEqual([
      "source_a",
    ]);
  });

  it("prevents hiding every column", () => {
    const all = ["a", "b"];
    const next = toggleColumnHidden([], "a", true, all);
    const blocked = toggleColumnHidden(next, "b", true, all);
    expect(blocked).toEqual(next);
  });

  it("detects locked comparison status columns", () => {
    expect(isLockedResultColumn("hub_match_status")).toBe(true);
  });
});
