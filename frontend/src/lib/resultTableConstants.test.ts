import { describe, expect, it } from "vitest";
import { RESULT_DEFAULT_PAGE_SIZE, RESULT_PAGE_SIZE_OPTIONS } from "@/lib/resultTableConstants";

describe("resultTableConstants", () => {
  it("default page size is 25", () => {
    expect(RESULT_DEFAULT_PAGE_SIZE).toBe(25);
  });

  it("offers 10, 25, 50 and 100 page sizes", () => {
    expect(RESULT_PAGE_SIZE_OPTIONS).toEqual([10, 25, 50, 100]);
  });
});
