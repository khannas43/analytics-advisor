import { describe, expect, it } from "vitest";
import { compareCellValues } from "@/lib/resultTableSort";

describe("compareCellValues", () => {
  it("sorts numbers numerically", () => {
    expect(compareCellValues("10", "2")).toBeGreaterThan(0);
    expect(compareCellValues(2, 10)).toBeLessThan(0);
  });

  it("sorts text with locale awareness", () => {
    expect(compareCellValues("b", "a")).toBeGreaterThan(0);
  });

  it("orders nulls after non-null values", () => {
    expect(compareCellValues(null, "a")).toBeGreaterThan(0);
    expect(compareCellValues("a", null)).toBeLessThan(0);
  });
});
