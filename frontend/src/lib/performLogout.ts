import { clearAuthToken, readStoredAuthToken } from "@/lib/authToken";
import { useQueryResultsStore } from "@/lib/queryResultsStore";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";

/** Server audit + clear client session and sensitive cached query rows, then hard redirect to login. */
export async function performLogout(): Promise<void> {
  const token = readStoredAuthToken();

  if (token) {
    try {
      await fetch(`${API_BASE}/api/auth/logout`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      });
    } catch {
      /* network or expired session — still clear locally */
    }
  }

  clearAuthToken();
  useQueryResultsStore.getState().clear();

  if (typeof window !== "undefined") {
    window.location.replace("/login");
  }
}
