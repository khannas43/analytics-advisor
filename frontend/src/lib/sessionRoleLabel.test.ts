import { describe, expect, it } from "vitest";
import { sessionRoleLabel } from "@/lib/sessionRoleLabel";
import type { AuthSession } from "@/lib/authToken";

function session(partial: Partial<AuthSession>): AuthSession {
  return {
    username: "u",
    roles: [],
    authorities: [],
    admin: false,
    superAdmin: false,
    auditReader: false,
    mustChangePassword: false,
    active: true,
    ...partial,
  };
}

describe("sessionRoleLabel", () => {
  it("returns Super Admin when superAdmin flag is set", () => {
    expect(sessionRoleLabel(session({ superAdmin: true }))).toBe("Super Admin");
  });

  it("returns Admin when admin flag is set", () => {
    expect(sessionRoleLabel(session({ admin: true }))).toBe("Admin");
  });

  it("returns Officer for a standard officer session", () => {
    expect(sessionRoleLabel(session({ roles: ["STATE_OFFICER"] }))).toBe("Officer");
  });

  it("returns empty string when session is missing", () => {
    expect(sessionRoleLabel(null)).toBe("");
  });
});
