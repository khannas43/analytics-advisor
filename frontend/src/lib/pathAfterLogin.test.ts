import { describe, expect, it } from "vitest";
import { pathAfterLogin } from "@/lib/authToken";

describe("pathAfterLogin", () => {
  it("sends an officer who was headed to admin into the workspace", () => {
    expect(pathAfterLogin("/admin456", false)).toBe("/overview");
    expect(pathAfterLogin("/admin456?section=data-sources", false)).toBe("/overview");
  });

  it("keeps an administrator on the admin page", () => {
    expect(pathAfterLogin("/admin456", true)).toBe("/admin456");
    expect(pathAfterLogin("/admin456?section=users", true)).toBe("/admin456?section=users");
  });

  it("keeps an officer on the workspace when that was the requested page", () => {
    expect(pathAfterLogin("/overview", false)).toBe("/overview");
    expect(pathAfterLogin("/query-builder", false)).toBe("/query-builder");
  });
});
