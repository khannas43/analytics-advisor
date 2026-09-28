import type { AuthSession } from "@/lib/authToken";

/** Display role for the authenticated user bar (local auth session). */
export function sessionRoleLabel(session: AuthSession | null | undefined): string {
  if (!session) {
    return "";
  }
  if (session.superAdmin) {
    return "Super Admin";
  }
  if (session.admin) {
    return "Admin";
  }
  if (session.auditReader && session.roles.length === 0) {
    return "Audit Reader";
  }
  const roles = session.roles.map((r) => r.toUpperCase());
  if (roles.includes("SUPER_ADMIN")) {
    return "Super Admin";
  }
  if (roles.includes("ADMIN")) {
    return "Admin";
  }
  if (roles.includes("AUDIT_READER")) {
    return "Audit Reader";
  }
  return "Officer";
}
