import { describe, expect, it } from "vitest";
import { resolveAdminSection, visibleAdminSections } from "@/app/admin456/adminSections";

const all = { superAdmin: true, auditReader: true };
const neither = { superAdmin: false, auditReader: false };

describe("resolveAdminSection", () => {
  it("defaults to users when the parameter is absent", () => {
    expect(resolveAdminSection(null, all)).toBe("users");
    expect(resolveAdminSection(undefined, neither)).toBe("users");
    expect(resolveAdminSection("  ", all)).toBe("users");
  });

  it("accepts every supported section the session may open", () => {
    for (const id of ["users", "scopes", "connections", "data-sources", "registry", "columns", "audit", "otp", "backup", "guardrails"] as const) {
      expect(resolveAdminSection(id, all)).toBe(id);
    }
  });

  it("falls back to users for an invalid value", () => {
    expect(resolveAdminSection("not-a-section", all)).toBe("users");
    expect(resolveAdminSection("USERS", neither)).toBe("users");
  });

  it("falls back to the first permitted section when the requested one is hidden", () => {
    expect(resolveAdminSection("audit", neither)).toBe("users");
    expect(resolveAdminSection("otp", { superAdmin: false, auditReader: true })).toBe("users");
    expect(resolveAdminSection("audit", { superAdmin: true, auditReader: false })).toBe("users");
  });

  it("hides audit and otp unless the session allows them", () => {
    const labels = visibleAdminSections(neither).map((section) => section.id);
    expect(labels).not.toContain("audit");
    expect(labels).not.toContain("otp");
    expect(labels).not.toContain("data-sources");
    expect(visibleAdminSections(all).map((section) => section.id)).toEqual([
      "users",
      "scopes",
      "connections",
      "data-sources",
      "registry",
      "columns",
      "audit",
      "otp",
      "backup",
      "guardrails",
    ]);
  });
});
