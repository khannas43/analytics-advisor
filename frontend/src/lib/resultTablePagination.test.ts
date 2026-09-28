import { describe, expect, it } from "vitest";
import { compareCellValues } from "@/lib/resultTableSort";

/** Sorting is applied to the full filtered set before slicing pages (display-only pagination). */
describe("sort before pagination", () => {
  it("sorts entire dataset before a page slice would be taken", () => {
    const rows = [{ n: "10" }, { n: "2" }, { n: "1" }];
    const sorted = [...rows].sort((a, b) => compareCellValues(a.n, b.n));
    expect(sorted.map((r) => r.n)).toEqual(["1", "2", "10"]);
    const pageSize = 2;
    const page = sorted.slice(0, pageSize);
    expect(page).toEqual([{ n: "1" }, { n: "2" }]);
  });
});
